package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

/** Cross-collection guard + join-row visibility write for the collaborator image surface. */
class CollaboratorEditGuardIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private CollectionService collectionService;
  @Autowired private JdbcTemplate jdbc;

  private long seedCollection(String slug) {
    jdbc.update(
        "INSERT INTO collection (title, slug, visibility) VALUES (?, ?, 'UNLISTED')", slug, slug);
    return jdbc.queryForObject("SELECT id FROM collection WHERE slug=?", Long.class, slug);
  }

  /**
   * Seed a minimal IMAGE content row and link it into the collection; returns the content id
   * (content_image.id shares the content id space -- same idiom as
   * ContentRepositoryRoleVisibilityIntegrationTest.seedImage).
   */
  private long seedImageIn(long collectionId, String title) {
    Long contentId =
        jdbc.queryForObject(
            "INSERT INTO content (content_type) VALUES ('IMAGE') RETURNING id", Long.class);
    jdbc.update(
        "INSERT INTO content_image (id, title, image_url_web) VALUES (?, ?, ?)",
        contentId,
        title,
        "https://cdn.example.com/guard-" + title + ".jpg");
    jdbc.update(
        "INSERT INTO collection_content (collection_id, content_id, visible) VALUES (?, ?, true)",
        collectionId,
        contentId);
    return contentId;
  }

  @Test
  void outsiderImageIdIs403AndNothingIsWritten() {
    long collA = seedCollection("guard-a");
    long collB = seedCollection("guard-b");
    long insider = seedImageIn(collA, "insider");
    long outsider = seedImageIn(collB, "outsider");

    assertThatThrownBy(
            () -> collectionService.requireImagesInCollection(collA, List.of(insider, outsider)))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining(String.valueOf(outsider));

    assertThatCode(() -> collectionService.requireImagesInCollection(collA, List.of(insider)))
        .doesNotThrowAnyException();
  }

  @Test
  void unknownCollectionIs404() {
    assertThatThrownBy(() -> collectionService.requireImagesInCollection(999999L, List.of(1L)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void updateImageVisibilityFlipsTheJoinRowOnly() {
    long collA = seedCollection("guard-vis-a");
    long collB = seedCollection("guard-vis-b");
    long content = seedImageIn(collA, "vis-shared");
    // Link the same content into B as well, then flip visibility only in A.
    jdbc.update(
        "INSERT INTO collection_content (collection_id, content_id, visible) VALUES (?, ?, true)",
        collB,
        content);
    collectionService.updateImageVisibility(collA, content, false);
    assertThat(visibleIn(collA, content)).isFalse();
    assertThat(visibleIn(collB, content)).isTrue();
  }

  @Test
  void updateImageVisibilityOnNonMemberRowIs404() {
    long collA = seedCollection("guard-vis-404");
    assertThatThrownBy(() -> collectionService.updateImageVisibility(collA, 424242L, false))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  private boolean visibleIn(long collectionId, long contentId) {
    return jdbc.queryForObject(
        "SELECT visible FROM collection_content WHERE collection_id=? AND content_id=?",
        Boolean.class,
        collectionId,
        contentId);
  }
}
