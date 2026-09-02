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
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the {@code /user} synthetic {@link CollectionModel}: a self-only aggregation of every
 * collection a signed-in user is associated with, plus a cover drawn from their tagged content
 * (falling back to an associated collection's own cover when they are tagged in nothing). The
 * association set is the de-duplicated union of (a) collections the user's linked person is tagged
 * on via {@code collection_people} and (b) collections the user reaches through a role grant
 * (membership via a role). The page-level auth check guarantees the viewer is the owner, so this
 * leans on {@code UNLISTED} visibility rather than a gate.
 *
 * <p>{@link #assembleForShare} builds the same page for a share-link recipient, swapping half (b)
 * for the share's opt-in allowlist. That entry point has no owner-is-viewer guarantee, which is
 * exactly why it must not fall back to the grant-based set.
 *
 * <p>Mirrors the block-building shape of {@link SyntheticCollectionResolver} (a model whose blocks
 * are {@link ContentModels.Collection} via {@link
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
  private final ShareLinkService shareLinkService;
  private final CollectionRepository collectionRepository;
  private final ContentRepository contentRepository;
  private final CollectionProcessingUtil collectionProcessingUtil;
  private final ContentModelConverter contentModelConverter;

  /** Assemble the synthetic collection for a user id derived from the authenticated principal. */
  @Transactional(readOnly = true)
  public CollectionModel assembleForUser(Long userId) {
    return assemble(userId, collectionAccessService.memberCollectionIdsForUser(userId));
  }

  /**
   * Assemble the recipient view behind a share link: the same page, scoped to the share's allowlist
   * instead of the owner's grants.
   *
   * <pre>
   * assembleForUser  : personCollectionIds UNION memberCollectionIds
   * assembleForShare : personCollectionIds UNION share_link_collection opt-ins
   * </pre>
   *
   * <p>That substitution is the entire difference, and it is the point of the feature. A collection
   * the owner merely holds a grant on -- someone else's gallery they were let into -- is absent
   * here unless they deliberately opted it in, so holding a link can never become a way to pass
   * along access that was given to somebody else.
   *
   * @param shareLinkId the share whose scope bounds the page
   * @param ownerUserId the share owner, whose tags and description the page is built from
   */
  @Transactional(readOnly = true)
  public CollectionModel assembleForShare(Long shareLinkId, Long ownerUserId) {
    return assemble(ownerUserId, shareLinkService.scopeCollectionIds(shareLinkId));
  }

  /**
   * The shared body. {@code additionalCollectionIds} is unioned with the owner's tagged-in
   * collections; everything else -- tagged standalone content, cover resolution, title fallback,
   * ordering -- is identical for both entry points.
   *
   * <p>Since the V35 identity merge the account and the person tag are one {@code users} row, so
   * the principal's id IS the person id. The page treats the user as a "person" only when they are
   * actually tagged, by collection or by standalone content; a grant-only viewer with no person
   * tags falls back to the generic title and surfaces only their granted galleries, preserving the
   * pre-merge {@code findByUserId} contract.
   *
   * <p>The title follows that same rule, but the cover does not: it always falls back to one of the
   * viewer's associated collections, so a user with an account who is tagged in nothing still gets
   * an entry-point image instead of a blank header. The description is set unconditionally from the
   * user account row, independent of person tagging.
   */
  private CollectionModel assemble(Long userId, List<Long> additionalCollectionIds) {
    Optional<ContentPersonEntity> identity = personRepository.findById(userId);

    Set<Long> personCollectionIds = new LinkedHashSet<>();
    identity.ifPresent(
        p ->
            personCollectionIds.addAll(
                collectionRepository.findCollectionIdsByPersonId(p.getId())));
    List<ContentModel> taggedBlocks =
        identity.map(p -> buildTaggedContentBlocks(p.getId())).orElseGet(List::of);

    Set<Long> collectionIds = new LinkedHashSet<>(personCollectionIds);
    collectionIds.addAll(additionalCollectionIds);

    List<ContentModel> body = new ArrayList<>(buildCollectionBlocks(collectionIds));
    body.addAll(taggedBlocks);
    reindexSequentially(body);

    Optional<ContentPersonEntity> person =
        identity.filter(p -> !personCollectionIds.isEmpty() || !taggedBlocks.isEmpty());
    ContentModels.Image cover =
        person.flatMap(p -> resolveCover(p.getId())).orElseGet(() -> firstCollectionCover(body));
    String title = person.map(ContentPersonEntity::getPersonName).orElse(DEFAULT_TITLE);

    String description =
        appUserRepository.findById(userId).map(AppUserEntity::getDescription).orElse(null);

    return CollectionModel.builder()
        .slug("user")
        .title(title)
        .description(description)
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
   *
   * <p>Both kinds go through the BATCH converters, not the per-entity ones. The single-entity
   * {@code convertImageEntityToModel} / {@code convertRegularContentEntityToModel} resolve each
   * block's tags, people and locations with three queries apiece, so a heavily tagged user cost 3N
   * queries and dominated the whole response: measured against the local backend, 0 tagged images
   * answered in 0.31s, 14 in 0.78s, and 34 in 3.05s. The batch pair issues those three queries ONCE
   * per kind, making the block count irrelevant to the query count — the same treatment {@link
   * #buildCollectionBlocks} already gets from {@code batchConvertToBasicModels}.
   *
   * <p>The swap is shape-preserving, not merely equivalent-looking: each batch path shares its
   * record construction with the per-entity path it replaces ({@code buildImageRecord} / {@code
   * buildGifRecord}), and neither emits a per-block "containing collections" lookup — that field is
   * hard-coded empty in both. So the serialized response is unchanged.
   */
  private List<ContentModel> buildTaggedContentBlocks(Long personId) {
    List<ContentModel> blocks =
        new ArrayList<>(
            contentModelConverter.batchConvertImageEntitiesToModels(
                contentRepository.findTaggedImagesByPersonId(personId)));
    blocks.addAll(
        contentModelConverter.batchConvertGifEntitiesToModels(
            contentRepository.findTaggedGifsByPersonId(personId)));
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

  /**
   * Fallback cover for a viewer with no self-tagged image: the cover of the newest associated
   * collection that has one, or {@code null} when the viewer has no collections (or none of them
   * carry a cover).
   *
   * <p>Costs no additional query — {@link #buildCollectionBlocks} already hydrates each block's
   * cover through {@link CollectionProcessingUtil#batchConvertToBasicModels}, which batch-loads
   * them to avoid an N+1. The body is already ordered collection-date desc, so "first" means
   * "newest", which keeps the choice deterministic across requests (unlike {@link #resolveCover},
   * which is deliberately random over the viewer's tagged images).
   */
  private static ContentModels.Image firstCollectionCover(List<ContentModel> body) {
    return body.stream()
        .filter(ContentModels.Collection.class::isInstance)
        .map(ContentModels.Collection.class::cast)
        .map(ContentModels.Collection::coverImage)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }
}
