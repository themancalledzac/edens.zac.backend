package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.UserRatingOverrideRepository;
import edens.zac.portfolio.backend.entity.UserRatingOverrideEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-user rating overrides for client gallery views. The authorization fact is a CLIENT grant
 * reached through one of the user's roles: only such a client may write an override, and only for
 * the granted collection. Admins are not routed here — they edit the canonical rating via the admin
 * content path. An override never changes {@code content.rating}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserRatingOverrideService {

  private final UserRatingOverrideRepository overrideRepository;
  private final CollectionAccessService collectionAccessService;

  /**
   * Upsert {@code user}'s override for {@code contentId} in {@code collectionId} to {@code rating}.
   *
   * <p>Takes the whole principal rather than a bare id so the global-admin sentinel counts: an
   * admin holding no CLIENT role was denied here, which working rule 20 says must not happen.
   *
   * @throws IllegalArgumentException if rating is outside 0-5
   * @throws AccessDeniedException if the caller holds no CLIENT membership for the collection
   */
  @Transactional
  public void upsert(AuthPrincipal principal, Long collectionId, Long contentId, int rating) {
    if (rating < 0 || rating > 5) {
      throw new IllegalArgumentException("rating must be between 0 and 5, was " + rating);
    }
    Long userId = AuthPrincipal.isRealUser(principal) ? principal.userId() : null;
    if (userId == null || !collectionAccessService.isClient(principal, collectionId)) {
      throw new AccessDeniedException(
          "user " + userId + " may not override ratings in collection " + collectionId);
    }
    overrideRepository.upsert(
        UserRatingOverrideEntity.builder()
            .userId(userId)
            .contentId(contentId)
            .collectionId(collectionId)
            .rating(rating)
            .build());
    log.info(
        "Upserted rating override user={} collection={} content={} rating={}",
        userId,
        collectionId,
        contentId,
        rating);
  }

  /** Every override the user holds within the collection's view. Read-only; no authz gate. */
  @Transactional(readOnly = true)
  public List<UserRatingOverrideEntity> listForUserInCollection(Long userId, Long collectionId) {
    return overrideRepository.findByUserIdAndCollectionId(userId, collectionId);
  }
}
