package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.GalleryAccessEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class GalleryAccessRepositoryExistsIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private GalleryAccessRepository repo;
  @Autowired private JdbcTemplate jdbc;

  private Long seedUser() {
    return jdbc.queryForObject(
        "INSERT INTO users (name, email, role, webauthn_user_handle, status) "
            + "VALUES ('Client', 'c@example.com', 'CLIENT', gen_random_uuid(), 'ACTIVE') RETURNING id",
        Long.class);
  }

  private Long seedCollection() {
    return jdbc.queryForObject(
        "INSERT INTO collection (title, slug, type, visibility) "
            + "VALUES ('G', 'g', 'CLIENT_GALLERY', 'UNLISTED') RETURNING id",
        Long.class);
  }

  @Test
  void existsIsFalseThenTrueAfterInsert() {
    Long userId = seedUser();
    Long collectionId = seedCollection();
    assertThat(repo.existsByUserIdAndCollectionId(userId, collectionId)).isFalse();

    repo.insert(
        GalleryAccessEntity.builder()
            .userId(userId)
            .collectionId(collectionId)
            .canDownload(true)
            .canTag(false)
            .build());

    assertThat(repo.existsByUserIdAndCollectionId(userId, collectionId)).isTrue();
  }
}
