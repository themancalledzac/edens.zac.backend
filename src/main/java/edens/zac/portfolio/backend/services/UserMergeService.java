package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.controller.admin.UserRequests.MergePreview;
import edens.zac.portfolio.backend.controller.admin.UserRequests.MergeResult;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.UserCollectionRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.types.UserStatus;
import java.util.NoSuchElementException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Merges a tag-only PERSON (source) into a surviving identity (target). */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserMergeService {

  private final AppUserRepository appUserRepository;
  private final PersonRepository personRepository;
  private final UserCollectionRepository userCollectionRepository;

  /**
   * Counts what a merge of {@code sourceId} into {@code targetId} would move, without mutating
   * anything.
   *
   * @return empty when either id is missing
   * @throws IllegalStateException when the pair is not mergeable (same id, or source not a PERSON)
   */
  @Transactional(readOnly = true)
  public Optional<MergePreview> preview(Long sourceId, Long targetId) {
    Optional<AppUserEntity> source = appUserRepository.findById(sourceId);
    Optional<AppUserEntity> target = appUserRepository.findById(targetId);
    if (source.isEmpty() || target.isEmpty()) {
      return Optional.empty();
    }
    requireMergeable(sourceId, targetId, source.get());
    return Optional.of(
        new MergePreview(
            sourceId,
            source.get().getName(),
            targetId,
            target.get().getName(),
            personRepository.countImageTags(sourceId),
            personRepository.countCollectionTags(sourceId),
            personRepository.countCollisions(sourceId, targetId)));
  }

  /**
   * Re-points tags + memberships from source onto target, then hard-deletes the source PERSON row.
   * Runs in a single transaction: re-point happens before delete (the tag FKs are {@code ON DELETE
   * CASCADE}), and collisions are de-duped before the re-point (the join PKs are composite).
   *
   * @throws NoSuchElementException when either id is missing
   * @throws IllegalStateException when the pair is not mergeable (same id, or source not a PERSON)
   */
  @Transactional
  public MergeResult merge(Long sourceId, Long targetId) {
    AppUserEntity source =
        appUserRepository
            .findById(sourceId)
            .orElseThrow(() -> new NoSuchElementException("source " + sourceId + " not found"));
    appUserRepository
        .findById(targetId)
        .orElseThrow(() -> new NoSuchElementException("target " + targetId + " not found"));
    requireMergeable(sourceId, targetId, source);

    final int images = personRepository.countImageTags(sourceId);
    final int collections = personRepository.countCollectionTags(sourceId);
    final int collapsed = personRepository.countCollisions(sourceId, targetId);

    personRepository.repointTags(sourceId, targetId);
    userCollectionRepository.repointMemberships(sourceId, targetId);
    personRepository.deletePersonById(sourceId);

    log.info(
        "Merged person {} into {} (images={}, collections={}, collapsed={})",
        sourceId,
        targetId,
        images,
        collections,
        collapsed);
    // movedImageTags/movedCollections are the GROSS source counts (coherent with the preview's
    // imageTagCount/collectionCount), not net-of-collisions; duplicatesCollapsed reports the
    // overlap.
    return new MergeResult(images, collections, collapsed);
  }

  private void requireMergeable(Long sourceId, Long targetId, AppUserEntity source) {
    if (sourceId.equals(targetId)) {
      throw new IllegalStateException("cannot merge an identity into itself");
    }
    if (source.getStatus() != UserStatus.PERSON) {
      throw new IllegalStateException("source must be a tag-only PERSON");
    }
  }
}
