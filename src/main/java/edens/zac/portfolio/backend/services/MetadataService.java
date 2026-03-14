package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.ContentCameraDao;
import edens.zac.portfolio.backend.dao.ContentFilmTypeDao;
import edens.zac.portfolio.backend.dao.ContentLensDao;
import edens.zac.portfolio.backend.dao.ContentPersonDao;
import edens.zac.portfolio.backend.dao.ContentTagDao;
import edens.zac.portfolio.backend.dao.LocationDao;
import edens.zac.portfolio.backend.entity.ContentCameraEntity;
import edens.zac.portfolio.backend.entity.ContentFilmTypeEntity;
import edens.zac.portfolio.backend.entity.ContentLensEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.entity.ContentTagEntity;
import edens.zac.portfolio.backend.entity.LocationEntity;
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

  private final ContentTagDao contentTagDao;
  private final ContentPersonDao contentPersonDao;
  private final ContentCameraDao contentCameraDao;
  private final ContentLensDao contentLensDao;
  private final ContentFilmTypeDao contentFilmTypeDao;
  private final LocationDao locationDao;
  private final MetadataValidator metadataValidator;

  // ========== Tag Operations ==========

  @Transactional(readOnly = true)
  public List<Records.Tag> getAllTags() {
    return contentTagDao.findAllByOrderByTagNameAsc().stream()
        .map(this::toTagModel)
        .collect(Collectors.toList());
  }

  @Transactional
  @CacheEvict(value = "generalMetadata", allEntries = true)
  public Map<String, Object> createTag(String tagName) {
    metadataValidator.validateTagName(tagName);
    tagName = tagName.trim();

    if (contentTagDao.existsByTagNameIgnoreCase(tagName)) {
      throw new DataIntegrityViolationException("Tag already exists: " + tagName);
    }

    ContentTagEntity tag = new ContentTagEntity(tagName);
    ContentTagEntity savedTag = contentTagDao.save(tag);

    return Map.of(
        "id", savedTag.getId(),
        "tagName", savedTag.getTagName(),
        "createdAt", savedTag.getCreatedAt());
  }

  // ========== Person Operations ==========

  @Transactional(readOnly = true)
  public List<Records.Person> getAllPeople() {
    return contentPersonDao.findAllByOrderByPersonNameAsc().stream()
        .map(this::toPersonModel)
        .collect(Collectors.toList());
  }

  @Transactional
  @CacheEvict(value = "generalMetadata", allEntries = true)
  public Map<String, Object> createPerson(String personName) {
    metadataValidator.validatePersonName(personName);
    personName = personName.trim();

    if (contentPersonDao.existsByPersonNameIgnoreCase(personName)) {
      throw new DataIntegrityViolationException("Person already exists: " + personName);
    }

    ContentPersonEntity person = new ContentPersonEntity(personName);
    ContentPersonEntity savedPerson = contentPersonDao.save(person);

    return Map.of(
        "id", savedPerson.getId(),
        "personName", savedPerson.getPersonName(),
        "createdAt", savedPerson.getCreatedAt());
  }

  // ========== Camera Operations ==========

  @Transactional(readOnly = true)
  public List<Records.Camera> getAllCameras() {
    return contentCameraDao.findAllByOrderByCameraNameAsc().stream()
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
          contentCameraDao.findByBodySerialNumber(bodySerialNumber);
      if (existing.isPresent()) {
        throw new DataIntegrityViolationException(
            "Camera with serial number already exists: " + bodySerialNumber);
      }
    }

    if (contentCameraDao.existsByCameraNameIgnoreCase(cameraName)) {
      throw new DataIntegrityViolationException("Camera already exists: " + cameraName);
    }

    ContentCameraEntity camera =
        ContentCameraEntity.builder()
            .cameraName(cameraName)
            .bodySerialNumber(bodySerialNumber != null ? bodySerialNumber.trim() : null)
            .build();
    ContentCameraEntity savedCamera = contentCameraDao.save(camera);

    return Map.of(
        "id", savedCamera.getId(),
        "cameraName", savedCamera.getCameraName(),
        "createdAt", savedCamera.getCreatedAt());
  }

  @Transactional(readOnly = true)
  public ContentCameraEntity findCameraById(Long id) {
    return contentCameraDao
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Camera not found: " + id));
  }

  // ========== Lens Operations ==========

  @Transactional(readOnly = true)
  public List<Records.Lens> getAllLenses() {
    return contentLensDao.findAllByOrderByLensNameAsc().stream()
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
          contentLensDao.findByLensSerialNumber(lensSerialNumber);
      if (existing.isPresent()) {
        throw new DataIntegrityViolationException(
            "Lens with serial number already exists: " + lensSerialNumber);
      }
    }

    if (contentLensDao.existsByLensNameIgnoreCase(lensName)) {
      throw new DataIntegrityViolationException("Lens already exists: " + lensName);
    }

    ContentLensEntity lens =
        ContentLensEntity.builder()
            .lensName(lensName)
            .lensSerialNumber(lensSerialNumber != null ? lensSerialNumber.trim() : null)
            .build();
    ContentLensEntity savedLens = contentLensDao.save(lens);

    return Map.of(
        "id", savedLens.getId(),
        "lensName", savedLens.getLensName(),
        "createdAt", savedLens.getCreatedAt());
  }

  @Transactional(readOnly = true)
  public ContentLensEntity findLensById(Long id) {
    return contentLensDao
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Lens not found: " + id));
  }

  // ========== Film Type Operations ==========

  @Transactional(readOnly = true)
  public List<ContentFilmTypeModel> getAllFilmTypes() {
    return contentFilmTypeDao.findAllByOrderByDisplayNameAsc().stream()
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

    if (contentFilmTypeDao.existsByFilmTypeNameIgnoreCase(filmTypeName)) {
      throw new DataIntegrityViolationException("Film type already exists: " + filmTypeName);
    }

    ContentFilmTypeEntity filmType =
        new ContentFilmTypeEntity(filmTypeName, displayName, defaultIso);
    filmType = contentFilmTypeDao.save(filmType);
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
        contentFilmTypeDao.findByFilmTypeNameIgnoreCase(technicalName);
    if (existing.isPresent()) {
      return existing.get();
    }
    ContentFilmTypeEntity newFilmType =
        new ContentFilmTypeEntity(technicalName, displayName, defaultIso);
    newFilmType = contentFilmTypeDao.save(newFilmType);
    if (tracking != null) {
      tracking.add(newFilmType);
    }
    log.info("Created new film type: {}", displayName);
    return newFilmType;
  }

  @Transactional(readOnly = true)
  public ContentFilmTypeEntity findFilmTypeById(Long id) {
    return contentFilmTypeDao
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Film type not found: " + id));
  }

  // ========== Location Operations ==========

  @Transactional(readOnly = true)
  public List<Records.Location> getAllLocations() {
    return locationDao.findAllByOrderByLocationNameAsc().stream()
        .map(this::toLocationModel)
        .collect(Collectors.toList());
  }

  @Transactional
  public LocationEntity findOrCreateLocation(String name) {
    return locationDao.findOrCreate(name);
  }

  @Transactional(readOnly = true)
  public LocationEntity findLocationById(Long id) {
    return locationDao
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Location not found with ID: " + id));
  }

  // ========== Private Converters ==========

  private Records.Tag toTagModel(ContentTagEntity entity) {
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
