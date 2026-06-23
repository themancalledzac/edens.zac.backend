package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModel;
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

  private void grantWithExpiry(Long userId, Long collectionId, LocalDateTime expiresAt) {
    jdbc.update(
        "INSERT INTO gallery_access (user_id, collection_id, can_download, can_tag, expires_at) "
            + "VALUES (?, ?, true, false, ?)",
        userId,
        collectionId,
        Timestamp.valueOf(expiresAt));
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

  private Long seedTaggedGif(Long personId, String gifUrl) {
    Long contentId =
        jdbc.queryForObject(
            "INSERT INTO content (content_type) VALUES ('GIF') RETURNING id", Long.class);
    jdbc.update(
        "INSERT INTO content_gif (id, title, gif_url, gif_url_web) VALUES (?, ?, ?, ?)",
        contentId,
        "gif",
        gifUrl,
        gifUrl);
    jdbc.update(
        "INSERT INTO content_image_people (content_id, person_id) VALUES (?, ?)",
        contentId,
        personId);
    return contentId;
  }

  private static List<ContentModels.Collection> collectionBlocks(CollectionModel model) {
    return model.getContent().stream()
        .filter(ContentModels.Collection.class::isInstance)
        .map(ContentModels.Collection.class::cast)
        .toList();
  }

  private static List<Long> referencedCollectionIds(CollectionModel model) {
    return collectionBlocks(model).stream()
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

    // Body = 3 collection blocks + 2 standalone tagged-image blocks.
    assertThat(model.getContentCount()).isEqualTo(5);
    assertThat(model.getContent()).hasSize(5);

    // Deterministic ordering: collections first (collection_date desc -> granted -1d, both -3d,
    // tagged -5d), then standalone content (capture date desc -> new, old).
    assertThat(referenced).containsExactly(granted, both, tagged);
    assertThat(model.getContent().get(0)).isInstanceOf(ContentModels.Collection.class);
    assertThat(model.getContent().get(3)).isInstanceOf(ContentModels.Image.class);
    assertThat(((ContentModels.Image) model.getContent().get(3)).imageUrl())
        .isEqualTo("https://cdn/new.webp");
    assertThat(((ContentModels.Image) model.getContent().get(4)).imageUrl())
        .isEqualTo("https://cdn/old.webp");

    // orderIndex is reassigned sequentially across the whole body.
    for (int i = 0; i < model.getContent().size(); i++) {
      assertThat(model.getContent().get(i).orderIndex()).isEqualTo(i);
    }

    // Cover is the most-recent tagged image (D2).
    assertThat(model.getCoverImage()).isNotNull();
    assertThat(model.getCoverImage().imageUrl()).isEqualTo("https://cdn/new.webp");
  }

  @Test
  void expiredGrantIsExcludedWhileActiveGrantIsIncluded() {
    Long userId = seedUser("expiry-" + UUID.randomUUID() + "@example.com");

    Long expired = seedCollection("CLIENT_GALLERY", LocalDateTime.now().minusDays(2));
    grantWithExpiry(userId, expired, LocalDateTime.now().minusDays(1));

    Long active = seedCollection("CLIENT_GALLERY", LocalDateTime.now().minusDays(1));
    grant(userId, active);

    CollectionModel model = assembler.assembleForUser(userId);

    // Mirrors Phase 3 enforcement: an expired grant must not surface a gallery the click-through
    // would then password-gate.
    assertThat(referencedCollectionIds(model)).containsExactly(active);
    assertThat(referencedCollectionIds(model)).doesNotContain(expired);
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

  @Test
  void standaloneTaggedImageNotInAnyCollectionAppearsAsImageBlock() {
    Long userId = seedUser("standalone-img-" + UUID.randomUUID() + "@example.com");
    Long personId = seedPerson("Standalone Image Person");
    people.linkUser(personId, userId);
    // Tagged image with NO collection membership and NO grant.
    seedTaggedImage(personId, "https://cdn/standalone.webp", LocalDateTime.now().minusDays(1));

    CollectionModel model = assembler.assembleForUser(userId);

    assertThat(collectionBlocks(model)).isEmpty();
    assertThat(model.getContent()).hasSize(1);
    assertThat(model.getContent().get(0)).isInstanceOf(ContentModels.Image.class);
    assertThat(((ContentModels.Image) model.getContent().get(0)).imageUrl())
        .isEqualTo("https://cdn/standalone.webp");
    assertThat(model.getContent().get(0).orderIndex()).isZero();
  }

  @Test
  void taggedGifAppearsAsGifBlock() {
    Long userId = seedUser("gif-" + UUID.randomUUID() + "@example.com");
    Long personId = seedPerson("Gif Person");
    people.linkUser(personId, userId);
    seedTaggedGif(personId, "https://cdn/anim.gif");

    CollectionModel model = assembler.assembleForUser(userId);

    assertThat(model.getContent()).hasSize(1);
    ContentModel block = model.getContent().get(0);
    assertThat(block).isInstanceOf(ContentModels.Gif.class);
    assertThat(((ContentModels.Gif) block).gifUrl()).isEqualTo("https://cdn/anim.gif");
    assertThat(block.orderIndex()).isZero();
  }

  @Test
  void bodyOrderingIsCollectionsThenContentBothDateDesc() {
    Long userId = seedUser("order-" + UUID.randomUUID() + "@example.com");
    Long personId = seedPerson("Order Person");
    people.linkUser(personId, userId);

    Long collection = seedCollection("CLIENT_GALLERY", LocalDateTime.now().minusDays(2));
    tagCollection(collection, personId);
    seedTaggedImage(personId, "https://cdn/img.webp", LocalDateTime.now().minusDays(3));
    seedTaggedGif(personId, "https://cdn/g.gif");

    CollectionModel model = assembler.assembleForUser(userId);

    // Collection block first, then standalone content (image before gif), orderIndex 0..2.
    List<ContentModel> body = model.getContent();
    assertThat(body).hasSize(3);
    assertThat(body.get(0)).isInstanceOf(ContentModels.Collection.class);
    assertThat(body.get(1)).isInstanceOf(ContentModels.Image.class);
    assertThat(body.get(2)).isInstanceOf(ContentModels.Gif.class);
    assertThat(body.get(0).orderIndex()).isZero();
    assertThat(body.get(1).orderIndex()).isEqualTo(1);
    assertThat(body.get(2).orderIndex()).isEqualTo(2);
  }
}
