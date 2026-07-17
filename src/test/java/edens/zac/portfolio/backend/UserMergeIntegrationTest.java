package edens.zac.portfolio.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.controller.admin.UserRequests.MergePreview;
import edens.zac.portfolio.backend.controller.admin.UserRequests.MergeResult;
import edens.zac.portfolio.backend.services.UserMergeService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Round-trips {@link UserMergeService} against a real Postgres container: seeds two identities with
 * overlapping tags and asserts the merge re-points tags, collapses duplicates, and hard-deletes the
 * source — and that a real account can never be the source. Seeding mirrors {@link
 * IdentityMergeMigrationIntegrationTest} (post-V35 shape: {@code users} + {@code person_id} joins).
 */
class UserMergeIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private UserMergeService userMergeService;

  private Long newPerson(String name) {
    jdbc.update(
        "INSERT INTO users (name, webauthn_user_handle, status) VALUES (?, gen_random_uuid(), 'PERSON')",
        name);
    return jdbc.queryForObject("SELECT id FROM users WHERE name = ?", Long.class, name);
  }

  private Long newAccount(String email, String name) {
    jdbc.update(
        "INSERT INTO users (email, name, webauthn_user_handle, status) "
            + "VALUES (?, ?, gen_random_uuid(), 'ACTIVE')",
        email,
        name);
    return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
  }

  private Long newImageTaggedWith(Long personId) {
    jdbc.update("INSERT INTO content (content_type) VALUES ('IMAGE')");
    Long contentId = jdbc.queryForObject("SELECT max(id) FROM content", Long.class);
    jdbc.update(
        "INSERT INTO content_image (id, title, image_url_web) VALUES (?, 'x', 'http://x')",
        contentId);
    jdbc.update(
        "INSERT INTO content_image_people (content_id, person_id) VALUES (?, ?)",
        contentId,
        personId);
    return contentId;
  }

  /** A real collection row. */
  private Long newCollection() {
    String slug = "merge-test-" + UUID.randomUUID();
    return jdbc.queryForObject(
        "INSERT INTO collection (title, slug, type, visibility) "
            + "VALUES (?, ?, 'CLIENT_GALLERY', 'UNLISTED') RETURNING id",
        Long.class,
        slug,
        slug);
  }

  private void tagPersonOnCollection(Long collectionId, Long personId) {
    jdbc.update(
        "INSERT INTO collection_people (collection_id, person_id) VALUES (?, ?)",
        collectionId,
        personId);
  }

  @Test
  void mergeRepointsTagsCollapsesDuplicatesAndDeletesSource() {
    Long account = newAccount("danny@danny.com", "Danny");
    Long person = newPerson("Danny Nieves");

    // --- image tags ---
    Long onlyOnPersonImage = newImageTaggedWith(person);
    Long sharedImage = newImageTaggedWith(person);
    // account is ALSO tagged on `sharedImage` -> that source row must collapse, not duplicate.
    jdbc.update(
        "INSERT INTO content_image_people (content_id, person_id) VALUES (?, ?)",
        sharedImage,
        account);

    // --- collection tags (exercises the (collection_id, person_id) PK dedupe branch) ---
    Long onlyOnPersonCollection = newCollection();
    tagPersonOnCollection(onlyOnPersonCollection, person);
    Long sharedCollection = newCollection();
    tagPersonOnCollection(sharedCollection, person);
    // account is ALSO tagged on `sharedCollection` -> the source row must collapse on re-point,
    // otherwise the UPDATE would hit a duplicate-key violation on the composite PK.
    tagPersonOnCollection(sharedCollection, account);

    MergeResult result = userMergeService.merge(person, account);

    // One image overlap + one collection overlap collapse.
    assertThat(result.duplicatesCollapsed()).isEqualTo(2);
    // Source had two image tags + two collection tags before the merge; reported moved counts are
    // the gross source counts (preview-coherent), independent of how many collapsed on landing.
    assertThat(result.movedImageTags()).isEqualTo(2);
    assertThat(result.movedCollections()).isEqualTo(2);

    // source row gone
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, person))
        .isZero();

    // both images now point at the account, no duplicate on `sharedImage`
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM content_image_people WHERE person_id = ?",
                Integer.class,
                account))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM content_image_people WHERE content_id = ? AND person_id = ?",
                Integer.class,
                onlyOnPersonImage,
                account))
        .isEqualTo(1);

    // both collections now point at the account, with NO duplicate row on `sharedCollection`
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM collection_people WHERE person_id = ?",
                Integer.class,
                account))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM collection_people WHERE collection_id = ? AND person_id = ?",
                Integer.class,
                sharedCollection,
                account))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM collection_people WHERE collection_id = ? AND person_id = ?",
                Integer.class,
                onlyOnPersonCollection,
                account))
        .isEqualTo(1);
    // no orphaned source rows remain in either join
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM collection_people WHERE person_id = ?",
                Integer.class,
                person))
        .isZero();
  }

  @Test
  void previewReportsCountsWithoutMutating() {
    Long account = newAccount("a@a.com", "Acct");
    Long person = newPerson("Tag Person");
    newImageTaggedWith(person);

    Optional<MergePreview> preview = userMergeService.preview(person, account);

    assertThat(preview).isPresent();
    assertThat(preview.get().imageTagCount()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, person))
        .isEqualTo(1); // unchanged
  }

  @Test
  void refusesToDeleteARealAccount() {
    Long accountA = newAccount("keep@x.com", "Keep");
    Long accountB = newAccount("loser@x.com", "Loser");
    assertThatThrownBy(() -> userMergeService.merge(accountB, accountA))
        .isInstanceOf(IllegalStateException.class);
  }
}
