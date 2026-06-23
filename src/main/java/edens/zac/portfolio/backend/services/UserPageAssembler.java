package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.GalleryAccessRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.entity.GalleryAccessEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import edens.zac.portfolio.backend.types.ContentType;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the {@code /user} synthetic {@link CollectionModel}: a self-only, PARENT-shaped
 * aggregation of every collection a signed-in user is associated with, plus a cover drawn from
 * their tagged content. The association set is the de-duplicated union of (a) collections the
 * user's linked person is tagged on via {@code collection_people} and (b) collections the user
 * holds a {@code gallery_access} grant to. The page-level auth check guarantees the viewer is the
 * owner, so this leans on {@code UNLISTED} visibility rather than a gate.
 *
 * <p>Mirrors the block-building shape of {@link SyntheticCollectionResolver} (PARENT model of
 * {@link ContentModels.Collection} blocks via {@link
 * CollectionProcessingUtil#batchConvertToBasicModels}), but sources rows from the principal's
 * unions instead of a slug catalog.
 */
@Service
@RequiredArgsConstructor
public class UserPageAssembler {

  private static final String DEFAULT_TITLE = "Your Galleries";

  private final PersonRepository personRepository;
  private final GalleryAccessRepository galleryAccessRepository;
  private final CollectionRepository collectionRepository;
  private final ContentRepository contentRepository;
  private final CollectionProcessingUtil collectionProcessingUtil;
  private final ContentModelConverter contentModelConverter;

  /** Assemble the synthetic collection for a user id derived from the authenticated principal. */
  @Transactional(readOnly = true)
  public CollectionModel assembleForUser(Long userId) {
    Optional<ContentPersonEntity> person = personRepository.findByUserId(userId);

    Set<Long> collectionIds = new LinkedHashSet<>();
    person.ifPresent(
        p -> collectionIds.addAll(collectionRepository.findCollectionIdsByPersonId(p.getId())));
    galleryAccessRepository.findByUserId(userId).stream()
        .map(GalleryAccessEntity::getCollectionId)
        .forEach(collectionIds::add);

    List<ContentModel> content = buildCollectionBlocks(collectionIds);
    ContentModels.Image cover = person.flatMap(p -> resolveCover(p.getId())).orElse(null);
    String title = person.map(ContentPersonEntity::getPersonName).orElse(DEFAULT_TITLE);

    return CollectionModel.builder()
        .slug("user")
        .title(title)
        .type(CollectionType.PARENT)
        .visibility(CollectionVisibility.UNLISTED)
        .coverImage(cover)
        .content(content)
        .contentCount(content.size())
        .contentPerPage(content.size())
        .currentPage(0)
        .totalPages(1)
        .build();
  }

  /**
   * Load and convert the associated collections into deterministically ordered Collection blocks.
   */
  private List<ContentModel> buildCollectionBlocks(Set<Long> collectionIds) {
    if (collectionIds.isEmpty()) {
      return List.of();
    }
    List<CollectionEntity> rows = collectionRepository.findByIds(List.copyOf(collectionIds));
    rows.sort(
        Comparator.comparing(
                CollectionEntity::getCollectionDate,
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(CollectionEntity::getId, Comparator.reverseOrder()));
    return collectionProcessingUtil.batchConvertToBasicModels(rows).stream()
        .map(UserPageAssembler::toCollectionContent)
        .map(ContentModel.class::cast)
        .toList();
  }

  /** The most-recent associated content image as a cover model (Decision D2). */
  private Optional<ContentModels.Image> resolveCover(Long personId) {
    return contentRepository
        .findMostRecentImageIdByPersonId(personId)
        .flatMap(contentRepository::findImageById)
        .map(contentModelConverter::convertImageEntityToModel);
  }

  private static ContentModels.Collection toCollectionContent(CollectionModel c) {
    return new ContentModels.Collection(
        c.getId(),
        ContentType.COLLECTION,
        c.getTitle(),
        c.getDescription(),
        null,
        0,
        true,
        c.getCreatedAt(),
        c.getUpdatedAt(),
        c.getId(),
        c.getSlug(),
        c.getType(),
        c.getCoverImage());
  }
}
