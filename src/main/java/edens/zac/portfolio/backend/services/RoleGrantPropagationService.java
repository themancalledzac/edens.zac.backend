package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.dao.RoleRepository.CollectionGrant;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.types.AccessLevel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write-time waterfall of role grants down the collection tree (materialized propagation).
 * Inherited grants are real {@code role_collection} rows carrying their ORIGIN (the collection
 * holding the direct grant) in {@code inherited_from_collection_id}, so the resolution queries in
 * {@link RoleRepository} stay flat and untouched. Propagation follows only visible parent-to-child
 * links ({@code cc.visible = true}), matching the V47 backfill; removal walks are
 * visibility-agnostic so stale copies are always stripped. Direct grants are sticky: propagation
 * never clobbers or downgrades a direct row, and removing a parent grant leaves a child's own
 * direct grant in place.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoleGrantPropagationService {

  private final RoleRepository roleRepository;
  private final CollectionRepository collectionRepository;

  /**
   * Grant/raise/demote hook: upsert the direct grant, then rebuild this origin's inherited copies
   * at the new level. Stripping before re-propagating is what makes DEMOTION stick -- the
   * inherited-grant upsert never downgrades, so stale CLIENT copies must be deleted first.
   * Surviving ancestor grants are then re-materialized so a descendant also covered by a higher
   * ancestor grant keeps its higher level.
   */
  @Transactional
  public void setGrant(Long roleId, Long collectionId, AccessLevel level, Long grantedBy) {
    roleRepository.setCollectionGrant(roleId, collectionId, level, grantedBy);
    roleRepository.removeInheritedGrantsByOrigin(roleId, collectionId);
    propagateToVisibleSubtree(roleId, level, collectionId, collectionId);
    rematerializeSubtreeFromAncestors(roleId, collectionId);
    log.info(
        "Set {} grant for role {} on collection {} and waterfalled to descendants",
        level,
        roleId,
        collectionId);
  }

  /**
   * Removal hook: delete the grant and its tree-wide inherited copies, then re-materialize whatever
   * the affected subtree still inherits from surviving ancestor grants.
   */
  @Transactional
  public void removeGrant(Long roleId, Long collectionId) {
    roleRepository.removeCollectionGrant(roleId, collectionId);
    roleRepository.removeInheritedGrantsByOrigin(roleId, collectionId);
    rematerializeSubtreeFromAncestors(roleId, collectionId);
    log.info(
        "Removed grant for role {} on collection {} along with its inherited copies",
        roleId,
        collectionId);
  }

  /** Link hook: copy every grant the parent holds into the newly linked child's subtree. */
  @Transactional
  public void onChildLinked(Long parentId, Long childId) {
    for (CollectionGrant grant : roleRepository.allGrantsForCollection(parentId)) {
      Long originId = grant.direct() ? parentId : grant.inheritedFromCollectionId();
      roleRepository.insertInheritedGrant(grant.roleId(), childId, grant.level(), originId);
      propagateToVisibleSubtree(grant.roleId(), grant.level(), originId, childId);
    }
  }

  /** Unlink hook: strip the child subtree's inherited copies originating at/above the parent. */
  @Transactional
  public void onChildUnlinked(Long parentId, Long childId) {
    Set<Long> origins = ancestorsOf(parentId);
    origins.add(parentId);
    for (Long member : subtreeOf(childId)) {
      for (CollectionGrant grant : roleRepository.allGrantsForCollection(member)) {
        if (!grant.direct() && origins.contains(grant.inheritedFromCollectionId())) {
          roleRepository.removeInheritedGrantsForCollectionByOrigin(
              grant.roleId(), member, grant.inheritedFromCollectionId());
        }
      }
    }
  }

  /**
   * Insert inherited copies of the origin's direct grant on every collection reachable from {@code
   * rootId} through visible links, cycle-guarded. The root itself is not written -- for a grant it
   * already holds the direct row, and for a link the caller inserts the child's copy before
   * descending.
   */
  private void propagateToVisibleSubtree(
      Long roleId, AccessLevel level, Long originCollectionId, Long rootId) {
    Set<Long> visited = new HashSet<>();
    visited.add(rootId);
    Deque<Long> pending =
        new ArrayDeque<>(collectionRepository.findVisibleReferencedCollectionIdsByParentId(rootId));
    while (!pending.isEmpty()) {
      Long current = pending.poll();
      if (!visited.add(current)) {
        continue;
      }
      if (!current.equals(originCollectionId)) {
        roleRepository.insertInheritedGrant(roleId, current, level, originCollectionId);
      }
      pending.addAll(collectionRepository.findVisibleReferencedCollectionIdsByParentId(current));
    }
  }

  /**
   * Re-materialize, within {@code collectionId}'s subtree only, the inherited copies still due from
   * surviving ancestor grants. Rows carrying a given origin exist only inside that origin's
   * subtree, so after stripping origin = {@code collectionId} nothing OUTSIDE its subtree can need
   * rewriting -- rooting the walk here instead of at each ancestor keeps the write set minimal.
   * Only ancestors connected to the collection through an unbroken chain of visible links qualify:
   * a hidden link on the way down blocks re-materialization exactly as it blocks forward
   * propagation. The root itself is upserted too (a no-op right after {@code setGrant}, where it
   * holds a fresh direct row; the re-inherited copy after {@code removeGrant}).
   */
  private void rematerializeSubtreeFromAncestors(Long roleId, Long collectionId) {
    for (Long ancestorId : visiblyLinkedAncestorsOf(collectionId)) {
      roleRepository.directGrantsForCollection(ancestorId).stream()
          .filter(g -> g.roleId().equals(roleId))
          .forEach(
              g -> {
                roleRepository.insertInheritedGrant(roleId, collectionId, g.level(), ancestorId);
                propagateToVisibleSubtree(roleId, g.level(), ancestorId, collectionId);
              });
    }
  }

  /** Every ancestor of the collection through the content graph (any depth), cycle-guarded. */
  private Set<Long> ancestorsOf(Long collectionId) {
    Set<Long> visited = new HashSet<>();
    visited.add(collectionId);
    Set<Long> ancestors = new LinkedHashSet<>();
    Deque<Long> pending = new ArrayDeque<>(parentIdsOf(collectionId));
    while (!pending.isEmpty()) {
      Long current = pending.poll();
      if (!visited.add(current)) {
        continue;
      }
      ancestors.add(current);
      pending.addAll(parentIdsOf(current));
    }
    return ancestors;
  }

  /**
   * The collection plus every descendant, regardless of link visibility (removal must also reach
   * copies under links that were later hidden), cycle-guarded.
   */
  private Set<Long> subtreeOf(Long rootId) {
    Set<Long> visited = new LinkedHashSet<>();
    visited.add(rootId);
    Deque<Long> pending = new ArrayDeque<>(childIdsOf(rootId));
    while (!pending.isEmpty()) {
      Long current = pending.poll();
      if (!visited.add(current)) {
        continue;
      }
      pending.addAll(childIdsOf(current));
    }
    return visited;
  }

  /**
   * Ancestors reachable from the collection through an unbroken upward chain of VISIBLE links,
   * cycle-guarded. Only these can waterfall grants down to it (single-parent trees; multi-parent
   * inheritance is a declared non-goal).
   */
  private Set<Long> visiblyLinkedAncestorsOf(Long collectionId) {
    Set<Long> visited = new HashSet<>();
    visited.add(collectionId);
    Set<Long> ancestors = new LinkedHashSet<>();
    Deque<Long> pending =
        new ArrayDeque<>(
            collectionRepository.findVisibleParentCollectionIdsByChildId(collectionId));
    while (!pending.isEmpty()) {
      Long current = pending.poll();
      if (!visited.add(current)) {
        continue;
      }
      ancestors.add(current);
      pending.addAll(collectionRepository.findVisibleParentCollectionIdsByChildId(current));
    }
    return ancestors;
  }

  private List<Long> parentIdsOf(Long collectionId) {
    return collectionRepository.findAllParentCollectionsByChildId(collectionId).stream()
        .map(CollectionEntity::getId)
        .toList();
  }

  private List<Long> childIdsOf(Long collectionId) {
    return collectionRepository.findAllReferencedCollectionsByParentId(collectionId).stream()
        .map(CollectionEntity::getId)
        .toList();
  }
}
