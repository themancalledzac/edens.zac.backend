package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.UserSelectRepository;
import edens.zac.portfolio.backend.entity.UserSelectEntity;
import edens.zac.portfolio.backend.model.UserSelectGroup;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-user Selects (favorites). Authorization mirrors the gallery enforcement path: a non-admin
 * must hold a non-expired {@code gallery_access} grant for a collection to add or list selects in
 * it ({@link GalleryAccessService#hasGrant}); an admin is all-access. Removal needs no per-
 * collection check — a user may always unselect their own row, and the delete is keyed by {@code
 * (user_id, content_id)} so it can only ever touch the caller's own selects.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSelectsService {

  private final UserSelectRepository userSelectRepository;
  private final GalleryAccessService galleryAccessService;

  /** Add an image to the user's selects, scoped to the collection. Idempotent. */
  @Transactional
  public void add(Long userId, boolean isAdmin, Long collectionId, Long contentId) {
    requireCollectionAccess(userId, isAdmin, collectionId);
    userSelectRepository.insert(
        UserSelectEntity.builder()
            .userId(userId)
            .contentId(contentId)
            .collectionId(collectionId)
            .build());
  }

  /** Remove an image from the user's own selects. No-op if it was not selected. */
  @Transactional
  public void remove(Long userId, Long contentId) {
    userSelectRepository.deleteByUserIdAndContentId(userId, contentId);
  }

  /** The selected image ids in one collection, newest-selected first. */
  @Transactional(readOnly = true)
  public List<Long> listSelectIds(Long userId, boolean isAdmin, Long collectionId) {
    requireCollectionAccess(userId, isAdmin, collectionId);
    return userSelectRepository.findContentIdsByUserIdAndCollectionId(userId, collectionId);
  }

  /** Every select the user holds, grouped by collection (collections in newest-select order). */
  @Transactional(readOnly = true)
  public List<UserSelectGroup> listAll(Long userId) {
    Map<Long, List<Long>> byCollection = new LinkedHashMap<>();
    for (UserSelectEntity row : userSelectRepository.findByUserId(userId)) {
      byCollection
          .computeIfAbsent(row.getCollectionId(), k -> new ArrayList<>())
          .add(row.getContentId());
    }
    List<UserSelectGroup> groups = new ArrayList<>();
    byCollection.forEach(
        (collectionId, contentIds) ->
            groups.add(
                UserSelectGroup.builder()
                    .collectionId(collectionId)
                    .contentIds(contentIds)
                    .build()));
    return groups;
  }

  private void requireCollectionAccess(Long userId, boolean isAdmin, Long collectionId) {
    if (isAdmin) {
      return;
    }
    if (!galleryAccessService.hasGrant(userId, collectionId)) {
      throw new AccessDeniedException("No gallery access for collection " + collectionId);
    }
  }
}
