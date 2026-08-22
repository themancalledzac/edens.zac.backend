package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.model.DiskUploadRequest;
import edens.zac.portfolio.backend.model.Records;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Rule B — "any collection may hold any mix of content" — pinned on a collection that GENUINELY
 * holds child collections.
 *
 * <p>This has to be a real-Postgres test. U4 removed three {@code CollectionType.isParentType()}
 * write guards; with the enum gone, parent-ness is only obtainable by querying the
 * collection_content -> content_collection chain ({@code
 * findAllReferencedCollectionIdsByParentId}). A Mockito fixture therefore cannot be a wrapper at
 * all: a builder-built {@code CollectionEntity} named "Wrapper" carries no children, and a mocked
 * repository answers every derivation with its default. Those unit tests assert only that an
 * existing collection is accepted; re-introducing the invariant as {@code if
 * (!findAllReferencedCollectionIdsByParentId(id).isEmpty()) throw} leaves every one of them green.
 * These do not.
 *
 * <p>Rows live in the SHARED singleton container (only auth tables are truncated), so every slug
 * carries a ruleb- prefix and assertions never use global counts.
 */
class RuleBMixedContentIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private ContentService contentService;
  @Autowired private ContentMutationUtil contentMutationUtil;
  @Autowired private ImageUploadPipelineService imageUploadPipelineService;
  @Autowired private CollectionRepository collectionRepository;
  @Autowired private ContentRepository contentRepository;
  @Autowired private JdbcTemplate jdbc;

  private Long seedCollection(String slug) {
    jdbc.update(
        "INSERT INTO collection (title, slug, visibility, is_client, is_blog)"
            + " VALUES (?, ?, 'LISTED', false, false)",
        slug,
        slug);
    return jdbc.queryForObject("SELECT id FROM collection WHERE slug = ?", Long.class, slug);
  }

  private Long seedImage(String title) {
    Long contentId =
        jdbc.queryForObject(
            "INSERT INTO content (content_type, created_at, updated_at)"
                + " VALUES ('IMAGE', NOW(), NOW()) RETURNING id",
            Long.class);
    jdbc.update(
        "INSERT INTO content_image (id, title, image_url_web) VALUES (?, ?, ?)",
        contentId,
        title,
        "https://cdn.example.com/Image/Web/" + title + ".webp");
    return contentId;
  }

  /**
   * Build a collection that really is a wrapper: a child collection linked through a
   * content_collection reference row, exactly as {@code linkCollectionToParent} would. Asserts the
   * derived signal before returning, so a fixture that silently stops being a wrapper fails here
   * rather than making the Rule B assertion vacuous.
   */
  private Long seedWrapper(String slug) {
    Long parentId = seedCollection(slug);
    Long childId = seedCollection(slug + "-child");
    ContentCollectionEntity ref =
        contentRepository.saveCollectionContent(
            ContentCollectionEntity.builder()
                .referencedCollection(
                    collectionRepository.findById(childId).orElseThrow(IllegalStateException::new))
                .build());
    collectionRepository.saveContent(
        CollectionContentEntity.builder()
            .collectionId(parentId)
            .contentId(ref.getId())
            .orderIndex(0)
            .visible(true)
            .build());
    assertThat(collectionRepository.findAllReferencedCollectionIdsByParentId(parentId))
        .isNotEmpty();
    return parentId;
  }

  @Test
  @DisplayName("linkContentToCollection persists an IMAGE into a collection that holds children")
  void linkContentToCollection_realWrapper_isPersisted() {
    Long wrapperId = seedWrapper("ruleb-link");
    Long imageId = seedImage("ruleb-link-image");

    contentService.linkContentToCollection(wrapperId, imageId, 1, true);

    assertThat(collectionRepository.findContentByCollectionIdAndContentId(wrapperId, imageId))
        .isPresent();
  }

  @Test
  @DisplayName("handleAddToCollections persists an IMAGE into a collection that holds children")
  void handleAddToCollections_realWrapper_isPersisted() {
    Long wrapperId = seedWrapper("ruleb-add");
    Long imageId = seedImage("ruleb-add-image");

    contentMutationUtil.handleAddToCollections(
        imageId, List.of(new Records.ChildCollection(wrapperId, null, null, null, true, null)));

    assertThat(collectionRepository.findContentByCollectionIdAndContentId(wrapperId, imageId))
        .isPresent();
  }

  @Test
  @DisplayName("processFilesFromDisk accepts a collection that holds children as an upload target")
  void processFilesFromDisk_realWrapper_isAccepted() {
    Long wrapperId = seedWrapper("ruleb-upload");

    assertThatCode(
            () ->
                imageUploadPipelineService.processFilesFromDisk(
                    wrapperId, new DiskUploadRequest(List.of(), null)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a wrapper ends up holding both a child collection and an image")
  void wrapperHoldsBothChildCollectionsAndImages() {
    // The invariant Rule B removes, stated positively: one collection, mixed membership.
    Long wrapperId = seedWrapper("ruleb-mixed");
    Long imageId = seedImage("ruleb-mixed-image");

    contentService.linkContentToCollection(wrapperId, imageId, 1, true);

    assertThat(collectionRepository.findAllReferencedCollectionIdsByParentId(wrapperId))
        .isNotEmpty();
    assertThat(contentService.findImagesForCollection(wrapperId))
        .extracting(edens.zac.portfolio.backend.entity.ContentImageEntity::getId)
        .containsExactly(imageId);
  }
}
