package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.UserFollowedCollectionEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Exercises {@link UserFollowedCollectionRepository} against the real V40 schema (boots Postgres +
 * Flyway). Seeds minimal FK-satisfying rows: {@code users} and {@code collection}.
 */
class UserFollowedCollectionRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private UserFollowedCollectionRepository userFollowedCollectionRepository;
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

  @Test
  void insertThenListReturnsTheCollection() {
    Long userId = seedUser("follow-1-" + UUID.randomUUID() + "@example.com");
    Long collectionId = seedCollection("follow-collection-1-" + UUID.randomUUID());

    userFollowedCollectionRepository.insert(
        UserFollowedCollectionEntity.builder().userId(userId).collectionId(collectionId).build());

    assertThat(userFollowedCollectionRepository.findCollectionIdsByUserId(userId))
        .containsExactly(collectionId);
  }

  @Test
  void insertIsIdempotentOnConflict() {
    Long userId = seedUser("follow-2-" + UUID.randomUUID() + "@example.com");
    Long collectionId = seedCollection("follow-collection-2-" + UUID.randomUUID());

    UserFollowedCollectionEntity row =
        UserFollowedCollectionEntity.builder().userId(userId).collectionId(collectionId).build();
    userFollowedCollectionRepository.insert(row);
    userFollowedCollectionRepository.insert(row);

    assertThat(userFollowedCollectionRepository.findCollectionIdsByUserId(userId))
        .containsExactly(collectionId);
  }

  @Test
  void deleteRemovesTheFollow() {
    Long userId = seedUser("follow-3-" + UUID.randomUUID() + "@example.com");
    Long collectionId = seedCollection("follow-collection-3-" + UUID.randomUUID());

    userFollowedCollectionRepository.insert(
        UserFollowedCollectionEntity.builder().userId(userId).collectionId(collectionId).build());

    assertThat(userFollowedCollectionRepository.deleteByUserIdAndCollectionId(userId, collectionId))
        .isEqualTo(1);
    assertThat(userFollowedCollectionRepository.findCollectionIdsByUserId(userId)).isEmpty();
  }
}
