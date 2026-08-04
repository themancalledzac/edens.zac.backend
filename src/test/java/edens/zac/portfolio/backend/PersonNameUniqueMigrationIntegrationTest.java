package edens.zac.portfolio.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies V53 enforces one identity per name (case-insensitive).
 *
 * <p>Person tags are name-keyed: the Lightroom plugin sends a bare person name and
 * PersonRepository.findByPersonNameIgnoreCase resolves it via queryForObject. Two rows sharing a
 * name made that throw, and the throw was swallowed, so exports silently dropped the person.
 */
class PersonNameUniqueMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private JdbcTemplate jdbc;

  @Test
  void uniqueLowercaseNameIndexExists() {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM pg_indexes WHERE tablename='users' AND indexname='idx_users_name_lower'",
            Integer.class);
    assertThat(count).isEqualTo(1);
  }

  @Test
  void noDuplicateNamesSurviveTheMigration() {
    Integer groups =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM (SELECT 1 FROM users GROUP BY LOWER(name) HAVING COUNT(*) > 1) g",
            Integer.class);
    assertThat(groups).isZero();
  }

  @Test
  void insertingASecondIdentityWithTheSameNameIsRejected() {
    jdbc.update(
        "INSERT INTO users (name, webauthn_user_handle, status) VALUES ('Dup Guard', gen_random_uuid(), 'PERSON')");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO users (name, webauthn_user_handle, status) VALUES ('Dup Guard', gen_random_uuid(), 'PERSON')"))
        .isInstanceOf(DuplicateKeyException.class);
  }

  /** The constraint is case-insensitive -- 'tara edens' must not slip past 'Tara Edens'. */
  @Test
  void insertingADifferentlyCasedDuplicateIsRejected() {
    jdbc.update(
        "INSERT INTO users (name, webauthn_user_handle, status) VALUES ('Case Guard', gen_random_uuid(), 'PERSON')");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO users (name, webauthn_user_handle, status) VALUES ('case guard', gen_random_uuid(), 'PERSON')"))
        .isInstanceOf(DuplicateKeyException.class);
  }

  /**
   * The whole point of the constraint: a name-keyed lookup can now only ever resolve to zero or one
   * identity, which is what makes PersonRepository.findByPersonNameIgnoreCase (a queryForObject)
   * total rather than a latent IncorrectResultSizeDataAccessException.
   */
  @Test
  void nameLookupResolvesToAtMostOneIdentity() {
    jdbc.update(
        "INSERT INTO users (name, webauthn_user_handle, status) VALUES ('Lookup Target', gen_random_uuid(), 'PERSON')");

    Integer matches =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE LOWER(name) = LOWER('lookup TARGET')", Integer.class);
    assertThat(matches).isEqualTo(1);
  }
}
