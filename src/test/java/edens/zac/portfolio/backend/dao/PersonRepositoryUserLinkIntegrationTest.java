package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class PersonRepositoryUserLinkIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private PersonRepository personRepository;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void truncatePeople() {
    jdbc.execute("TRUNCATE TABLE content_people RESTART IDENTITY CASCADE");
  }

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
        name.toLowerCase().replace(' ', '-'));
  }

  @Test
  void linkThenFindByUserId() {
    Long userId = seedUser("jane@example.com");
    Long personId = seedPerson("Jane Doe");

    personRepository.linkUser(personId, userId);

    assertThat(personRepository.findByUserId(userId))
        .isPresent()
        .get()
        .extracting(e -> e.getId())
        .isEqualTo(personId);
  }

  @Test
  void unlinkClearsTheLink() {
    Long userId = seedUser("jane@example.com");
    Long personId = seedPerson("Jane Doe");
    personRepository.linkUser(personId, userId);

    personRepository.unlinkUser(personId);

    assertThat(personRepository.findByUserId(userId)).isEmpty();
  }

  @Test
  void secondPersonLinkToSameUserViolatesUniqueIndex() {
    Long userId = seedUser("a@example.com");
    Long personOne = seedPerson("Person One");
    Long personTwo = seedPerson("Person Two");
    personRepository.linkUser(personOne, userId);

    assertThatThrownBy(() -> personRepository.linkUser(personTwo, userId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void findLinkedUserIdsByPersonIdsReturnsOnlyLinked() {
    Long userId = seedUser("jane@example.com");
    Long linked = seedPerson("Linked");
    Long unlinked = seedPerson("Unlinked");
    personRepository.linkUser(linked, userId);

    assertThat(personRepository.findLinkedUserIdsByPersonIds(java.util.List.of(linked, unlinked)))
        .containsExactly(userId);
  }
}
