package edens.zac.portfolio.backend;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.controller.admin.AdminUserController;
import edens.zac.portfolio.backend.controller.admin.UserRequests.CreateUserResponse;
import edens.zac.portfolio.backend.controller.admin.UserRequests.UpdateUserRequest;
import edens.zac.portfolio.backend.controller.admin.UserRequests.UpgradeUserRequest;
import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.types.AccessLevel;
import edens.zac.portfolio.backend.types.UserStatus;
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
  @Autowired private RoleRepository roleRepository;

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

  /**
   * A {@code role_member} row pointing at a PERSON. {@code addMember} has refused to create one
   * since S-2, and no migration purged the rows that predate that guard -- so this is seeded by
   * hand precisely because the application can no longer produce it.
   */
  private Long seedDormantGrant(Long personId, String slug, AccessLevel level) {
    Long roleId = roleRepository.createRole(slug + "-role", null);
    jdbc.update(
        "INSERT INTO collection (title, slug, visibility) VALUES (?, ?, 'UNLISTED')", slug, slug);
    Long collectionId =
        jdbc.queryForObject("SELECT id FROM collection WHERE slug = ?", Long.class, slug);
    jdbc.update("INSERT INTO role_member (role_id, user_id) VALUES (?, ?)", roleId, personId);
    roleRepository.setCollectionGrant(roleId, collectionId, level, null);
    return collectionId;
  }

  private int membershipCount(Long userId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM role_member WHERE user_id = ?", Integer.class, userId);
  }

  /**
   * S-12: the upgrade keeps the row id, which is the feature -- every tag stays FK'd to it. The
   * cost is that a {@code role_member} row already pointing at the person is carried across too,
   * and a grant that was dormant becomes a live one under a login the person now controls.
   *
   * <p>The opening assertion is the part worth reading: {@code canView} answers <em>true</em> for
   * the PERSON already, because it joins {@code role_member} to {@code role_collection} and tests
   * no status at all. So the grant is not dormant at the authorization layer -- it is live there
   * the whole time, and the only thing standing between it and real access is that a PERSON cannot
   * log in. The upgrade supplies exactly that, which is why the inheritance is instant rather than
   * something that has to be triggered later.
   *
   * <p>Asserted on {@code canView} as well as on the row count, since the count alone would pass a
   * sweep that ran after the flip. Mutation this catches: drop the sweep from {@code upgradeUser}
   * and the upgraded account can read a collection nobody granted it.
   */
  @Test
  void upgradeDropsAGrantThatWasDormantWhileTheRowWasAPerson() {
    Long person = newPerson("Cora Dormant");
    Long collection = seedDormantGrant(person, "dormant-on-upgrade", AccessLevel.CLIENT);
    assertThat(roleRepository.canView(person, collection)).isTrue();

    ResponseEntity<CreateUserResponse> response =
        adminUserController.upgradeUser(person, new UpgradeUserRequest("cora@example.com"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, person))
        .isEqualTo("INVITED");
    assertThat(membershipCount(person)).isZero();
    assertThat(roleRepository.canView(person, collection)).isFalse();
  }

  /**
   * The second path onto the same state, which S-12 did not name: {@code updateUser} takes a bare
   * status and never checks that the existing row is an account, so an admin PATCH can turn a
   * PERSON into one without going through {@code upgradeUser} at all. Mutation this catches: wire
   * the sweep into {@code upgradeUser} alone and the PATCH still inherits the grant.
   */
  @Test
  void patchingAPersonIntoAnAccountAlsoDropsTheDormantGrant() {
    Long person = newPerson("Dana Patched");
    Long collection = seedDormantGrant(person, "dormant-on-patch", AccessLevel.CLIENT);

    adminUserController.updateUser(
        person, new UpdateUserRequest("dana@example.com", "Dana Patched", UserStatus.ACTIVE, null));

    assertThat(membershipCount(person)).isZero();
    assertThat(roleRepository.canView(person, collection)).isFalse();
  }

  /**
   * The scope guard: the sweep runs unconditionally on every admin PATCH, so a real account's role
   * memberships must survive one. Mutation this catches: drop the {@code status = 'PERSON'} test
   * from the statement and an ordinary rename strips the user's roles.
   */
  @Test
  void patchingARealAccountLeavesItsMembershipsAlone() {
    Long account = newAccount("evan@example.com", "Evan Member");
    Long roleId = roleRepository.createRole("evan-keeps-role", null);
    roleRepository.addMember(roleId, account, null);

    adminUserController.updateUser(
        account, new UpdateUserRequest(null, "Evan Renamed", UserStatus.ACTIVE, null));

    assertThat(membershipCount(account)).isEqualTo(1);
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
