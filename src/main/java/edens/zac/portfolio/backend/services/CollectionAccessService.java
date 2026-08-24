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
 * Per-collection access. A user may VIEW a collection when any of their roles grants it (any
 * level); CLIENT powers (download/tag/star) require a CLIENT-or-higher grant. A global admin
 * reaches everything through the ADMIN sentinel, and a share-link holder is capped at GENERAL.
 *
 * <p>Every check here resolves through {@link #effectiveLevel}. That was not true before -- {@code
 * canView} and {@code isClient} queried {@link RoleRepository} directly and so saw neither the
 * admin sentinel nor the share branch, which meant an admin with no role membership was bounced to
 * a password prompt on a gallery they could already see the tile for.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionAccessService {

  private final RoleRepository roleRepository;
  private final ShareLinkService shareLinkService;

  /**
   * True when the principal may VIEW the collection: any stored grant on any of their roles, a
   * share whose scope covers it, or the global-admin sentinel.
   *
   * <p>GENERAL is the floor of the ladder, so "at least GENERAL" and "holds any grant" are the same
   * question for a session principal -- this returns exactly what {@code RoleRepository.canView}
   * returned for that case. What routing through {@link #effectiveLevel} adds is the admin sentinel
   * and the share branch.
   *
   * <p>Because a share principal now resolves GENERAL here, callers on the anonymous read surface
   * must screen with {@link AuthPrincipal#isRealUser} before asking. The two gallery-password gates
   * do, which is what keeps a share link from being an alternative to the password prompt.
   */
  @Transactional(readOnly = true)
  public boolean canView(AuthPrincipal principal, Long collectionId) {
    return hasAtLeast(principal, collectionId, AccessLevel.GENERAL);
  }

  /**
   * True when the principal may DOWNLOAD / TAG: a CLIENT-or-higher grant, or the global-admin
   * sentinel. A share-link holder is capped at GENERAL by {@link #effectiveLevel}, so routing this
   * through it cannot hand a link holder CLIENT powers.
   */
  @Transactional(readOnly = true)
  public boolean isClient(AuthPrincipal principal, Long collectionId) {
    return hasAtLeast(principal, collectionId, AccessLevel.CLIENT);
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
   * <p>This is the one place a share link acquires any read access at all. canView, isClient,
   * hasAtLeast and CollaboratorAccessInterceptor all resolve through here, so the GENERAL ceiling
   * covers every one of them at once: a link holder can never download, tag, star or reach
   * /api/edit, without any of those paths needing to know that shares exist.
   *
   * <p>The share branch sits ahead of the admin check on purpose. A flyby is built with {@code
   * isAdmin=false}, but ordering the branches this way means a share principal resolves as a share
   * no matter what else it might later carry, rather than depending on that flag staying false.
   */
  @Transactional(readOnly = true)
  public Optional<AccessLevel> effectiveLevel(AuthPrincipal principal, Long collectionId) {
    if (principal == null) {
      return Optional.empty();
    }
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
