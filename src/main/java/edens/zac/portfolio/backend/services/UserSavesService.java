package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.UserSavedImageRepository;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.UserSavedImageEntity;
import edens.zac.portfolio.backend.model.ContentModels;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-user saved images ("Your Space" bookmarks). Any logged-in user may save any image: there is
 * no per-collection access check. Auth is enforced at the controller (principal null-check).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSavesService {

  private final UserSavedImageRepository userSavedImageRepository;
  private final ContentRepository contentRepository;
  private final ContentModelConverter contentModelConverter;

  /** Add an image to the user's saves. Idempotent. */
  @Transactional
  public void add(Long userId, Long imageId) {
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
