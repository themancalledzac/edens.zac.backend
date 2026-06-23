package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.dao.GalleryAccessRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class GalleryAccessServiceIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private GalleryAccessService service;
  @Autowired private GalleryAccessRepository grants;
  @Autowired private PersonRepository people;
  @Autowired private JdbcTemplate jdbc;

  private Long seedUser(String email) {
    return jdbc.queryForObject(
        "INSERT INTO app_user (email, role, webauthn_user_handle, status) "
            + "VALUES (?, 'CLIENT', gen_random_uuid(), 'ACTIVE') RETURNING id",
        Long.class,
        email);
  }

  private Long seedPerson(String name) {
    return jdbc.queryForObject(
        "INSERT INTO content_people (person_name, slug) VALUES (?, ?) RETURNING id",
        Long.class,
        name,
        name.toLowerCase().replace(' ', '-'));
  }

  private Long seedCollection(String slug, String type) {
    return jdbc.queryForObject(
        "INSERT INTO collection (title, slug, type, visibility) "
            + "VALUES ('G', ?, ?, 'UNLISTED') RETURNING id",
        Long.class,
        slug,
        type);
  }

  @Test
  void clientGalleryLinkedPersonGetsGrant_idempotently() {
    Long adminId = seedUser("admin@example.com");
    Long userId = seedUser("jane@example.com");
    Long personId = seedPerson("Jane");
    people.linkUser(personId, userId);
    Long collectionId = seedCollection("g-client", "CLIENT_GALLERY");

    service.syncFromCollectionPeople(collectionId, List.of(personId), adminId);
    service.syncFromCollectionPeople(collectionId, List.of(personId), adminId); // re-run

    assertThat(grants.existsByUserIdAndCollectionId(userId, collectionId)).isTrue();
    assertThat(grants.findByUserId(userId)).hasSize(1); // idempotent
  }

  @Test
  void nonClientGalleryCreatesNoGrant() {
    Long userId = seedUser("jane2@example.com");
    Long personId = seedPerson("Jane2");
    people.linkUser(personId, userId);
    Long collectionId = seedCollection("g-portfolio", "PORTFOLIO");

    service.syncFromCollectionPeople(collectionId, List.of(personId), 99L);

    assertThat(grants.findByUserId(userId)).isEmpty();
  }

  @Test
  void unlinkedPersonCreatesNoGrant() {
    Long collectionId = seedCollection("g-client2", "CLIENT_GALLERY");
    Long personId = seedPerson("NoAccount");

    service.syncFromCollectionPeople(collectionId, List.of(personId), 99L);

    assertThat(jdbc.queryForObject("SELECT count(*) FROM gallery_access", Integer.class)).isZero();
  }
}
