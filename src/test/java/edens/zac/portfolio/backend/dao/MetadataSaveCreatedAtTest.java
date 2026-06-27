package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.entity.ContentCameraEntity;
import edens.zac.portfolio.backend.entity.ContentFilmTypeEntity;
import edens.zac.portfolio.backend.entity.ContentLensEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.entity.LocationEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.KeyHolder;

/**
 * Regression guard for the metadata {@code create*} NullPointerException.
 *
 * <p>The JDBC {@code save()} INSERT branches resolved a {@code created_at} value for the SQL
 * parameter but only wrote {@code id} back onto the entity — never {@code createdAt}. Callers that
 * then read {@code entity.getCreatedAt()} (notably {@code MetadataService.create*(...)} building
 * {@code Map.of("createdAt", saved.getCreatedAt())}) got {@code null}, and {@code Map.of} throws on
 * a null value. Each test asserts the saved entity carries a non-null {@code createdAt} (and id),
 * with no database — the generated-key call is mocked.
 */
@ExtendWith(MockitoExtension.class)
class MetadataSaveCreatedAtTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  private EquipmentRepository equipmentRepository;
  private TagRepository tagRepository;
  private PersonRepository personRepository;
  private LocationRepository locationRepository;

  @BeforeEach
  void setUp() {
    equipmentRepository = new EquipmentRepository(jdbcTemplate);
    tagRepository = new TagRepository(jdbcTemplate);
    personRepository = new PersonRepository(jdbcTemplate);
    locationRepository = new LocationRepository(jdbcTemplate);
    for (BaseDao repo :
        new BaseDao[] {equipmentRepository, tagRepository, personRepository, locationRepository}) {
      injectMockTemplate(repo);
    }
    // insertAndReturnId reads the generated key from the KeyHolder it passes in; populate that
    // KeyHolder so each save() gets a non-null id and proceeds to the createdAt writeback under
    // test.
    when(namedParameterJdbcTemplate.update(
            anyString(), any(SqlParameterSource.class), any(KeyHolder.class), any(String[].class)))
        .thenAnswer(
            invocation -> {
              KeyHolder keyHolder = invocation.getArgument(2);
              keyHolder.getKeyList().add(Map.of("id", 1L));
              return 1;
            });
  }

  private void injectMockTemplate(BaseDao repo) {
    try {
      Field field = BaseDao.class.getDeclaredField("namedParameterJdbcTemplate");
      field.setAccessible(true);
      field.set(repo, namedParameterJdbcTemplate);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to inject mock NamedParameterJdbcTemplate", e);
    }
  }

  @Test
  void saveCamera_populatesCreatedAt() {
    ContentCameraEntity saved =
        equipmentRepository.saveCamera(
            ContentCameraEntity.builder().cameraName("Mamiya 645 Pro").isFilm(true).build());

    assertThat(saved.getId()).isEqualTo(1L);
    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  void saveLens_populatesCreatedAt() {
    ContentLensEntity saved =
        equipmentRepository.saveLens(ContentLensEntity.builder().lensName("Sekor C 80mm").build());

    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  void saveFilmType_populatesCreatedAt() {
    ContentFilmTypeEntity saved =
        equipmentRepository.saveFilmType(
            ContentFilmTypeEntity.builder()
                .filmTypeName("PORTRA_400")
                .displayName("Portra 400")
                .defaultIso(400)
                .build());

    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  void saveTag_populatesCreatedAt() {
    TagEntity saved = tagRepository.save(new TagEntity("landscape"));

    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  void savePerson_populatesCreatedAt() {
    ContentPersonEntity saved =
        personRepository.save(ContentPersonEntity.builder().personName("Jane Doe").build());

    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  void saveLocation_populatesCreatedAt() {
    LocationEntity saved =
        locationRepository.save(
            LocationEntity.builder().locationName("Columbia River Gorge").build());

    assertThat(saved.getCreatedAt()).isNotNull();
  }
}
