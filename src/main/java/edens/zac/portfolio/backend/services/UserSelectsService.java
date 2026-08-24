package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.UserSelectRepository;
import edens.zac.portfolio.backend.entity.UserSelectEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
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
 * must reach a collection through a role grant to add or list selects in it ({@link
 * CollectionAccessService#canView}). Removal needs no per-collection check — a user may always
 * unselect their own row, and the delete is keyed by {@code (user_id, content_id)} so it can only
 * ever touch the caller's own selects.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSelectsService {

  private final UserSelectRepository userSelectRepository;
  private final CollectionAccessService collectionAccessService;

  /** Add an image to the user's selects, scoped to the collection. Idempotent. */
  @Transactional
  public void add(AuthPrincipal principal, Long collectionId, Long contentId) {
    Long userId = requireCollectionAccess(principal, collectionId);
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
  public List<Long> listSelectIds(AuthPrincipal principal, Long collectionId) {
    Long userId = requireCollectionAccess(principal, collectionId);
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

  /**
   * The caller's user id, once their VIEW access to the collection is established. Takes the whole
   * principal so the global-admin sentinel counts; the id alone dropped it.
   *
   * <p>{@link AuthPrincipal#isRealUser} is checked first even though {@code /api/read/user/**} is
   * already gated on {@code hasRole("USER")}. A share-link holder resolves GENERAL through {@code
   * effectiveLevel} but has no user id, so without this the rows inserted below would be keyed on
   * null if that route gating ever changed.
   */
  private Long requireCollectionAccess(AuthPrincipal principal, Long collectionId) {
    if (!AuthPrincipal.isRealUser(principal)
        || !collectionAccessService.canView(principal, collectionId)) {
      throw new AccessDeniedException("No gallery access for collection " + collectionId);
    }
    return principal.userId();
  }
}
