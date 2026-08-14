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
 * their roles grants it (any level); CLIENT powers (download/tag/star) require a CLIENT-or-higher
 * grant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionAccessService {

  private final RoleRepository roleRepository;
  private final ShareLinkService shareLinkService;

  /** True when the user may VIEW the collection through any of their roles. */
  @Transactional(readOnly = true)
  public boolean canView(Long userId, Long collectionId) {
    return roleRepository.canView(userId, collectionId);
  }

  /** True when the user may DOWNLOAD / TAG (a CLIENT-or-higher grant on any of their roles). */
  @Transactional(readOnly = true)
  public boolean isClient(Long userId, Long collectionId) {
    return roleRepository.isClient(userId, collectionId);
  }

  /** Distinct collection ids the user can reach through any role — for the /user listing. */
  @Transactional(readOnly = true)
  public List<Long> memberCollectionIdsForUser(Long userId) {
    return roleRepository.memberCollectionIdsForUser(userId);
  }

  /** Deduped (collectionId, level) the user can reach, the highest rank winning on conflict. */
  @Transactional(readOnly = true)
  public List<EffectiveGrant> effectiveGrants(Long userId) {
    return roleRepository.effectiveGrants(userId);
  }

  /**
   * The principal's effective level on one collection: GENERAL for a share-link holder inside that
   * share's scope, the ADMIN sentinel for a global admin, otherwise the highest stored grant across
   * their roles, otherwise empty. Absence is load-bearing -- "no grant" must stay distinguishable
   * from a GENERAL grant, or every logged-in user would satisfy hasAtLeast(GENERAL) and canView
   * would leak site-wide.
   *
   * <p>This is the single seam where a share link acquires any read access at all. Because canView,
   * isClient, hasAtLeast and CollaboratorAccessInterceptor all resolve through here, the GENERAL
   * ceiling covers the whole surface at once: a link holder can never download, tag, star or reach
   * /api/edit, without any of those paths needing to know that shares exist.
   */
  @Transactional(readOnly = true)
  public Optional<AccessLevel> effectiveLevel(AuthPrincipal principal, Long collectionId) {
    if (principal == null) {
      return Optional.empty();
    }
    // Ahead of the admin check on purpose. A flyby is built with isAdmin=false, but ordering the
    // branches this way means a share principal is resolved as a share no matter what else it
    // might later carry, rather than depending on that flag staying false.
    if (principal.shareId() != null) {
      return shareLinkService.levelFor(principal.shareId(), collectionId);
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
