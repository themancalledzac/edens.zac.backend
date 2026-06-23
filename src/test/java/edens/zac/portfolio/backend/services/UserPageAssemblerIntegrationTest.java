package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Exercises {@link UserPageAssembler} against a real schema: the synthetic {@code /user} model is
 * the de-duplicated union of person-tagged collections and granted galleries, with a cover drawn
 * from the most-recent tagged image (Decision D2). Because the base class truncates only auth
 * tables, each test seeds uniquely named people/content and scopes assertions to those ids.
 */
class UserPageAssemblerIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private UserPageAssembler assembler;
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
        name.toLowerCase().replace(' ', '-') + "-" + UUID.randomUUID());
  }

  private Long seedCollection(String type, LocalDateTime date) {
    return jdbc.queryForObject(
        "INSERT INTO collection (title, slug, type, visibility, collection_date) "
            + "VALUES ('Gallery', ?, ?, 'UNLISTED', ?) RETURNING id",
        Long.class,
        "g-" + UUID.randomUUID(),
        type,
        date == null ? null : Timestamp.valueOf(date));
  }

  private void tagCollection(Long collectionId, Long personId) {
    jdbc.update(
        "INSERT INTO collection_people (collection_id, person_id) VALUES (?, ?)",
        collectionId,
        personId);
  }

  private void grant(Long userId, Long collectionId) {
    jdbc.update(
        "INSERT INTO gallery_access (user_id, collection_id, can_download, can_tag) "
            + "VALUES (?, ?, true, false)",
        userId,
        collectionId);
  }

  private Long seedTaggedImage(Long personId, String webUrl, LocalDateTime captureDate) {
    Long contentId =
        jdbc.queryForObject(
            "INSERT INTO content (content_type) VALUES ('IMAGE') RETURNING id", Long.class);
    jdbc.update(
        "INSERT INTO content_image (id, title, image_url_web, capture_date) VALUES (?, ?, ?, ?)",
        contentId,
        "img",
        webUrl,
        captureDate == null ? null : Timestamp.valueOf(captureDate));
    jdbc.update(
        "INSERT INTO content_image_people (content_id, person_id) VALUES (?, ?)",
        contentId,
        personId);
    return contentId;
  }

  private static List<Long> referencedCollectionIds(CollectionModel model) {
    return model.getContent().stream()
        .map(ContentModels.Collection.class::cast)
        .map(ContentModels.Collection::referencedCollectionId)
        .toList();
  }

  @Test
  void unionsTaggedAndGrantedCollectionsDeDuplicatedWithMostRecentCover() {
    Long userId = seedUser("jane-" + UUID.randomUUID() + "@example.com");
    Long personId = seedPerson("Jane Doe");
    people.linkUser(personId, userId);

    Long tagged = seedCollection("CLIENT_GALLERY", LocalDateTime.now().minusDays(5));
    tagCollection(tagged, personId);

    Long granted = seedCollection("CLIENT_GALLERY", LocalDateTime.now().minusDays(1));
    grant(userId, granted);

    // A collection both tagged AND granted must collapse to a single block (de-dupe).
    Long both = seedCollection("CLIENT_GALLERY", LocalDateTime.now().minusDays(3));
    tagCollection(both, personId);
    grant(userId, both);

    seedTaggedImage(personId, "https://cdn/old.webp", LocalDateTime.now().minusYears(2));
    seedTaggedImage(personId, "https://cdn/new.webp", LocalDateTime.now().minusDays(2));

    CollectionModel model = assembler.assembleForUser(userId);

    assertThat(model.getSlug()).isEqualTo("user");
    assertThat(model.getTitle()).isEqualTo("Jane Doe");
    assertThat(model.getType()).isEqualTo(CollectionType.PARENT);
    assertThat(model.getVisibility()).isEqualTo(CollectionVisibility.UNLISTED);

    List<Long> referenced = referencedCollectionIds(model);
    assertThat(referenced).containsExactlyInAnyOrder(tagged, granted, both);
    assertThat(model.getContentCount()).isEqualTo(3);

    // Deterministic ordering: collection_date desc -> granted (-1d), both (-3d), tagged (-5d).
    assertThat(referenced).containsExactly(granted, both, tagged);

    // Cover is the most-recent tagged image (D2).
    assertThat(model.getCoverImage()).isNotNull();
    assertThat(model.getCoverImage().imageUrl()).isEqualTo("https://cdn/new.webp");
  }

  @Test
  void grantOnlyUserWithNoPersonLinkStillGetsGrantedCollections() {
    Long userId = seedUser("grantonly-" + UUID.randomUUID() + "@example.com");
    Long granted = seedCollection("CLIENT_GALLERY", LocalDateTime.now().minusDays(1));
    grant(userId, granted);

    CollectionModel model = assembler.assembleForUser(userId);

    assertThat(referencedCollectionIds(model)).containsExactly(granted);
    assertThat(model.getTitle()).isEqualTo("Your Galleries");
    assertThat(model.getCoverImage()).isNull();
  }

  @Test
  void emptyCaseUserWithNoPersonLinkAndNoGrants() {
    Long userId = seedUser("empty-" + UUID.randomUUID() + "@example.com");

    CollectionModel model = assembler.assembleForUser(userId);

    assertThat(model.getContent()).isEmpty();
    assertThat(model.getContentCount()).isZero();
    assertThat(model.getCoverImage()).isNull();
    assertThat(model.getTitle()).isEqualTo("Your Galleries");
    assertThat(model.getType()).isEqualTo(CollectionType.PARENT);
    assertThat(model.getVisibility()).isEqualTo(CollectionVisibility.UNLISTED);
  }

  @Test
  void linkedPersonWithoutTaggedImageHasNullCover() {
    Long userId = seedUser("nocover-" + UUID.randomUUID() + "@example.com");
    Long personId = seedPerson("No Cover Person");
    people.linkUser(personId, userId);
    Long tagged = seedCollection("CLIENT_GALLERY", LocalDateTime.now().minusDays(1));
    tagCollection(tagged, personId);

    CollectionModel model = assembler.assembleForUser(userId);

    assertThat(referencedCollectionIds(model)).containsExactly(tagged);
    assertThat(model.getCoverImage()).isNull();
    assertThat(model.getTitle()).isEqualTo("No Cover Person");
  }
}
