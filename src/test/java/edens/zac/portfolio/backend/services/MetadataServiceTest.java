package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.EquipmentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.ContentCameraEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.services.validator.MetadataValidator;
import edens.zac.portfolio.backend.types.FilmFormat;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit coverage for {@link MetadataService#createCamera} and {@link MetadataService#deletePerson}.
 * Pure Mockito (no Spring / DB) matching the other service unit tests in this package.
 */
@ExtendWith(MockitoExtension.class)
class MetadataServiceTest {

  @Mock private TagRepository tagRepository;
  @Mock private PersonRepository personRepository;
  @Mock private EquipmentRepository equipmentRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private MetadataValidator metadataValidator;
  @Mock private ReadCacheInvalidator readCacheInvalidator;

  @InjectMocks private MetadataService metadataService;

  private ContentCameraEntity camera(
      Long id, String name, Boolean isFilm, FilmFormat format, String serial) {
    return ContentCameraEntity.builder()
        .id(id)
        .cameraName(name)
        .isFilm(isFilm)
        .defaultFilmFormat(format)
        .bodySerialNumber(serial)
        .createdAt(LocalDateTime.now())
        .build();
  }

  @Test
  void createCamera_updatesExisting_whenNameAlreadyPresent() {
    ContentCameraEntity existing = camera(42L, "Leica M6", false, null, null);
    when(equipmentRepository.findCameraByNameIgnoreCase("Leica M6"))
        .thenReturn(Optional.of(existing));

    Map<String, Object> result =
        metadataService.createCamera("Leica M6", null, true, FilmFormat.MM_35);

    // Applies the requested film metadata to the existing camera rather than inserting a new one.
    verify(equipmentRepository).updateCameraFilmMetadata(42L, true, FilmFormat.MM_35);
    verify(equipmentRepository, never()).saveCamera(any());
    assertThat(result.get("id")).isEqualTo(42L);
    assertThat(result.get("cameraName")).isEqualTo("Leica M6");
    assertThat(result.get("isFilm")).isEqualTo(true);
  }

  @Test
  void createCamera_createsNew_whenNameNotPresent() {
    when(equipmentRepository.findCameraByNameIgnoreCase("Nikon Z6")).thenReturn(Optional.empty());
    when(equipmentRepository.saveCamera(any()))
        .thenReturn(camera(7L, "Nikon Z6", false, null, "SN-123"));

    Map<String, Object> result = metadataService.createCamera("Nikon Z6", "SN-123", false, null);

    ArgumentCaptor<ContentCameraEntity> saved = ArgumentCaptor.forClass(ContentCameraEntity.class);
    verify(equipmentRepository).saveCamera(saved.capture());
    assertThat(saved.getValue().getCameraName()).isEqualTo("Nikon Z6");
    assertThat(saved.getValue().getBodySerialNumber()).isEqualTo("SN-123");
    verify(equipmentRepository, never()).updateCameraFilmMetadata(any(), any(), any());
    assertThat(result.get("id")).isEqualTo(7L);
  }

  @Test
  void createCamera_rejectsSerialNumberConflict() {
    when(equipmentRepository.findCameraByBodySerialNumber("DUP-SERIAL"))
        .thenReturn(Optional.of(camera(99L, "Other", false, null, "DUP-SERIAL")));

    assertThatThrownBy(() -> metadataService.createCamera("Any Camera", "DUP-SERIAL", false, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("DUP-SERIAL");

    verify(equipmentRepository, never()).saveCamera(any());
    verify(equipmentRepository, never()).updateCameraFilmMetadata(eq(99L), any(), any());
  }

  @Test
  void createCamera_rejectsBlankName() {
    assertThatThrownBy(() -> metadataService.createCamera("   ", null, false, null))
        .isInstanceOf(IllegalArgumentException.class);

    verify(equipmentRepository, never()).saveCamera(any());
  }

  private ContentPersonEntity person(Long id, String name) {
    return ContentPersonEntity.builder().id(id).personName(name).build();
  }

  @Test
  void deletePerson_deletesThroughTheStatusGuardedPrimitive() {
    when(personRepository.findById(5L)).thenReturn(Optional.of(person(5L, "Ansel")));
    when(personRepository.deletePersonById(5L)).thenReturn(1);

    metadataService.deletePerson(5L);

    verify(personRepository).deleteAllAssociationsByPersonId(5L);
    verify(personRepository).deletePersonById(5L);
  }

  @Test
  void deletePerson_refusesAnAccountId() {
    // Bug #1 regression. Since V35 merged people into users, findById matches account rows too, so
    // it cannot tell a person tag from an account. The guarded delete is what stops an admin
    // delete-person call from destroying a real account: it matches 0 rows for an account id, and
    // deletePerson must turn that into a 404 rather than reporting success.
    when(personRepository.findById(5L)).thenReturn(Optional.of(person(5L, "Real Account")));
    when(personRepository.deletePersonById(5L)).thenReturn(0);

    assertThatThrownBy(() -> metadataService.deletePerson(5L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("5");
  }

  @Test
  void deletePerson_404sForAnUnknownId() {
    when(personRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> metadataService.deletePerson(404L))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(personRepository, never()).deleteAllAssociationsByPersonId(any());
    verify(personRepository, never()).deletePersonById(any());
  }
}
