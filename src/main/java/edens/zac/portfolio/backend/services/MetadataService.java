package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.EquipmentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.ContentCameraEntity;
import edens.zac.portfolio.backend.entity.ContentFilmTypeEntity;
import edens.zac.portfolio.backend.entity.ContentLensEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.entity.LocationEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.ContentFilmTypeModel;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.services.validator.MetadataValidator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing metadata entities: tags, people, cameras, lenses, film types, locations. */
@Service
@Slf4j
@RequiredArgsConstructor
public class MetadataService {

  private final TagRepository tagRepository;
  private final PersonRepository personRepository;
  private final EquipmentRepository equipmentRepository;
  private final LocationRepository locationRepository;
  private final MetadataValidator metadataValidator;

  // ========== Tag Operations ==========

  @Transactional(readOnly = true)
  public List<Records.Tag> getAllTags() {
    return tagRepository.findAllByOrderByTagNameAsc().stream()
        .map(this::toTagModel)
        .collect(Collectors.toList());
  }

  @Transactional
  @CacheEvict(value = "generalMetadata", allEntries = true)
  public Map<String, Object> createTag(String tagName) {
    metadataValidator.validateTagName(tagName);
    tagName = tagName.trim();

    if (tagRepository.existsByTagNameIgnoreCase(tagName)) {
      throw new DataIntegrityViolationException("Tag already exists: " + tagName);
    }

    TagEntity tag = new TagEntity(tagName);
    TagEntity savedTag = tagRepository.save(tag);

    return Map.of(
        "id", savedTag.getId(),
        "tagName", savedTag.getTagName(),
        "createdAt", savedTag.getCreatedAt());
  }

  // ========== Person Operations ==========

  @Transactional(readOnly = true)
  public List<Records.Person> getAllPeople() {
    return personRepository.findAllByOrderByPersonNameAsc().stream()
        .map(this::toPersonModel)
        .collect(Collectors.toList());
  }

  @Transactional
  @CacheEvict(value = "generalMetadata", allEntries = true)
  public Map<String, Object> createPerson(String personName) {
    metadataValidator.validatePersonName(personName);
    personName = personName.trim();

    if (personRepository.existsByPersonNameIgnoreCase(personName)) {
      throw new DataIntegrityViolationException("Person already exists: " + personName);
    }

    ContentPersonEntity person = new ContentPersonEntity(personName);
    ContentPersonEntity savedPerson = personRepository.save(person);

    return Map.of(
        "id", savedPerson.getId(),
        "personName", savedPerson.getPersonName(),
        "createdAt", savedPerson.getCreatedAt());
  }

  // ========== Camera Operations ==========

  @Transactional(readOnly = true)
  public List<Records.Camera> getAllCameras() {
    return equipmentRepository.findAllCamerasOrderByName().stream()
        .map(ContentProcessingUtil::cameraEntityToCameraModel)
        .collect(Collectors.toList());
  }

  @Transactional
  @CacheEvict(value = "generalMetadata", allEntries = true)
  public Map<String, Object> createCamera(String cameraName, String bodySerialNumber) {
    if (cameraName == null || cameraName.trim().isEmpty()) {
      throw new IllegalArgumentException("cameraName is required");
    }
    cameraName = cameraName.trim();

    if (bodySerialNumber != null && !bodySerialNumber.trim().isEmpty()) {
      Optional<ContentCameraEntity> existing =
          equipmentRepository.findCameraByBodySerialNumber(bodySerialNumber);
      if (existing.isPresent()) {
        throw new DataIntegrityViolationException(
            "Camera with serial number already exists: " + bodySerialNumber);
      }
    }

    if (equipmentRepository.existsByCameraNameIgnoreCase(cameraName)) {
      throw new DataIntegrityViolationException("Camera already exists: " + cameraName);
    }

    ContentCameraEntity camera =
        ContentCameraEntity.builder()
            .cameraName(cameraName)
            .bodySerialNumber(bodySerialNumber != null ? bodySerialNumber.trim() : null)
            .build();
    ContentCameraEntity savedCamera = equipmentRepository.saveCamera(camera);

    return Map.of(
        "id", savedCamera.getId(),
        "cameraName", savedCamera.getCameraName(),
        "createdAt", savedCamera.getCreatedAt());
  }

  @Transactional(readOnly = true)
  public ContentCameraEntity findCameraById(Long id) {
    return equipmentRepository
        .findCameraById(id)
        .orElseThrow(() -> new IllegalArgumentException("Camera not found: " + id));
  }

  // ========== Lens Operations ==========

  @Transactional(readOnly = true)
  public List<Records.Lens> getAllLenses() {
    return equipmentRepository.findAllLensesOrderByName().stream()
        .map(ContentProcessingUtil::lensEntityToLensModel)
        .collect(Collectors.toList());
  }

  @Transactional
  @CacheEvict(value = "generalMetadata", allEntries = true)
  public Map<String, Object> createLens(String lensName, String lensSerialNumber) {
    if (lensName == null || lensName.trim().isEmpty()) {
      throw new IllegalArgumentException("lensName is required");
    }
    lensName = lensName.trim();

    if (lensSerialNumber != null && !lensSerialNumber.trim().isEmpty()) {
      Optional<ContentLensEntity> existing =
          equipmentRepository.findLensBySerialNumber(lensSerialNumber);
      if (existing.isPresent()) {
        throw new DataIntegrityViolationException(
            "Lens with serial number already exists: " + lensSerialNumber);
      }
    }

    if (equipmentRepository.existsByLensNameIgnoreCase(lensName)) {
      throw new DataIntegrityViolationException("Lens already exists: " + lensName);
    }

    ContentLensEntity lens =
        ContentLensEntity.builder()
            .lensName(lensName)
            .lensSerialNumber(lensSerialNumber != null ? lensSerialNumber.trim() : null)
            .build();
    ContentLensEntity savedLens = equipmentRepository.saveLens(lens);

    return Map.of(
        "id", savedLens.getId(),
        "lensName", savedLens.getLensName(),
        "createdAt", savedLens.getCreatedAt());
  }

  @Transactional(readOnly = true)
  public ContentLensEntity findLensById(Long id) {
    return equipmentRepository
        .findLensById(id)
        .orElseThrow(() -> new IllegalArgumentException("Lens not found: " + id));
  }

  // ========== Film Type Operations ==========

  @Transactional(readOnly = true)
  public List<ContentFilmTypeModel> getAllFilmTypes() {
    return equipmentRepository.findAllFilmTypesOrderByDisplayName().stream()
        .map(this::toFilmTypeModel)
        .collect(Collectors.toList());
  }

  @Transactional
  @CacheEvict(value = "generalMetadata", allEntries = true)
  public Map<String, Object> createFilmType(
      String filmTypeName, String displayName, Integer defaultIso) {
    metadataValidator.validateFilmType(filmTypeName, displayName, defaultIso);
    filmTypeName = filmTypeName.trim();
    displayName = displayName.trim();

    if (equipmentRepository.existsByFilmTypeNameIgnoreCase(filmTypeName)) {
      throw new DataIntegrityViolationException("Film type already exists: " + filmTypeName);
    }

    ContentFilmTypeEntity filmType =
        new ContentFilmTypeEntity(filmTypeName, displayName, defaultIso);
    filmType = equipmentRepository.saveFilmType(filmType);
    log.info("Created film type: {} (ID: {})", filmType.getDisplayName(), filmType.getId());

    return Map.of(
        "success",
        true,
        "message",
        "Film type created successfully",
        "filmType",
        toFilmTypeModel(filmType));
  }

  /**
   * Find or create a film type by display name. Creates the technical name from the display name.
   * Adds newly created entities to the tracking set if provided.
   */
  @Transactional
  public ContentFilmTypeEntity findOrCreateFilmType(
      String displayName, Integer defaultIso, Set<ContentFilmTypeEntity> tracking) {
    String technicalName = displayName.toUpperCase().replaceAll("\\s+", "_");
    Optional<ContentFilmTypeEntity> existing =
        equipmentRepository.findFilmTypeByNameIgnoreCase(technicalName);
    if (existing.isPresent()) {
      return existing.get();
    }
    ContentFilmTypeEntity newFilmType =
        new ContentFilmTypeEntity(technicalName, displayName, defaultIso);
    newFilmType = equipmentRepository.saveFilmType(newFilmType);
    if (tracking != null) {
      tracking.add(newFilmType);
    }
    log.info("Created new film type: {}", displayName);
    return newFilmType;
  }

  @Transactional(readOnly = true)
  public ContentFilmTypeEntity findFilmTypeById(Long id) {
    return equipmentRepository
        .findFilmTypeById(id)
        .orElseThrow(() -> new IllegalArgumentException("Film type not found: " + id));
  }

  // ========== Location Operations ==========

  @Transactional(readOnly = true)
  public List<Records.Location> getAllLocations() {
    return locationRepository.findAllByOrderByLocationNameAsc().stream()
        .map(this::toLocationModel)
        .collect(Collectors.toList());
  }

  @Transactional
  public LocationEntity findOrCreateLocation(String name) {
    return locationRepository.findOrCreate(name);
  }

  @Transactional(readOnly = true)
  public LocationEntity findLocationById(Long id) {
    return locationRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Location not found with ID: " + id));
  }

  // ========== Private Converters ==========

  private Records.Tag toTagModel(TagEntity entity) {
    return new Records.Tag(entity.getId(), entity.getTagName());
  }

  private Records.Person toPersonModel(ContentPersonEntity entity) {
    return new Records.Person(entity.getId(), entity.getPersonName());
  }

  private Records.Location toLocationModel(LocationEntity entity) {
    return new Records.Location(entity.getId(), entity.getLocationName());
  }

  ContentFilmTypeModel toFilmTypeModel(ContentFilmTypeEntity entity) {
    return new ContentFilmTypeModel(
        entity.getId(),
        entity.getFilmTypeName(),
        entity.getDisplayName(),
        entity.getDefaultIso(),
        List.of());
  }
}
