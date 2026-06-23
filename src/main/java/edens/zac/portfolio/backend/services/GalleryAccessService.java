package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.GalleryAccessRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.entity.GalleryAccessEntity;
import edens.zac.portfolio.backend.types.CollectionType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materializes {@code gallery_access} grants from person tags. A grant is the authorization fact; a
 * tag is metadata — this is the single place the two are connected, and only for admin tags on
 * {@code CLIENT_GALLERY} collections. Materialize-only: grants are upserted on tag and are not
 * revoked on untag (revocation is an explicit admin action, deferred to client management).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GalleryAccessService {

  private final CollectionRepository collectionRepository;
  private final PersonRepository personRepository;
  private final GalleryAccessRepository galleryAccessRepository;

  /** True if the user currently holds a non-expired grant for the collection. */
  @Transactional(readOnly = true)
  public boolean hasGrant(Long userId, Long collectionId) {
    return galleryAccessRepository
        .findByUserIdAndCollectionId(userId, collectionId)
        .map(GalleryAccessService::isActive)
        .orElse(false);
  }

  /**
   * True if the user holds a non-expired grant for the collection AND that grant allows download.
   */
  @Transactional(readOnly = true)
  public boolean hasDownloadGrant(Long userId, Long collectionId) {
    return galleryAccessRepository
        .findByUserIdAndCollectionId(userId, collectionId)
        .filter(GalleryAccessService::isActive)
        .map(GalleryAccessEntity::isCanDownload)
        .orElse(false);
  }

  /**
   * Collection ids the user holds a currently-active (non-expired) grant to. Shares the same expiry
   * semantics as {@link #hasGrant} so listing surfaces (e.g. the {@code /user} page) never
   * advertise a gallery the enforcement path would then password-gate.
   */
  @Transactional(readOnly = true)
  public List<Long> activeGrantCollectionIdsForUser(Long userId) {
    return galleryAccessRepository.findByUserId(userId).stream()
        .filter(GalleryAccessService::isActive)
        .map(GalleryAccessEntity::getCollectionId)
        .toList();
  }

  /** A grant is active when it has no expiry or its expiry is still in the future. */
  private static boolean isActive(GalleryAccessEntity grant) {
    return grant.getExpiresAt() == null || grant.getExpiresAt().isAfter(LocalDateTime.now());
  }

  /**
   * For a {@code CLIENT_GALLERY}, ensure every tagged account-linked person holds a grant. No-op
   * for other collection types. Idempotent via the {@code UNIQUE(user_id, collection_id)} guard.
   */
  @Transactional
  public void syncFromCollectionPeople(Long collectionId, List<Long> personIds, Long grantedBy) {
    if (personIds == null || personIds.isEmpty()) {
      return;
    }
    boolean isClientGallery =
        collectionRepository
            .findById(collectionId)
            .map(c -> c.getType() == CollectionType.CLIENT_GALLERY)
            .orElse(false);
    if (!isClientGallery) {
      return;
    }
    for (Long userId : personRepository.findLinkedUserIdsByPersonIds(personIds)) {
      if (galleryAccessRepository.existsByUserIdAndCollectionId(userId, collectionId)) {
        continue;
      }
      galleryAccessRepository.insert(
          GalleryAccessEntity.builder()
              .userId(userId)
              .collectionId(collectionId)
              .canDownload(true)
              .canTag(false)
              .grantedBy(grantedBy)
              .build());
      log.info("Materialized gallery_access user={} collection={}", userId, collectionId);
    }
  }
}
