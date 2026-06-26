package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.dao.GalleryAccessRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class GalleryAccessServiceIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private GalleryAccessService service;
  @Autowired private GalleryAccessRepository grants;
  @Autowired private JdbcTemplate jdbc;

  // Since the V35 merge, an account and a person tag share one `users` row: an account is a
  // non-PERSON status row, a tag-only person is a status='PERSON' row.
  private Long seedUser(String email) {
    return jdbc.queryForObject(
        "INSERT INTO users (name, email, role, webauthn_user_handle, status) "
            + "VALUES (?, ?, 'CLIENT', gen_random_uuid(), 'ACTIVE') RETURNING id",
        Long.class,
        email,
        email);
  }

  private Long seedPerson(String name) {
    return jdbc.queryForObject(
        "INSERT INTO users (name, webauthn_user_handle, status) "
            + "VALUES (?, gen_random_uuid(), 'PERSON') RETURNING id",
        Long.class,
        name);
  }

  private Long seedCollection(String slug, String type) {
    return jdbc.queryForObject(
        "INSERT INTO collection (title, slug, type, visibility) "
            + "VALUES ('G', ?, ?, 'UNLISTED') RETURNING id",
        Long.class,
        slug,
        type);
  }

  /**
   * Insert a raw grant with an explicit expiry and download flag so the enforcement helpers can be
   * exercised against real rows (the upsert path always writes {@code expires_at = NULL}).
   */
  private void seedGrant(
      Long userId, Long collectionId, boolean canDownload, LocalDateTime expiresAt) {
    jdbc.update(
        "INSERT INTO gallery_access (user_id, collection_id, can_download, can_tag, expires_at) "
            + "VALUES (?, ?, ?, false, ?)",
        userId,
        collectionId,
        canDownload,
        expiresAt == null ? null : Timestamp.valueOf(expiresAt));
  }

  @Test
  void clientGalleryAccountPersonGetsGrant_idempotently() {
    Long adminId = seedUser("admin@example.com");
    // The account row IS the person identity post-merge: its id is the person id.
    Long userId = seedUser("jane@example.com");
    Long collectionId = seedCollection("g-client", "CLIENT_GALLERY");

    service.syncFromCollectionPeople(collectionId, List.of(userId), adminId);
    service.syncFromCollectionPeople(collectionId, List.of(userId), adminId); // re-run

    assertThat(grants.existsByUserIdAndCollectionId(userId, collectionId)).isTrue();
    assertThat(grants.findByUserId(userId)).hasSize(1); // idempotent
  }

  @Test
  void nonClientGalleryCreatesNoGrant() {
    Long userId = seedUser("jane2@example.com");
    Long collectionId = seedCollection("g-portfolio", "PORTFOLIO");

    service.syncFromCollectionPeople(collectionId, List.of(userId), 99L);

    assertThat(grants.findByUserId(userId)).isEmpty();
  }

  @Test
  void tagOnlyPersonCreatesNoGrant() {
    Long collectionId = seedCollection("g-client2", "CLIENT_GALLERY");
    // A status='PERSON' row has no account, so it must not receive a gallery_access grant.
    Long personId = seedPerson("NoAccount");

    service.syncFromCollectionPeople(collectionId, List.of(personId), 99L);

    assertThat(jdbc.queryForObject("SELECT count(*) FROM gallery_access", Integer.class)).isZero();
  }

  @Test
  void expiredGrantIsNotGranted() {
    Long userId = seedUser("expired@example.com");
    Long collectionId = seedCollection("g-expired", "CLIENT_GALLERY");
    seedGrant(userId, collectionId, true, LocalDateTime.now().minusDays(1));

    assertThat(service.hasGrant(userId, collectionId)).isFalse();
    assertThat(service.hasDownloadGrant(userId, collectionId)).isFalse();
  }

  @Test
  void activeDownloadableGrantIsGranted() {
    Long nullExpiryUser = seedUser("nullexpiry@example.com");
    Long futureExpiryUser = seedUser("futureexpiry@example.com");
    Long collectionId = seedCollection("g-active", "CLIENT_GALLERY");
    seedGrant(nullExpiryUser, collectionId, true, null);
    seedGrant(futureExpiryUser, collectionId, true, LocalDateTime.now().plusDays(1));

    assertThat(service.hasGrant(nullExpiryUser, collectionId)).isTrue();
    assertThat(service.hasDownloadGrant(nullExpiryUser, collectionId)).isTrue();
    assertThat(service.hasGrant(futureExpiryUser, collectionId)).isTrue();
    assertThat(service.hasDownloadGrant(futureExpiryUser, collectionId)).isTrue();
  }

  @Test
  void activeGrantWithoutDownloadAllowsAccessButNotDownload() {
    Long userId = seedUser("nodownload@example.com");
    Long collectionId = seedCollection("g-nodownload", "CLIENT_GALLERY");
    seedGrant(userId, collectionId, false, null);

    assertThat(service.hasGrant(userId, collectionId)).isTrue();
    assertThat(service.hasDownloadGrant(userId, collectionId)).isFalse();
  }
}
