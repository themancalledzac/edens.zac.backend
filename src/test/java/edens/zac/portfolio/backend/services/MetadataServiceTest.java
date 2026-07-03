package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.EquipmentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.ContentCameraEntity;
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
 * Unit coverage for {@link MetadataService#createCamera} — the camera upsert added in commit
 * 9d500f7. Pure Mockito (no Spring / DB) matching the other service unit tests in this package.
 */
@ExtendWith(MockitoExtension.class)
class MetadataServiceTest {

  @Mock private TagRepository tagRepository;
  @Mock private PersonRepository personRepository;
  @Mock private EquipmentRepository equipmentRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private MetadataValidator metadataValidator;

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
}
