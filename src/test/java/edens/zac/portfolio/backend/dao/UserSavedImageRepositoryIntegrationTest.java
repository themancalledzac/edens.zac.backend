package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.UserSavedImageEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Exercises {@link UserSavedImageRepository} against the real V40 schema (boots Postgres + Flyway).
 * Seeds minimal FK-satisfying rows: {@code users} and an image (a {@code content} row plus its
 * {@code content_image} detail on a shared PK).
 */
class UserSavedImageRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private UserSavedImageRepository userSavedImageRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Long seedUser(String email) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO users (name, email, webauthn_user_handle, status) "
            + "VALUES (?, ?, gen_random_uuid(), 'ACTIVE') RETURNING id",
        Long.class,
        email,
        email);
  }

  private Long seedImage() {
    Long imageId =
        jdbcTemplate.queryForObject(
            "INSERT INTO content (content_type) VALUES ('IMAGE') RETURNING id", Long.class);
    jdbcTemplate.update(
        "INSERT INTO content_image (id, title, image_url_web) VALUES (?, ?, ?)",
        imageId,
        "img",
        "https://cdn.example.com/" + UUID.randomUUID() + ".jpg");
    return imageId;
  }

  @Test
  void insertThenListReturnsTheImage() {
    Long userId = seedUser("save-1-" + UUID.randomUUID() + "@example.com");
    Long imageId = seedImage();

    userSavedImageRepository.insert(
        UserSavedImageEntity.builder().userId(userId).imageId(imageId).build());

    assertThat(userSavedImageRepository.findImageIdsByUserId(userId)).containsExactly(imageId);
  }

  @Test
  void insertIsIdempotentOnConflict() {
    Long userId = seedUser("save-2-" + UUID.randomUUID() + "@example.com");
    Long imageId = seedImage();

    UserSavedImageEntity row =
        UserSavedImageEntity.builder().userId(userId).imageId(imageId).build();
    userSavedImageRepository.insert(row);
    userSavedImageRepository.insert(row);

    assertThat(userSavedImageRepository.findImageIdsByUserId(userId)).containsExactly(imageId);
  }

  @Test
  void deleteRemovesTheSave() {
    Long userId = seedUser("save-3-" + UUID.randomUUID() + "@example.com");
    Long imageId = seedImage();

    userSavedImageRepository.insert(
        UserSavedImageEntity.builder().userId(userId).imageId(imageId).build());

    assertThat(userSavedImageRepository.deleteByUserIdAndImageId(userId, imageId)).isEqualTo(1);
    assertThat(userSavedImageRepository.findImageIdsByUserId(userId)).isEmpty();
  }
}
