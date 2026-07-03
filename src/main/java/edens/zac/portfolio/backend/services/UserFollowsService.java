package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.UserFollowedCollectionRepository;
import edens.zac.portfolio.backend.entity.UserFollowedCollectionEntity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-user followed collections ("Your Space"). Any logged-in user may follow any collection: there
 * is no per-collection access check. Auth is enforced at the controller (principal null-check).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserFollowsService {

  private final UserFollowedCollectionRepository userFollowedCollectionRepository;
  private final CollectionRepository collectionRepository;

  /**
   * Add a collection to the user's follows. Idempotent. Rejects a nonexistent collection with a 404
   * (via {@link ResourceNotFoundException}) rather than letting the missing-row FK surface as a
   * 409.
   */
  @Transactional
  public void add(Long userId, Long collectionId) {
    if (collectionRepository.findById(collectionId).isEmpty()) {
      throw new ResourceNotFoundException("Collection not found with ID: " + collectionId);
    }
    userFollowedCollectionRepository.insert(
        UserFollowedCollectionEntity.builder().userId(userId).collectionId(collectionId).build());
  }

  /** Remove a collection from the user's own follows. No-op if it was not followed. */
  @Transactional
  public void remove(Long userId, Long collectionId) {
    userFollowedCollectionRepository.deleteByUserIdAndCollectionId(userId, collectionId);
  }

  /** The collection ids the user follows, newest-followed first. */
  @Transactional(readOnly = true)
  public List<Long> listFollowedCollectionIds(Long userId) {
    return userFollowedCollectionRepository.findCollectionIdsByUserId(userId);
  }
}
