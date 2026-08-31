package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.ShareLinkRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.ShareLinkEntity;
import edens.zac.portfolio.backend.types.AccessLevel;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lifecycle and scope resolution for user share links.
 *
 * <p>A share behaves as a virtual role: holding the token stands in for {@code role_member}, and a
 * computed collection set stands in for {@code role_collection}. The level is always {@link
 * AccessLevel#GENERAL} -- a link holder is a guest with an allowlist, never a borrower of the
 * owner's grants, so nothing here can return CLIENT or above no matter what the row says.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShareLinkService {

  private final ShareLinkRepository shareLinkRepository;
  private final AppUserRepository appUserRepository;
  private final TokenCipher tokenCipher;

  /**
   * Create the user's link, or reset an existing one. Reset rotates the token on the existing row,
   * so the owner's opt-in collections survive it; only the secret changes.
   *
   * @param userId the owner
   * @return the raw token, for building the shareable URL
   */
  @Transactional
  public String mintOrRotate(Long userId) {
    String raw = TokenUtil.generateRawToken();
    String hash = TokenUtil.sha256Hex(raw);
    String cipher = tokenCipher.encrypt(raw);

    int rotated = shareLinkRepository.rotateToken(userId, hash, cipher);
    if (rotated == 0) {
      shareLinkRepository.insert(
          ShareLinkEntity.builder()
              .userId(userId)
              .tokenHash(hash)
              .tokenCipher(cipher)
              .level(AccessLevel.GENERAL)
              .build());
      log.debug("Minted a new share link for user {}", userId);
    } else {
      log.debug("Rotated the share link token for user {}", userId);
    }
    return raw;
  }

  /**
   * The owner's own live token, so they can copy or re-send the link they already gave out.
   *
   * <p>This is what makes a share link behave the way its owner expects -- send it to one person
   * today, the same link to someone else next month, and it keeps working until they reset it.
   * Storing only the hash meant the link was visible exactly once and re-sending required a reset,
   * which would have broken the first recipient.
   *
   * @return the raw token, or empty when it cannot be recovered -- a row minted before V58, or one
   *     encrypted under a since-changed secret. Callers surface that as "reset to get a new link"
   *     rather than an error.
   */
  @Transactional(readOnly = true)
  public Optional<String> revealToken(Long userId) {
    return findForUser(userId).map(ShareLinkEntity::getTokenCipher).map(tokenCipher::decrypt);
  }

  /**
   * Resolve a raw token from a URL or cookie to its link. Returns empty for an unknown token, for a
   * rotated one -- a reset leaves no trace of the old secret to distinguish -- and for a link whose
   * owner's account is no longer active.
   *
   * <p>Disabling an account suspends its link rather than revoking it: no row is deleted, so
   * re-enabling makes the same URL work again for everyone already holding it. The status is read
   * fresh on each call, which is enough because this is the only way a token becomes a link. The
   * flyby cookie carries the raw token, so every request comes back through here, and nothing
   * downstream re-tests status.
   */
  @Transactional(readOnly = true)
  public Optional<ShareLinkEntity> resolveByRawToken(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    return shareLinkRepository
        .findByTokenHash(TokenUtil.sha256Hex(rawToken))
        .filter(link -> ownerAccountIsActive(link.getUserId()));
  }

  /**
   * Whether the link's owner can still sign in, via the same {@link SessionService#mayHoldSession}
   * rule the session path uses -- a link serves exactly while its owner's account does. Missing
   * user or missing status reads as false.
   */
  private boolean ownerAccountIsActive(Long userId) {
    return appUserRepository
        .findById(userId)
        .map(AppUserEntity::getStatus)
        .filter(SessionService::mayHoldSession)
        .isPresent();
  }

  /**
   * Load a share by its id -- the lookup for a principal already resolved by the filter, where the
   * raw token is not needed again.
   */
  @Transactional(readOnly = true)
  public Optional<ShareLinkEntity> findById(Long shareLinkId) {
    return shareLinkId == null ? Optional.empty() : shareLinkRepository.findById(shareLinkId);
  }

  @Transactional(readOnly = true)
  public Optional<ShareLinkEntity> findForUser(Long userId) {
    return userId == null ? Optional.empty() : shareLinkRepository.findByUserId(userId);
  }

  /** Every collection a link holder may open. Live, never snapshotted. */
  @Transactional(readOnly = true)
  public List<Long> scopeCollectionIds(Long shareLinkId) {
    return shareLinkRepository.findScopeCollectionIds(shareLinkId);
  }

  /** The collections the owner explicitly opted in, for rendering their toggles. */
  @Transactional(readOnly = true)
  public List<Long> optInCollectionIds(Long shareLinkId) {
    return shareLinkRepository.findOptInCollectionIds(shareLinkId);
  }

  /**
   * The level a link holder resolves to on one collection: {@code GENERAL} inside the share's
   * scope, empty outside it.
   *
   * <p>The GENERAL ceiling is pinned here rather than read from {@code share_link.level}. Absence
   * stays absence -- returning empty rather than a default keeps "no grant" distinguishable from a
   * GENERAL grant, which is what stops a link holder satisfying {@code hasAtLeast(GENERAL)}
   * site-wide.
   */
  @Transactional(readOnly = true)
  public Optional<AccessLevel> levelFor(Long shareLinkId, Long collectionId) {
    return shareLinkRepository.isCollectionInScope(shareLinkId, collectionId)
        ? Optional.of(AccessLevel.GENERAL)
        : Optional.empty();
  }

  /** Record that a link was opened, for the owner's "last opened" display. */
  @Transactional
  public void touchLastUsed(Long shareLinkId) {
    shareLinkRepository.touchLastUsed(shareLinkId);
  }

  @Transactional
  public void addOptIn(Long shareLinkId, Long collectionId) {
    shareLinkRepository.addOptIn(shareLinkId, collectionId);
  }

  @Transactional
  public void removeOptIn(Long shareLinkId, Long collectionId) {
    shareLinkRepository.removeOptIn(shareLinkId, collectionId);
  }
}
