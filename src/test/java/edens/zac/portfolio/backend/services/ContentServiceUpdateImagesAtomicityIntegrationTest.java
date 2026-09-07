package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.ContentImageUpdateRequest;
import edens.zac.portfolio.backend.model.Records;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Covers Bug #32: {@code ContentService.updateImages} reported per-item results that the database
 * did not agree with. Every item shared one transaction, so a failure part-way through an item left
 * that item's earlier writes committed while the response called it a failure, and a Postgres-level
 * error poisoned the transaction so nothing committed at all while the response still listed the
 * earlier items as succeeded.
 *
 * <p>Both failure shapes are covered, because they broke the contract in opposite directions. The
 * assertions are what the per-item error list is supposed to mean: a reported failure wrote
 * nothing, and a reported success is durable.
 *
 * <p>{@link AbstractPostgresIntegrationTest} is not {@code @Transactional}, so these writes really
 * commit and the assertions read committed state.
 */
class ContentServiceUpdateImagesAtomicityIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private ContentService contentService;
  @Autowired private JdbcTemplate jdbc;

  private Long seedImage(String title) {
    Long contentId =
        jdbc.queryForObject(
            "INSERT INTO content (content_type) VALUES ('IMAGE') RETURNING id", Long.class);
    jdbc.update(
        "INSERT INTO content_image (id, title, image_url_web) VALUES (?, ?, ?)",
        contentId,
        title,
        "https://cdn.example.com/bug32-" + UUID.randomUUID() + ".jpg");
    return contentId;
  }

  private String titleOf(Long imageId) {
    return jdbc.queryForObject(
        "SELECT title FROM content_image WHERE id = ?", String.class, imageId);
  }

  private int tagCountOf(Long imageId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM content_tags WHERE content_id = ?", Integer.class, imageId);
  }

  private ContentImageUpdateRequest update(Long id, String title, String newTag) {
    ContentImageUpdateRequest request = new ContentImageUpdateRequest();
    request.setId(id);
    request.setTitle(title);
    request.setTags(new CollectionRequests.TagUpdate(null, List.of(newTag), null));
    return request;
  }

  /**
   * Shape (a): a Java-side failure between an item's writes. The tags are written, then {@code
   * handleAddToCollections} throws for a collection id that does not exist. The item is reported as
   * failed, so nothing it wrote may survive.
   */
  @Test
  void aJavaSideFailureRollsBackOnlyThatItemsWrites() {
    Long firstId = seedImage("bug32 first before");
    Long secondId = seedImage("bug32 second before");

    ContentImageUpdateRequest second = update(secondId, "bug32 second after", "bug32-b");
    second.setCollections(
        new CollectionRequests.CollectionUpdate(
            null,
            List.of(new Records.ChildCollection(9_999_999L, "gone", "gone", null, true, null)),
            null));

    Map<String, Object> response =
        contentService.updateImages(
            List.of(update(firstId, "bug32 first after", "bug32-a"), second));

    assertThat(errorsOf(response)).hasSize(1);
    assertThat(titleOf(firstId)).isEqualTo("bug32 first after");
    assertThat(tagCountOf(firstId)).isEqualTo(1);
    assertThat(titleOf(secondId)).isEqualTo("bug32 second before");
    assertThat(tagCountOf(secondId)).isZero();
  }

  /**
   * Shape (b): a Postgres error. {@code content_image.title} is {@code VARCHAR(255)}, so an
   * over-long title fails at that item's {@code saveImage} after its tags are written. Without a
   * savepoint this aborted the whole transaction, and the first item -- reported as a success --
   * committed nothing.
   */
  @Test
  void aPostgresErrorOnOneItemDoesNotDiscardTheItemsReportedAsSucceeding() {
    Long firstId = seedImage("bug32 pg first before");
    Long secondId = seedImage("bug32 pg second before");

    Map<String, Object> response =
        contentService.updateImages(
            List.of(
                update(firstId, "bug32 pg first after", "bug32-pg-a"),
                update(secondId, "x".repeat(300), "bug32-pg-b")));

    assertThat(errorsOf(response)).hasSize(1);
    assertThat(titleOf(firstId)).isEqualTo("bug32 pg first after");
    assertThat(tagCountOf(firstId)).isEqualTo(1);
    assertThat(titleOf(secondId)).isEqualTo("bug32 pg second before");
    assertThat(tagCountOf(secondId)).isZero();
  }

  @Test
  void abatchWithNoFailuresStillCommitsEveryItem() {
    Long firstId = seedImage("bug32 ok first before");
    Long secondId = seedImage("bug32 ok second before");

    Map<String, Object> response =
        contentService.updateImages(
            List.of(
                update(firstId, "bug32 ok first after", "bug32-ok-a"),
                update(secondId, "bug32 ok second after", "bug32-ok-b")));

    assertThat(errorsOf(response)).isEmpty();
    assertThat(titleOf(firstId)).isEqualTo("bug32 ok first after");
    assertThat(titleOf(secondId)).isEqualTo("bug32 ok second after");
    assertThat(tagCountOf(firstId)).isEqualTo(1);
    assertThat(tagCountOf(secondId)).isEqualTo(1);
  }

  @SuppressWarnings("unchecked")
  private List<String> errorsOf(Map<String, Object> response) {
    Object errors = response.get("errors");
    return errors == null ? List.of() : (List<String>) errors;
  }
}
