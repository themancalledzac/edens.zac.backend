package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.entity.LocationEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.Records;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Guards the six {@code ORDER BY lower(...)} sites whose result reaches a user without a Java
 * re-sort: {@code TagRepository.findAllByOrderByTagNameAsc} and {@code findTagsByCollectionIds},
 * {@code PersonRepository.findAllByOrderByPersonNameAsc}, {@code
 * LocationRepository.findAllByOrderByLocationNameAsc} and {@code findLocationsWithVisibleContent},
 * and {@code CollectionPeopleRepository.findPeopleForCollections}.
 *
 * <p>These must be Testcontainers tests. The production database sorts under the {@code C}
 * collation, so removing {@code lower(} from a site returns uppercase names first; a mocked
 * repository returns whatever the test handed it and cannot see ordering at all.
 *
 * <p>Each test seeds {@code Alpha}, {@code bravo}, {@code Charlie} behind a per-test random prefix
 * and filters the result to that prefix, because the shared container does not truncate {@code
 * tag}, {@code location} or {@code collection} between test classes. Under {@code C} those three
 * sort {@code Alpha, Charlie, bravo}; the assertions below demand {@code Alpha, bravo, Charlie}.
 */
class NameOrderCollationIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TagRepository tagRepository;
  @Autowired private PersonRepository personRepository;
  @Autowired private LocationRepository locationRepository;
  @Autowired private CollectionPeopleRepository collectionPeopleRepository;
  @Autowired private JdbcTemplate jdbc;

  private static String prefix() {
    return "nameorder-" + UUID.randomUUID() + "-";
  }

  /** Seeded in {@code C} order, so a query that lost its {@code ORDER BY} outright also fails. */
  private static List<String> mixedCaseNames(String prefix) {
    return List.of(prefix + "Alpha", prefix + "Charlie", prefix + "bravo");
  }

  private static List<String> caseInsensitiveOrder(String prefix) {
    return List.of(prefix + "Alpha", prefix + "bravo", prefix + "Charlie");
  }

  private Long seedTag(String name) {
    return jdbc.queryForObject(
        "INSERT INTO tag (tag_name, slug) VALUES (?, ?) RETURNING id", Long.class, name, name);
  }

  private Long seedLocation(String name) {
    return jdbc.queryForObject(
        "INSERT INTO location (location_name, slug) VALUES (?, ?) RETURNING id",
        Long.class,
        name,
        name);
  }

  private Long seedPerson(String name) {
    return jdbc.queryForObject(
        "INSERT INTO users (name, webauthn_user_handle, status)"
            + " VALUES (?, gen_random_uuid(), 'PERSON') RETURNING id",
        Long.class,
        name);
  }

  private Long seedListedCollection(String slug) {
    return jdbc.queryForObject(
        "INSERT INTO collection (title, slug, visibility) VALUES (?, ?, 'LISTED') RETURNING id",
        Long.class,
        slug,
        slug);
  }

  @Test
  void findAllByOrderByTagNameAsc_ordersCaseInsensitively() {
    String prefix = prefix();
    mixedCaseNames(prefix).forEach(this::seedTag);

    List<String> found =
        tagRepository.findAllByOrderByTagNameAsc().stream()
            .map(TagEntity::getTagName)
            .filter(name -> name.startsWith(prefix))
            .toList();

    assertThat(found).containsExactlyElementsOf(caseInsensitiveOrder(prefix));
  }

  @Test
  void findTagsByCollectionIds_ordersCaseInsensitively() {
    String prefix = prefix();
    Long collectionId = seedListedCollection(prefix + "collection");
    mixedCaseNames(prefix)
        .forEach(
            name ->
                jdbc.update(
                    "INSERT INTO collection_tags (collection_id, tag_id) VALUES (?, ?)",
                    collectionId,
                    seedTag(name)));

    List<String> found =
        tagRepository.findTagsByCollectionIds(List.of(collectionId)).get(collectionId).stream()
            .map(TagEntity::getTagName)
            .toList();

    assertThat(found).containsExactlyElementsOf(caseInsensitiveOrder(prefix));
  }

  @Test
  void findAllByOrderByPersonNameAsc_ordersCaseInsensitively() {
    String prefix = prefix();
    mixedCaseNames(prefix).forEach(this::seedPerson);

    List<String> found =
        personRepository.findAllByOrderByPersonNameAsc().stream()
            .map(ContentPersonEntity::getPersonName)
            .filter(name -> name.startsWith(prefix))
            .toList();

    assertThat(found).containsExactlyElementsOf(caseInsensitiveOrder(prefix));
  }

  @Test
  void findAllByOrderByLocationNameAsc_ordersCaseInsensitively() {
    String prefix = prefix();
    mixedCaseNames(prefix).forEach(this::seedLocation);

    List<String> found =
        locationRepository.findAllByOrderByLocationNameAsc().stream()
            .map(LocationEntity::getLocationName)
            .filter(name -> name.startsWith(prefix))
            .toList();

    assertThat(found).containsExactlyElementsOf(caseInsensitiveOrder(prefix));
  }

  @Test
  void findLocationsWithVisibleContent_ordersCaseInsensitively() {
    String prefix = prefix();
    Long collectionId = seedListedCollection(prefix + "collection");
    mixedCaseNames(prefix)
        .forEach(
            name ->
                jdbc.update(
                    "INSERT INTO collection_locations (collection_id, location_id) VALUES (?, ?)",
                    collectionId,
                    seedLocation(name)));

    List<String> found =
        locationRepository.findLocationsWithVisibleContent().stream()
            .map(Records.LocationWithCounts::name)
            .filter(name -> name.startsWith(prefix))
            .toList();

    assertThat(found).containsExactlyElementsOf(caseInsensitiveOrder(prefix));
  }

  @Test
  void findPeopleForCollections_ordersCaseInsensitively() {
    String prefix = prefix();
    Long collectionId = seedListedCollection(prefix + "collection");
    mixedCaseNames(prefix)
        .forEach(
            name ->
                jdbc.update(
                    "INSERT INTO collection_people (collection_id, person_id) VALUES (?, ?)",
                    collectionId,
                    seedPerson(name)));

    List<String> found =
        collectionPeopleRepository
            .findPeopleForCollections(List.of(collectionId))
            .get(collectionId)
            .stream()
            .map(Records.Person::name)
            .toList();

    assertThat(found).containsExactlyElementsOf(caseInsensitiveOrder(prefix));
  }
}
