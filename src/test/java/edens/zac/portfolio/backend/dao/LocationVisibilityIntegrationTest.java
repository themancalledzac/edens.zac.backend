package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.model.Records;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Covers S-35: {@code findLocationsWithVisibleContent} counted an image whose only home is a
 * private gallery as an "orphan" of every location it is tagged with, so the {@code HAVING} listed
 * the location on that count alone. #309 fixed the location page's two orphan queries but not this
 * one, so the list advertised a location the page then rendered empty.
 *
 * <p>#309's test seeds no {@code collection_locations} row and so never reaches this query. These
 * cases seed one.
 *
 * <p>Locations are named {@code s35-<uuid>} because the shared container does not truncate {@code
 * location} between test classes, and this query returns every location in the database.
 */
class LocationVisibilityIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private LocationRepository locationRepository;
  @Autowired private JdbcTemplate jdbc;

  private Long seedLocation() {
    String name = "s35-" + UUID.randomUUID();
    return jdbc.queryForObject(
        "INSERT INTO location (location_name, slug) VALUES (?, ?) RETURNING id",
        Long.class,
        name,
        name);
  }

  private Long seedCollection(String visibility, String galleryPassword) {
    String slug = "s35-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO collection (title, slug, visibility, gallery_password) VALUES (?, ?, ?, ?)",
        slug,
        slug,
        visibility,
        galleryPassword);
    return jdbc.queryForObject("SELECT id FROM collection WHERE slug = ?", Long.class, slug);
  }

  private Long seedImage() {
    Long contentId =
        jdbc.queryForObject(
            "INSERT INTO content (content_type) VALUES ('IMAGE') RETURNING id", Long.class);
    jdbc.update(
        "INSERT INTO content_image (id, title, image_url_web) VALUES (?, ?, ?)",
        contentId,
        "s35 image",
        "https://cdn.example.com/s35-" + UUID.randomUUID() + ".jpg");
    return contentId;
  }

  private void addMembership(Long collectionId, Long contentId) {
    jdbc.update(
        "INSERT INTO collection_content (collection_id, content_id, order_index, visible)"
            + " VALUES (?, ?, 0, true)",
        collectionId,
        contentId);
  }

  private void tagImageWithLocation(Long contentId, Long locationId) {
    jdbc.update(
        "INSERT INTO content_image_locations (content_id, location_id) VALUES (?, ?)",
        contentId,
        locationId);
  }

  private void tagCollectionWithLocation(Long collectionId, Long locationId) {
    jdbc.update(
        "INSERT INTO collection_locations (collection_id, location_id) VALUES (?, ?)",
        collectionId,
        locationId);
  }

  private Optional<Records.LocationWithCounts> findSeeded(Long locationId) {
    return locationRepository.findLocationsWithVisibleContent().stream()
        .filter(row -> locationId.equals(row.id()))
        .findFirst();
  }

  @Test
  void locationHoldingOnlyPrivateGalleryImagesIsNotListed() {
    Long locationId = seedLocation();
    Long imageId = seedImage();
    Long galleryId = seedCollection("LISTED", "secret");
    addMembership(galleryId, imageId);
    tagImageWithLocation(imageId, locationId);
    tagCollectionWithLocation(galleryId, seedLocation());

    assertThat(findSeeded(locationId)).isEmpty();
  }

  @Test
  void locationHoldingAPubliclyVisibleOrphanIsListedWithThatCount() {
    Long locationId = seedLocation();
    Long imageId = seedImage();
    Long publicId = seedCollection("LISTED", null);
    addMembership(publicId, imageId);
    tagImageWithLocation(imageId, locationId);
    tagCollectionWithLocation(publicId, seedLocation());

    assertThat(findSeeded(locationId))
        .get()
        .extracting(Records.LocationWithCounts::imageCount)
        .isEqualTo(1);
  }

  @Test
  void anImageHeldByAListedCollectionAtThisLocationIsNotAnOrphanOfIt() {
    Long locationId = seedLocation();
    Long imageId = seedImage();
    Long publicId = seedCollection("LISTED", null);
    addMembership(publicId, imageId);
    tagImageWithLocation(imageId, locationId);
    tagCollectionWithLocation(publicId, locationId);

    assertThat(findSeeded(locationId))
        .get()
        .extracting(Records.LocationWithCounts::imageCount)
        .isEqualTo(0);
  }

  /**
   * Guards the change S-35's row proposed but which would have made this worse: a {@code
   * gallery_password} term on the {@code NOT EXISTS}. The image here is publicly visible elsewhere
   * and is held at this location by a LISTED password gallery, so it is already represented by that
   * gallery's tile and must not also appear in the orphan count. Adding the term would flip it to
   * 1.
   */
  @Test
  void anImageHeldAtThisLocationOnlyByAProtectedGalleryIsStillNotAnOrphan() {
    Long locationId = seedLocation();
    Long imageId = seedImage();
    Long publicElsewhereId = seedCollection("LISTED", null);
    Long galleryHereId = seedCollection("LISTED", "secret");
    addMembership(publicElsewhereId, imageId);
    addMembership(galleryHereId, imageId);
    tagImageWithLocation(imageId, locationId);
    tagCollectionWithLocation(publicElsewhereId, seedLocation());
    tagCollectionWithLocation(galleryHereId, locationId);

    assertThat(findSeeded(locationId))
        .get()
        .extracting(Records.LocationWithCounts::imageCount)
        .isEqualTo(0);
  }

  @Test
  void aPasswordProtectedGalleryStillMakesItsOwnLocationDiscoverable() {
    Long locationId = seedLocation();
    Long galleryId = seedCollection("LISTED", "secret");
    tagCollectionWithLocation(galleryId, locationId);

    assertThat(findSeeded(locationId))
        .get()
        .extracting(Records.LocationWithCounts::collectionCount)
        .isEqualTo(1);
  }
}
