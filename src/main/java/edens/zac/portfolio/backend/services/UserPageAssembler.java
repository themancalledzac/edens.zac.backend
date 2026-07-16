package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.util.ArrayList;
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
 * holds a {@code user_collection} membership in. The page-level auth check guarantees the viewer is
 * the owner, so this leans on {@code UNLISTED} visibility rather than a gate.
 *
 * <p>Mirrors the block-building shape of {@link SyntheticCollectionResolver} (PARENT model of
 * {@link ContentModels.Collection} blocks via {@link
 * CollectionProcessingUtil#batchConvertToBasicModels}), but sources rows from the principal's
 * unions instead of a slug catalog, and additionally appends the linked person's standalone tagged
 * image/gif content as IMAGE/GIF blocks (spec §7). The body is ordered collections-first
 * (collection-date desc), then standalone content (capture/creation date desc), with {@code
 * orderIndex} reassigned sequentially for stable rendering.
 */
@Service
@RequiredArgsConstructor
public class UserPageAssembler {

  private static final String DEFAULT_TITLE = "Your Galleries";

  private final AppUserRepository appUserRepository;
  private final PersonRepository personRepository;
  private final CollectionAccessService collectionAccessService;
  private final CollectionRepository collectionRepository;
  private final ContentRepository contentRepository;
  private final CollectionProcessingUtil collectionProcessingUtil;
  private final ContentModelConverter contentModelConverter;

  /** Assemble the synthetic collection for a user id derived from the authenticated principal. */
  @Transactional(readOnly = true)
  public CollectionModel assembleForUser(Long userId) {
    // Since the V35 identity merge the account and the person tag are one `users` row, so the
    // principal's id IS the person id. The page treats the user as a "person" only when they are
    // actually tagged (tagged collections or tagged standalone content); a grant-only viewer with
    // no person tags still falls back to the generic title and surfaces only their granted
    // galleries (preserving the pre-merge findByUserId contract).
    Optional<ContentPersonEntity> identity = personRepository.findById(userId);

    Set<Long> personCollectionIds = new LinkedHashSet<>();
    identity.ifPresent(
        p ->
            personCollectionIds.addAll(
                collectionRepository.findCollectionIdsByPersonId(p.getId())));
    List<ContentModel> taggedBlocks =
        identity.map(p -> buildTaggedContentBlocks(p.getId())).orElseGet(List::of);

    Set<Long> collectionIds = new LinkedHashSet<>(personCollectionIds);
    collectionIds.addAll(collectionAccessService.memberCollectionIdsForUser(userId));

    List<ContentModel> body = new ArrayList<>(buildCollectionBlocks(collectionIds));
    body.addAll(taggedBlocks);
    reindexSequentially(body);

    // Cover and title only apply when the viewer is an actual tagged person (has tagged collections
    // or tagged standalone content); a grant-only viewer keeps the generic title and no cover.
    Optional<ContentPersonEntity> person =
        identity.filter(p -> !personCollectionIds.isEmpty() || !taggedBlocks.isEmpty());
    ContentModels.Image cover = person.flatMap(p -> resolveCover(p.getId())).orElse(null);
    String title = person.map(ContentPersonEntity::getPersonName).orElse(DEFAULT_TITLE);

    // Description is set unconditionally from the user account row, independent of person tagging.
    String description =
        appUserRepository.findById(userId).map(AppUserEntity::getDescription).orElse(null);

    return CollectionModel.builder()
        .slug("user")
        .title(title)
        .description(description)
        .type(CollectionType.PARENT)
        .visibility(CollectionVisibility.UNLISTED)
        .coverImage(cover)
        .content(body)
        .contentCount(body.size())
        .contentPerPage(body.size())
        .currentPage(0)
        .totalPages(1)
        .build();
  }

  /**
   * Load and convert the associated collections into Collection cover-tile blocks, ordered by
   * collection date desc (then id desc as a stable tiebreaker).
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
        .map(ContentModels.Collection::fromCollectionModel)
        .map(ContentModel.class::cast)
        .toList();
  }

  /**
   * The linked person's standalone tagged content as IMAGE/GIF blocks, each kind already date-desc
   * from the DAO (images by capture date, gifs by creation), images before gifs. Cross-collection
   * de-duplication is intentionally out of scope for this slice.
   */
  private List<ContentModel> buildTaggedContentBlocks(Long personId) {
    List<ContentModel> blocks = new ArrayList<>();
    contentRepository.findTaggedImagesByPersonId(personId).stream()
        .map(contentModelConverter::convertImageEntityToModel)
        .forEach(blocks::add);
    contentRepository.findTaggedGifsByPersonId(personId).stream()
        .map(contentModelConverter::convertRegularContentEntityToModel)
        .forEach(blocks::add);
    return blocks;
  }

  /** Reassign {@code orderIndex} to match list position so the body renders deterministically. */
  private static void reindexSequentially(List<ContentModel> body) {
    for (int i = 0; i < body.size(); i++) {
      body.set(i, withOrderIndex(body.get(i), i));
    }
  }

  private static ContentModel withOrderIndex(ContentModel block, int orderIndex) {
    return switch (block) {
      case ContentModels.Collection c -> c.withOrderIndex(orderIndex);
      case ContentModels.Image img -> img.withOrderIndex(orderIndex);
      case ContentModels.Gif gif -> gif.withOrderIndex(orderIndex);
      case ContentModels.Text t -> t; // never emitted by this assembler
    };
  }

  /** A random associated content image as a cover model. */
  private Optional<ContentModels.Image> resolveCover(Long personId) {
    return contentRepository
        .findRandomImageIdByPersonId(personId)
        .flatMap(contentRepository::findImageById)
        .map(contentModelConverter::convertImageEntityToModel);
  }
}
