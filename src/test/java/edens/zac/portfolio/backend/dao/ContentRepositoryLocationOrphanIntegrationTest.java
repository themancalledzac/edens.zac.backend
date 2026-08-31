package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentGifEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.types.ContentType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Covers the location page's orphan queries against real Postgres, because the whole of bug #19
 * lived in the SQL: {@code content_image_locations} is content-keyed (V27) so a GIF can be
 * location-tagged, while the query joined {@code content_image} and structurally dropped every
 * non-image row. A mocked repository cannot see that.
 *
 * <p>Location names are suffixed with a UUID because the shared Testcontainers Postgres does not
 * truncate {@code location} or {@code content} between test classes.
 */
class ContentRepositoryLocationOrphanIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private ContentRepository contentRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String locationName;
  private Long locationId;

  private void seedLocation() {
    locationName = "orphan-loc-" + UUID.randomUUID();
    locationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO location (location_name, slug) VALUES (?, ?) RETURNING id",
            Long.class,
            locationName,
            locationName);
  }

  private Long seedImage(String captureDate) {
    Long contentId =
        jdbcTemplate.queryForObject(
            "INSERT INTO content (content_type) VALUES ('IMAGE') RETURNING id", Long.class);
    jdbcTemplate.update(
        "INSERT INTO content_image (id, title, image_url_web, capture_date)"
            + " VALUES (?, ?, ?, CAST(? AS TIMESTAMP))",
        contentId,
        "an image",
        "https://cdn.example.com/" + UUID.randomUUID() + ".jpg",
        captureDate);
    return contentId;
  }

  private Long seedGif(String captureDate) {
    Long contentId =
        jdbcTemplate.queryForObject(
            "INSERT INTO content (content_type) VALUES ('GIF') RETURNING id", Long.class);
    jdbcTemplate.update(
        "INSERT INTO content_gif (id, title, gif_url, capture_date)"
            + " VALUES (?, ?, ?, CAST(? AS TIMESTAMP))",
        contentId,
        "a gif",
        "https://cdn.example.com/" + UUID.randomUUID() + ".gif",
        captureDate);
    return contentId;
  }

  private void tag(Long contentId) {
    jdbcTemplate.update(
        "INSERT INTO content_image_locations (content_id, location_id) VALUES (?, ?)",
        contentId,
        locationId);
  }

  @Test
  void aLocationTaggedGifSurfacesAlongsideImages() {
    seedLocation();
    Long imageId = seedImage("2024-01-01 10:00:00");
    Long gifId = seedGif("2024-06-01 10:00:00");
    tag(imageId);
    tag(gifId);

    List<ContentEntity> orphans =
        contentRepository.findOrphanContentByLocationName(locationName, List.of(), 50, 0);

    assertThat(orphans).extracting(ContentEntity::getId).containsExactly(gifId, imageId);
    assertThat(orphans)
        .extracting(ContentEntity::getContentType)
        .containsExactly(ContentType.GIF, ContentType.IMAGE);
    assertThat(orphans.get(0)).isInstanceOf(ContentGifEntity.class);
    assertThat(orphans.get(1)).isInstanceOf(ContentImageEntity.class);
  }

  @Test
  void theCountIncludesTaggedGifs() {
    seedLocation();
    tag(seedImage("2024-01-01 10:00:00"));
    tag(seedGif("2024-06-01 10:00:00"));

    assertThat(contentRepository.countOrphanContentByLocationName(locationName, List.of()))
        .isEqualTo(2L);
  }

  @Test
  void pagingRunsOverTheCombinedSetNotOneTypeAtATime() {
    seedLocation();
    Long newest = seedGif("2024-09-01 10:00:00");
    Long middle = seedImage("2024-06-01 10:00:00");
    Long oldest = seedGif("2024-03-01 10:00:00");
    tag(newest);
    tag(middle);
    tag(oldest);

    assertThat(contentRepository.findOrphanContentByLocationName(locationName, List.of(), 2, 0))
        .extracting(ContentEntity::getId)
        .containsExactly(newest, middle);
    assertThat(contentRepository.findOrphanContentByLocationName(locationName, List.of(), 2, 2))
        .extracting(ContentEntity::getId)
        .containsExactly(oldest);
  }

  @Test
  void contentHeldByAnExcludedCollectionIsNotAnOrphan() {
    seedLocation();
    Long gifId = seedGif("2024-06-01 10:00:00");
    Long imageId = seedImage("2024-01-01 10:00:00");
    tag(gifId);
    tag(imageId);

    String slug = "orphan-coll-" + UUID.randomUUID();
    Long collectionId =
        jdbcTemplate.queryForObject(
            "INSERT INTO collection (title, slug, visibility) VALUES (?, ?, 'LISTED') RETURNING id",
            Long.class,
            slug,
            slug);
    jdbcTemplate.update(
        "INSERT INTO collection_content (collection_id, content_id, order_index, visible)"
            + " VALUES (?, ?, 0, true)",
        collectionId,
        gifId);

    assertThat(
            contentRepository.findOrphanContentByLocationName(
                locationName, List.of(collectionId), 50, 0))
        .extracting(ContentEntity::getId)
        .containsExactly(imageId);
    assertThat(
            contentRepository.countOrphanContentByLocationName(locationName, List.of(collectionId)))
        .isEqualTo(1L);
  }
}
