package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.UserSelectEntity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Exercises {@link UserSelectRepository} against the real V33 schema (boots Postgres + Flyway).
 * Seeds minimal FK-satisfying rows: {@code app_user}, {@code collection}, and an image (which is a
 * {@code content} row plus its {@code content_image} detail, joined on a shared PK).
 */
class UserSelectRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private UserSelectRepository userSelectRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Long seedUser(String email) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO users (name, email, webauthn_user_handle, status) "
            + "VALUES (?, ?, gen_random_uuid(), 'ACTIVE') RETURNING id",
        Long.class,
        email,
        email);
  }

  private Long seedCollection(String slug) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO collection (type, title, slug, visibility) "
            + "VALUES ('CLIENT_GALLERY', ?, ?, 'HIDDEN') RETURNING id",
        Long.class,
        "Title " + slug,
        slug);
  }

  private Long seedImage() {
    Long contentId =
        jdbcTemplate.queryForObject(
            "INSERT INTO content (content_type) VALUES ('IMAGE') RETURNING id", Long.class);
    jdbcTemplate.update(
        "INSERT INTO content_image (id, title, image_url_web) VALUES (?, ?, ?)",
        contentId,
        "img",
        "https://cdn.example.com/" + UUID.randomUUID() + ".jpg");
    return contentId;
  }

  @Test
  void insertThenListIdsReturnsTheImage() {
    Long userId = seedUser("sel-1-" + UUID.randomUUID() + "@example.com");
    Long collectionId = seedCollection("sel-collection-1-" + UUID.randomUUID());
    Long imageId = seedImage();

    userSelectRepository.insert(
        UserSelectEntity.builder()
            .userId(userId)
            .contentId(imageId)
            .collectionId(collectionId)
            .build());

    assertThat(userSelectRepository.findContentIdsByUserIdAndCollectionId(userId, collectionId))
        .containsExactly(imageId);
  }

  @Test
  void insertIsIdempotentOnConflict() {
    Long userId = seedUser("sel-2-" + UUID.randomUUID() + "@example.com");
    Long collectionId = seedCollection("sel-collection-2-" + UUID.randomUUID());
    Long imageId = seedImage();

    UserSelectEntity row =
        UserSelectEntity.builder()
            .userId(userId)
            .contentId(imageId)
            .collectionId(collectionId)
            .build();
    userSelectRepository.insert(row);
    userSelectRepository.insert(row);

    assertThat(userSelectRepository.findContentIdsByUserIdAndCollectionId(userId, collectionId))
        .containsExactly(imageId);
  }

  @Test
  void deleteRemovesTheSelect() {
    Long userId = seedUser("sel-3-" + UUID.randomUUID() + "@example.com");
    Long collectionId = seedCollection("sel-collection-3-" + UUID.randomUUID());
    Long imageId = seedImage();

    userSelectRepository.insert(
        UserSelectEntity.builder()
            .userId(userId)
            .contentId(imageId)
            .collectionId(collectionId)
            .build());

    assertThat(userSelectRepository.deleteByUserIdAndContentId(userId, imageId)).isEqualTo(1);
    assertThat(userSelectRepository.findContentIdsByUserIdAndCollectionId(userId, collectionId))
        .isEmpty();
  }

  @Test
  void findByUserIdGroupsByCollectionNewestFirst() {
    Long userId = seedUser("sel-4-" + UUID.randomUUID() + "@example.com");
    Long collectionA = seedCollection("sel-collection-4a-" + UUID.randomUUID());
    Long collectionB = seedCollection("sel-collection-4b-" + UUID.randomUUID());
    Long image1 = seedImage();
    Long image2 = seedImage();
    Long image3 = seedImage();

    userSelectRepository.insert(
        UserSelectEntity.builder()
            .userId(userId)
            .contentId(image1)
            .collectionId(collectionA)
            .build());
    userSelectRepository.insert(
        UserSelectEntity.builder()
            .userId(userId)
            .contentId(image2)
            .collectionId(collectionA)
            .build());
    userSelectRepository.insert(
        UserSelectEntity.builder()
            .userId(userId)
            .contentId(image3)
            .collectionId(collectionB)
            .build());

    List<UserSelectEntity> all = userSelectRepository.findByUserId(userId);
    assertThat(all).hasSize(3);
    assertThat(all).allMatch(row -> row.getUserId().equals(userId));
    assertThat(all).extracting(UserSelectEntity::getContentId).contains(image1, image2, image3);
  }
}
