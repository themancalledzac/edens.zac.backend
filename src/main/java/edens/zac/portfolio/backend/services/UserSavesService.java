package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.UserSavedImageRepository;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.UserSavedImageEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.ContentModels;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-user saved images ("Your Space" bookmarks). A logged-in user may save an image only if they
 * may SEE it — i.e. it holds a visible membership in a LISTED collection or one the caller has an
 * explicit role grant for (see {@link ContentRepository#isImageVisibleToUser}). This prevents a
 * client-gallery user from POSTing an arbitrary image id to exfiltrate images from HIDDEN/UNLISTED
 * collections or another client's gated gallery via the saved-images read. Auth (identity) is
 * enforced at the controller (principal null-check); this service enforces per-image visibility.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSavesService {

  private final UserSavedImageRepository userSavedImageRepository;
  private final ContentRepository contentRepository;
  private final ContentModelConverter contentModelConverter;

  /**
   * Add an image to the user's saves. Idempotent. Rejects an image the caller may not see with a
   * 404 (via {@link ResourceNotFoundException}) — deliberately 404 not 403, so a sequential id scan
   * cannot distinguish "missing" from "exists but hidden" (no enumeration oracle). The visibility
   * check subsumes the old existence guard: an id with no visible LISTED/accessible membership
   * (including a nonexistent id) is treated as not found.
   *
   * <p>A global admin skips the visibility query entirely (working rule 20). The bypass sits here
   * rather than in {@code isImageVisibleToUser}'s SQL on purpose: that query is {@code LISTED OR
   * role grant} and has no {@code is_admin} term, and adding one would push an identity rule into a
   * statement that several read paths share for filtering rather than for authorization. Non-admins
   * take the identical query they always did, so the enumeration-oracle property above is
   * untouched.
   */
  @Transactional
  public void add(AuthPrincipal principal, Long imageId) {
    Long userId = AuthPrincipal.isRealUser(principal) ? principal.userId() : null;
    if (userId == null) {
      throw new ResourceNotFoundException("Image not found with ID: " + imageId);
    }
    if (!principal.isAdmin() && !contentRepository.isImageVisibleToUser(imageId, userId)) {
      throw new ResourceNotFoundException("Image not found with ID: " + imageId);
    }
    userSavedImageRepository.insert(
        UserSavedImageEntity.builder().userId(userId).imageId(imageId).build());
  }

  /** Remove an image from the user's own saves. No-op if it was not saved. */
  @Transactional
  public void remove(Long userId, Long imageId) {
    userSavedImageRepository.deleteByUserIdAndImageId(userId, imageId);
  }

  /** The image ids the user has saved, newest-saved first. */
  @Transactional(readOnly = true)
  public List<Long> listSavedImageIds(Long userId) {
    return userSavedImageRepository.findImageIdsByUserId(userId);
  }

  /** The user's saved images as full models, newest-saved first. */
  @Transactional(readOnly = true)
  public List<ContentModels.Image> listSavedImages(Long userId) {
    List<ContentImageEntity> entities = contentRepository.findSavedImagesByUserId(userId);
    return contentModelConverter.batchConvertImageEntitiesToModels(entities);
  }
}
