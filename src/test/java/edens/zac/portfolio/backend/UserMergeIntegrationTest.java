package edens.zac.portfolio.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.controller.admin.UserRequests.MergePreview;
import edens.zac.portfolio.backend.controller.admin.UserRequests.MergeResult;
import edens.zac.portfolio.backend.services.UserMergeService;
import java.util.Optional;
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

  @Test
  void mergeRepointsTagsCollapsesDuplicatesAndDeletesSource() {
    Long account = newAccount("danny@danny.com", "Danny");
    Long person = newPerson("Danny Nieves");
    Long onlyOnPerson = newImageTaggedWith(person);
    Long shared = newImageTaggedWith(person);
    // account is ALSO tagged on `shared` -> that source row must collapse, not duplicate.
    jdbc.update(
        "INSERT INTO content_image_people (content_id, person_id) VALUES (?, ?)", shared, account);

    MergeResult result = userMergeService.merge(person, account);

    assertThat(result.duplicatesCollapsed()).isEqualTo(1);
    // Source had two image tags before the merge; reported `movedImageTags` is the gross source
    // count (preview-coherent), independent of how many collapsed on landing.
    assertThat(result.movedImageTags()).isEqualTo(2);
    // source row gone
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, person))
        .isZero();
    // both images now point at the account, no duplicate on `shared`
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
                onlyOnPerson,
                account))
        .isEqualTo(1);
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
