package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.GalleryAccessEntity;
import edens.zac.portfolio.backend.types.Role;
import edens.zac.portfolio.backend.types.UserStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class GalleryAccessRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private GalleryAccessRepository galleryAccessRepository;
  @Autowired private AppUserRepository userRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Long seedUser(String email) {
    return userRepository.insert(
        AppUserEntity.builder()
            .email(email)
            .name(email)
            .role(Role.CLIENT)
            .webauthnUserHandle(UUID.randomUUID())
            .status(UserStatus.ACTIVE)
            .build());
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
  void insertThenFindByUserIdRoundTrips() {
    Long userId = seedUser("ga@example.com");
    Long collectionId = seedCollection("ga-collection-1");

    Long id =
        galleryAccessRepository.insert(
            GalleryAccessEntity.builder()
                .userId(userId)
                .collectionId(collectionId)
                .canDownload(true)
                .canTag(false)
                .build());
    assertThat(id).isNotNull();

    List<GalleryAccessEntity> grants = galleryAccessRepository.findByUserId(userId);
    assertThat(grants).hasSize(1);
    assertThat(grants.get(0).getCollectionId()).isEqualTo(collectionId);
    assertThat(grants.get(0).isCanDownload()).isTrue();
    assertThat(grants.get(0).isCanTag()).isFalse();
    assertThat(grants.get(0).getGrantedAt()).isNotNull();
  }

  @Test
  void findByUserIdReturnsEmptyForUserWithNoGrants() {
    Long userId = seedUser("empty@example.com");
    assertThat(galleryAccessRepository.findByUserId(userId)).isEmpty();
  }

  @Test
  void duplicateUserCollectionViolatesUniqueConstraint() {
    Long userId = seedUser("dup-ga@example.com");
    Long collectionId = seedCollection("ga-collection-2");
    galleryAccessRepository.insert(
        GalleryAccessEntity.builder().userId(userId).collectionId(collectionId).build());
    assertThatThrownBy(
            () ->
                galleryAccessRepository.insert(
                    GalleryAccessEntity.builder()
                        .userId(userId)
                        .collectionId(collectionId)
                        .build()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deletingUserCascadesGrants() {
    Long userId = seedUser("cascade-ga@example.com");
    Long collectionId = seedCollection("ga-collection-3");
    galleryAccessRepository.insert(
        GalleryAccessEntity.builder().userId(userId).collectionId(collectionId).build());

    jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    assertThat(galleryAccessRepository.findByUserId(userId)).isEmpty();
  }
}
