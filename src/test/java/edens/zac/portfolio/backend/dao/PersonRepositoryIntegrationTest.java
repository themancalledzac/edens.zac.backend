package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * S-3. Guards {@link PersonRepository#deletePersonById}'s {@code AND status = 'PERSON'} predicate
 * against a real Postgres row. That predicate is the whole of bug #1's fix -- since V35 merged
 * people into {@code users}, an admin delete-person call reaches account ids too, and only the
 * predicate stops one from destroying a real account.
 *
 * <p>Strip {@code AND status = 'PERSON'} and {@link #deleteLeavesARealAccountStanding} reddens.
 * Every other test that names this method mocks {@code PersonRepository}, so the SQL is invisible
 * to them; the 2026-08-24 mutation run passed with the predicate gone.
 */
class PersonRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private PersonRepository personRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Long seedAccount(String email) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO users (name, email, webauthn_user_handle, status) "
            + "VALUES (?, ?, gen_random_uuid(), 'ACTIVE') RETURNING id",
        Long.class,
        email,
        email);
  }

  private Long seedPerson(String name) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO users (name, webauthn_user_handle, status) "
            + "VALUES (?, gen_random_uuid(), 'PERSON') RETURNING id",
        Long.class,
        name);
  }

  private int rowCount(Long id) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM users WHERE id = ?", Integer.class, id);
  }

  @Test
  void deleteLeavesARealAccountStanding() {
    Long account = seedAccount("s3-account@x.com");

    assertThat(personRepository.deletePersonById(account)).isZero();
    assertThat(rowCount(account)).isEqualTo(1);
  }

  /** The other side of the predicate: without this, a no-op DELETE would pass the test above. */
  @Test
  void deleteRemovesATagOnlyPerson() {
    Long person = seedPerson("S3 Tag Person");

    assertThat(personRepository.deletePersonById(person)).isEqualTo(1);
    assertThat(rowCount(person)).isZero();
  }
}
