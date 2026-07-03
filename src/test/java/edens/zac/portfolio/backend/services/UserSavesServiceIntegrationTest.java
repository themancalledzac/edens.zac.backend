package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.model.ContentModels;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Exercises {@link UserSavesService#listSavedImages} end-to-end against the real schema: seeds a
 * user + saved images and asserts full {@link ContentModels.Image} models come back newest-saved
 * first with real fields populated.
 */
class UserSavesServiceIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private UserSavesService userSavesService;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Long seedUser(String email) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO users (name, email, webauthn_user_handle, status) "
            + "VALUES (?, ?, gen_random_uuid(), 'ACTIVE') RETURNING id",
        Long.class,
        email,
        email);
  }

  private Long seedImage(String title, String webUrl) {
    Long imageId =
        jdbcTemplate.queryForObject(
            "INSERT INTO content (content_type) VALUES ('IMAGE') RETURNING id", Long.class);
    jdbcTemplate.update(
        "INSERT INTO content_image (id, title, image_url_web) VALUES (?, ?, ?)",
        imageId,
        title,
        webUrl);
    return imageId;
  }

  private void save(Long userId, Long imageId, Instant createdAt) {
    jdbcTemplate.update(
        "INSERT INTO user_saved_image (user_id, image_id, created_at) VALUES (?, ?, ?)",
        userId,
        imageId,
        Timestamp.from(createdAt));
  }

  @Test
  void listSavedImagesReturnsFullModelsNewestFirst() {
    Long userId = seedUser("saves-img-" + UUID.randomUUID() + "@example.com");
    String olderUrl = "https://cdn.example.com/" + UUID.randomUUID() + ".jpg";
    String newerUrl = "https://cdn.example.com/" + UUID.randomUUID() + ".jpg";
    Long olderImage = seedImage("Older", olderUrl);
    Long newerImage = seedImage("Newer", newerUrl);

    Instant now = Instant.now();
    save(userId, olderImage, now.minusSeconds(60));
    save(userId, newerImage, now);

    List<ContentModels.Image> images = userSavesService.listSavedImages(userId);

    assertThat(images).hasSize(2);
    assertThat(images.get(0).id()).isEqualTo(newerImage);
    assertThat(images.get(0).title()).isEqualTo("Newer");
    assertThat(images.get(0).imageUrl()).isEqualTo(newerUrl);
    assertThat(images.get(1).id()).isEqualTo(olderImage);
    assertThat(images.get(1).title()).isEqualTo("Older");
    assertThat(images.get(1).imageUrl()).isEqualTo(olderUrl);
  }

  @Test
  void listSavedImagesIsEmptyWhenNoneSaved() {
    Long userId = seedUser("saves-img-empty-" + UUID.randomUUID() + "@example.com");

    assertThat(userSavesService.listSavedImages(userId)).isEmpty();
  }

  @Test
  void savesAreIsolatedPerUser_userAneverSeesUserBsaves() {
    Long userA = seedUser("saves-a-" + UUID.randomUUID() + "@example.com");
    Long userB = seedUser("saves-b-" + UUID.randomUUID() + "@example.com");
    Long imageA = seedImage("A's image", "https://cdn.example.com/" + UUID.randomUUID() + ".jpg");
    Long imageB = seedImage("B's image", "https://cdn.example.com/" + UUID.randomUUID() + ".jpg");

    userSavesService.add(userA, imageA);
    userSavesService.add(userB, imageB);

    // User A sees only their own save; user B's save never leaks into A's list.
    assertThat(userSavesService.listSavedImageIds(userA)).containsExactly(imageA);
    assertThat(userSavesService.listSavedImageIds(userA)).doesNotContain(imageB);
    assertThat(userSavesService.listSavedImages(userA))
        .extracting(ContentModels.Image::id)
        .containsExactly(imageA);

    // And symmetrically for user B.
    assertThat(userSavesService.listSavedImageIds(userB)).containsExactly(imageB);
    assertThat(userSavesService.listSavedImageIds(userB)).doesNotContain(imageA);
  }
}
