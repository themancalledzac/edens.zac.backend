package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.ShareLinkRepository;
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

  /**
   * Create the user's link, or reset an existing one. Reset rotates the token on the existing row,
   * so the owner's opt-in collections survive it; only the secret changes. The raw token is
   * returned for embedding in the URL and is never stored.
   *
   * @param userId the owner
   * @return the raw token -- the caller's only chance to see it
   */
  @Transactional
  public String mintOrRotate(Long userId) {
    String raw = TokenUtil.generateRawToken();
    String hash = TokenUtil.sha256Hex(raw);

    int rotated = shareLinkRepository.rotateToken(userId, hash);
    if (rotated == 0) {
      shareLinkRepository.insert(
          ShareLinkEntity.builder()
              .userId(userId)
              .tokenHash(hash)
              .level(AccessLevel.GENERAL)
              .build());
      log.debug("Minted a new share link for user {}", userId);
    } else {
      log.debug("Rotated the share link token for user {}", userId);
    }
    return raw;
  }

  /**
   * Resolve a raw token from a URL or cookie to its link. Returns empty for an unknown token and
   * for a rotated one alike -- a reset leaves no trace of the old secret to distinguish.
   */
  @Transactional(readOnly = true)
  public Optional<ShareLinkEntity> resolveByRawToken(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    return shareLinkRepository.findByTokenHash(TokenUtil.sha256Hex(rawToken));
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
