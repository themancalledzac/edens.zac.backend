package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.types.AccessLevel;
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
 * the de-duplicated union of person-tagged collections and member galleries, with a cover drawn
 * from the most-recent tagged image (Decision D2). Because the base class truncates only auth
 * tables, each test seeds uniquely named people/content and scopes assertions to those ids.
 */
class UserPageAssemblerIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private UserPageAssembler assembler;
  @Autowired private RoleRepository roleRepository;
  @Autowired private JdbcTemplate jdbc;

  // Since the V35 merge, the account and the person tag are one `users` row: the principal's id IS
  // the person id. A "linked person" test now seeds a single account row and tags it directly.
  private Long seedUser(String email) {
    return jdbc.queryForObject(
        "INSERT INTO users (name, email, webauthn_user_handle, status) "
            + "VALUES (?, ?, gen_random_uuid(), 'ACTIVE') RETURNING id",
        Long.class,
        email,
        email);
  }

  /** An account row whose display name drives the assembled title. */
  private Long seedUserNamed(String name) {
    return jdbc.queryForObject(
        "INSERT INTO users (name, email, webauthn_user_handle, status) "
            + "VALUES (?, ?, gen_random_uuid(), 'ACTIVE') RETURNING id",
        Long.class,
        name,
        name.toLowerCase().replace(' ', '-') + "-" + UUID.randomUUID() + "@example.com");
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
    // Access flows through roles now (user_collection is frozen): a fresh role the user joins,
    // carrying a GENERAL grant on the collection.
    Long roleId = roleRepository.createRole("userpage-grant-" + UUID.randomUUID(), null);
    roleRepository.addMember(roleId, userId, null);
    roleRepository.setCollectionGrant(roleId, collectionId, AccessLevel.GENERAL, null);
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

  /** Seed a user and set a description directly on the row. */
  private Long seedUserWithDescription(String name, String description) {
    Long id = seedUserNamed(name);
    jdbc.update("UPDATE users SET description = ? WHERE id = ?", description, id);
    return id;
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
  void unionsTaggedAndGrantedCollectionsDeDuplicatedWithRandomCover() {
    // The account row IS the person identity: tag and grant against the same id.
    Long userId = seedUserNamed("Jane Doe");

    Long tagged = seedCollection("CLIENT_GALLERY", LocalDateTime.now().minusDays(5));
    tagCollection(tagged, userId);

    Long granted = seedCollection("CLIENT_GALLERY", LocalDateTime.now().minusDays(1));
    grant(userId, granted);

    // A collection both tagged AND granted must collapse to a single block (de-dupe).
    Long both = seedCollection("CLIENT_GALLERY", LocalDateTime.now().minusDays(3));
    tagCollection(both, userId);
    grant(userId, both);

    seedTaggedImage(userId, "https://cdn/old.webp", LocalDateTime.now().minusYears(2));
    seedTaggedImage(userId, "https://cdn/new.webp", LocalDateTime.now().minusDays(2));

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

    // Cover is a random tagged image — assert it is one of the seeded images (not a fixed URL).
    assertThat(model.getCoverImage()).isNotNull();
    assertThat(model.getCoverImage().imageUrl())
        .isIn("https://cdn/old.webp", "https://cdn/new.webp");

    // No description was set on this user.
    assertThat(model.getDescription()).isNull();
  }

  @Test
  void memberOnlyUserWithNoPersonLinkStillGetsMemberCollections() {
    Long userId = seedUser("grantonly-" + UUID.randomUUID() + "@example.com");
    Long granted = seedCollection("CLIENT_GALLERY", LocalDateTime.now().minusDays(1));
    grant(userId, granted);

    CollectionModel model = assembler.assembleForUser(userId);

    assertThat(referencedCollectionIds(model)).containsExactly(granted);
    assertThat(model.getTitle()).isEqualTo("Your Galleries");
    assertThat(model.getCoverImage()).isNull();
  }

  @Test
  void emptyCaseUserWithNoPersonLinkAndNoMemberships() {
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
    Long userId = seedUserNamed("No Cover Person");
    Long tagged = seedCollection("CLIENT_GALLERY", LocalDateTime.now().minusDays(1));
    tagCollection(tagged, userId);

    CollectionModel model = assembler.assembleForUser(userId);

    assertThat(referencedCollectionIds(model)).containsExactly(tagged);
    assertThat(model.getCoverImage()).isNull();
    assertThat(model.getTitle()).isEqualTo("No Cover Person");
  }

  @Test
  void standaloneTaggedImageNotInAnyCollectionAppearsAsImageBlock() {
    Long userId = seedUserNamed("Standalone Image Person");
    // Tagged image with NO collection membership and NO grant.
    seedTaggedImage(userId, "https://cdn/standalone.webp", LocalDateTime.now().minusDays(1));

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
    Long userId = seedUserNamed("Gif Person");
    seedTaggedGif(userId, "https://cdn/anim.gif");

    CollectionModel model = assembler.assembleForUser(userId);

    assertThat(model.getContent()).hasSize(1);
    ContentModel block = model.getContent().get(0);
    assertThat(block).isInstanceOf(ContentModels.Gif.class);
    assertThat(((ContentModels.Gif) block).gifUrl()).isEqualTo("https://cdn/anim.gif");
    assertThat(block.orderIndex()).isZero();
  }

  @Test
  void bodyOrderingIsCollectionsThenContentBothDateDesc() {
    Long userId = seedUserNamed("Order Person");

    Long collection = seedCollection("CLIENT_GALLERY", LocalDateTime.now().minusDays(2));
    tagCollection(collection, userId);
    seedTaggedImage(userId, "https://cdn/img.webp", LocalDateTime.now().minusDays(3));
    seedTaggedGif(userId, "https://cdn/g.gif");

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

  @Test
  void taggedUserWithDescriptionHasCoverFromTaggedSetAndDescriptionSet() {
    Long userId = seedUserWithDescription("Described Person", "A portrait photographer.");

    // Tag two images; cover must be one of them.
    seedTaggedImage(userId, "https://cdn/a.webp", LocalDateTime.now().minusDays(1));
    seedTaggedImage(userId, "https://cdn/b.webp", LocalDateTime.now().minusDays(2));

    CollectionModel model = assembler.assembleForUser(userId);

    assertThat(model.getDescription()).isEqualTo("A portrait photographer.");
    assertThat(model.getCoverImage()).isNotNull();
    assertThat(model.getCoverImage().imageUrl()).isIn("https://cdn/a.webp", "https://cdn/b.webp");
  }

  @Test
  void untaggedUserWithDescriptionHasDescriptionSetAndNullCover() {
    Long userId = seedUserWithDescription("Untagged With Desc", "Grant-only viewer bio.");
    // This user has no tagged images, no tagged collections, no memberships.

    CollectionModel model = assembler.assembleForUser(userId);

    assertThat(model.getDescription()).isEqualTo("Grant-only viewer bio.");
    assertThat(model.getCoverImage()).isNull();
    assertThat(model.getContent()).isEmpty();
  }
}
