package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.types.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * S-3. Guards {@link PersonRepository#deletePersonById}'s {@code AND status = 'PERSON'} predicate
 * against a real Postgres row. That predicate is the whole of bug #1's fix -- since V35 merged
 * people into {@code users}, an admin delete-person call reaches account ids too, and only the
 * predicate stops one from destroying a real account.
 *
 * <p>Every other test that names this method mocks {@code PersonRepository}, so the SQL is
 * invisible to them; the 2026-08-24 mutation run passed with the predicate gone.
 *
 * <p>The original pair of cases seeded {@code ACTIVE} and {@code PERSON} only, which left a
 * mutation it could not see. Rewriting the predicate as {@code AND status <> 'ACTIVE'} kept both
 * green -- an ACTIVE row still survived, a PERSON row was still deleted -- while making every
 * INVITED and DISABLED account deletable through the people-delete endpoint. The surviving side is
 * now parameterized over every non-PERSON status instead of over one of them.
 *
 * <p>Derivation from the enum covers a fifth {@code UserStatus} automatically, and working rule 33
 * says to pair that with one literal pin so the derived source cannot quietly shrink: {@link
 * #personIsTheOnlyStatusThisMethodMayDelete} is that pin.
 */
class PersonRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private PersonRepository personRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Long seedUser(String name, UserStatus status) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO users (name, email, webauthn_user_handle, status) "
            + "VALUES (?, ?, gen_random_uuid(), ?) RETURNING id",
        Long.class,
        name,
        name + "@x.com",
        status.name());
  }

  private int rowCount(Long id) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM users WHERE id = ?", Integer.class, id);
  }

  @ParameterizedTest
  @EnumSource(value = UserStatus.class, names = "PERSON", mode = EnumSource.Mode.EXCLUDE)
  void deleteLeavesEveryNonPersonRowStanding(UserStatus status) {
    Long id = seedUser("s3-" + status.name().toLowerCase(java.util.Locale.ROOT), status);

    assertThat(personRepository.deletePersonById(id)).isZero();
    assertThat(rowCount(id)).isEqualTo(1);
  }

  /** The other side of the predicate: without this, a no-op DELETE would pass every case above. */
  @Test
  void deleteRemovesATagOnlyPerson() {
    Long person = seedUser("S3 Tag Person", UserStatus.PERSON);

    assertThat(personRepository.deletePersonById(person)).isEqualTo(1);
    assertThat(rowCount(person)).isZero();
  }

  /**
   * Working rule 33's pin. The parameterized case above asks the enum which statuses to try, so
   * deleting a constant would shrink the case list rather than redden anything. This states the set
   * in full, making a fifth status a decision someone has to make here rather than a silent gap.
   */
  @Test
  void personIsTheOnlyStatusThisMethodMayDelete() {
    assertThat(UserStatus.values())
        .containsExactlyInAnyOrder(
            UserStatus.INVITED, UserStatus.ACTIVE, UserStatus.DISABLED, UserStatus.PERSON);
  }
}
