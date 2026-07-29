package edens.zac.portfolio.backend;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.controller.admin.AdminUserController;
import edens.zac.portfolio.backend.controller.admin.UserRequests.CreateUserResponse;
import edens.zac.portfolio.backend.controller.admin.UserRequests.UpgradeUserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Round-trips {@link AdminUserController#upgradeUser} against a real Postgres container: seeds a
 * tag-only {@code PERSON} row (mirroring {@link UserMergeIntegrationTest}'s seeding), upgrades it,
 * and asserts the row's email/status flip, a single live invite is minted, and the person's image
 * tag stays FK'd to the same (now-upgraded) id — proving associations are untouched. Also pins the
 * two conflict rails (upgrading an account row, and an email already taken) leave state unchanged.
 *
 * <p>Invite delivery is not asserted here: {@code email.enabled} is false under the test profile,
 * so {@code EmailService} short-circuits to a log line. The send itself is pinned by {@code
 * AdminUserControllerTest.UpgradeUser}.
 */
class UserUpgradeIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private AdminUserController adminUserController;

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
  void upgradePersonSetsEmailFlipsStatusAndMintsInvite() {
    Long person = newPerson("Abby Bennett");
    Long taggedImage = newImageTaggedWith(person);

    ResponseEntity<CreateUserResponse> response =
        adminUserController.upgradeUser(person, new UpgradeUserRequest("abby@example.com"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().userId()).isEqualTo(person);
    assertThat(response.getBody().inviteUrl()).contains("/invite/");

    // Same row: email now set, status flipped to INVITED.
    assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, person))
        .isEqualTo("abby@example.com");
    assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, person))
        .isEqualTo("INVITED");

    // Exactly one live (unused, unexpired) invite for the upgraded id.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_invite "
                    + "WHERE user_id = ? AND used_at IS NULL AND expires_at > now()",
                Integer.class,
                person))
        .isEqualTo(1);

    // The image tag still references the SAME id — associations were not moved.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM content_image_people WHERE content_id = ? AND person_id = ?",
                Integer.class,
                taggedImage,
                person))
        .isEqualTo(1);
  }

  @Test
  void upgradeIsRejectedForAnAccountRow() {
    Long account = newAccount("real@example.com", "Real Account");

    ResponseEntity<CreateUserResponse> response =
        adminUserController.upgradeUser(account, new UpgradeUserRequest("new@example.com"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    // Untouched: an account row's email and status must not change.
    assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, account))
        .isEqualTo("real@example.com");
    assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, account))
        .isEqualTo("ACTIVE");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_invite WHERE user_id = ?", Integer.class, account))
        .isZero();
  }

  @Test
  void upgradeRejectsEmailAlreadyTaken() {
    Long account = newAccount("taken@example.com", "Existing");
    Long person = newPerson("Abby Bennett");

    ResponseEntity<CreateUserResponse> response =
        adminUserController.upgradeUser(person, new UpgradeUserRequest("taken@example.com"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    // The PERSON stays a PERSON with a NULL email; no invite minted.
    assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, person))
        .isEqualTo("PERSON");
    assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, person))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_invite WHERE user_id = ?", Integer.class, person))
        .isZero();
    // The existing account is untouched.
    assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, account))
        .isEqualTo("taken@example.com");
  }
}
