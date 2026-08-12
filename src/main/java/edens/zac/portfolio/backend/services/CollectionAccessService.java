package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.dao.RoleRepository.EffectiveGrant;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.types.AccessLevel;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-collection access, resolved through role membership. A user may VIEW a collection when any of
 * their roles grants it (any level); CLIENT powers (download/tag/star) require a CLIENT grant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionAccessService {

  private final RoleRepository roleRepository;

  /** True when the user may VIEW the collection through any of their roles. */
  @Transactional(readOnly = true)
  public boolean canView(Long userId, Long collectionId) {
    return roleRepository.canView(userId, collectionId);
  }

  /** True when the user may DOWNLOAD / TAG (a CLIENT grant on any of their roles). */
  @Transactional(readOnly = true)
  public boolean isClient(Long userId, Long collectionId) {
    return roleRepository.isClient(userId, collectionId);
  }

  /** Distinct collection ids the user can reach through any role — for the /user listing. */
  @Transactional(readOnly = true)
  public List<Long> memberCollectionIdsForUser(Long userId) {
    return roleRepository.memberCollectionIdsForUser(userId);
  }

  /** Deduped (collectionId, level) the user can reach, CLIENT winning on conflict. */
  @Transactional(readOnly = true)
  public List<EffectiveGrant> effectiveGrants(Long userId) {
    return roleRepository.effectiveGrants(userId);
  }

  /**
   * The principal's effective level on one collection: the ADMIN sentinel for a global admin,
   * otherwise the highest stored grant across their roles, otherwise empty. Absence is load-bearing
   * -- "no grant" must stay distinguishable from a GENERAL grant, or every logged-in user would
   * satisfy hasAtLeast(GENERAL) and canView would leak site-wide.
   */
  @Transactional(readOnly = true)
  public Optional<AccessLevel> effectiveLevel(AuthPrincipal principal, Long collectionId) {
    if (principal == null) {
      return Optional.empty();
    }
    if (principal.isAdmin()) {
      return Optional.of(AccessLevel.ADMIN);
    }
    return roleRepository.highestLevel(principal.userId(), collectionId);
  }

  /** True when the principal's effective level on the collection is at least {@code required}. */
  @Transactional(readOnly = true)
  public boolean hasAtLeast(AuthPrincipal principal, Long collectionId, AccessLevel required) {
    return effectiveLevel(principal, collectionId)
        .filter(level -> level.atLeast(required))
        .isPresent();
  }
}
