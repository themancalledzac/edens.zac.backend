package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.*;
import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.repository.ContentRepository;
import edens.zac.portfolio.backend.repository.ContentCameraRepository;
import edens.zac.portfolio.backend.repository.ContentFilmTypeRepository;
import edens.zac.portfolio.backend.repository.ContentLensRepository;
import edens.zac.portfolio.backend.repository.ContentPersonRepository;
import edens.zac.portfolio.backend.repository.ContentTagRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of ContentBlockService that provides methods for
 * managing content blocks, tags, and people.
 */
@Service
@Slf4j
@RequiredArgsConstructor
class ContentServiceImpl implements ContentService {

    private final ContentTagRepository contentTagRepository;
    private final ContentPersonRepository contentPersonRepository;
    private final ContentCameraRepository contentCameraRepository;
    private final ContentLensRepository contentLensRepository;
    private final ContentFilmTypeRepository contentFilmTypeRepository;
    private final ContentRepository contentRepository;
    private final ContentProcessingUtil contentProcessingUtil;

    @Override
    @Transactional
    public Map<String, Object> createTag(String tagName) {
        if (tagName == null || tagName.trim().isEmpty()) {
            throw new IllegalArgumentException("tagName is required");
        }

        tagName = tagName.trim();

        // Check if tag already exists (case-insensitive)
        if (contentTagRepository.existsByTagNameIgnoreCase(tagName)) {
            throw new DataIntegrityViolationException("Tag already exists: " + tagName);
        }

        ContentTagEntity tag = new ContentTagEntity(tagName);
        ContentTagEntity savedTag = contentTagRepository.save(tag);

        return Map.of(
                "id", savedTag.getId(),
                "tagName", savedTag.getTagName(),
                "createdAt", savedTag.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public Map<String, Object> createPerson(String personName) {
        if (personName == null || personName.trim().isEmpty()) {
            throw new IllegalArgumentException("personName is required");
        }

        personName = personName.trim();

        // Check if person already exists (case-insensitive)
        if (contentPersonRepository.existsByPersonNameIgnoreCase(personName)) {
            throw new DataIntegrityViolationException("Person already exists: " + personName);
        }

        ContentPersonEntity person = new ContentPersonEntity(personName);
        ContentPersonEntity savedPerson = contentPersonRepository.save(person);

        return Map.of(
                "id", savedPerson.getId(),
                "personName", savedPerson.getPersonName(),
                "createdAt", savedPerson.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public Map<String, Object> createCamera(String cameraName) {
        if (cameraName == null || cameraName.trim().isEmpty()) {
            throw new IllegalArgumentException("cameraName is required");
        }

        cameraName = cameraName.trim();

        // Check if camera already exists (case-insensitive)
        if (contentCameraRepository.existsByCameraNameIgnoreCase(cameraName)) {
            throw new DataIntegrityViolationException("Camera already exists: " + cameraName);
        }

        ContentCameraEntity camera = new ContentCameraEntity(cameraName);
        ContentCameraEntity savedCamera = contentCameraRepository.save(camera);

        return Map.of(
                "id", savedCamera.getId(),
                "cameraName", savedCamera.getCameraName(),
                "createdAt", savedCamera.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public Map<String, Object> updateImages(List<ImageUpdateRequest> updates) {
        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("At least one image update is required");
        }

        // Track updated images and newly created metadata
        List<ImageContentModel> updatedImages = new ArrayList<>();
        Set<ContentTagEntity> newlyCreatedTags = new HashSet<>();
        Set<ContentPersonEntity> newlyCreatedPeople = new HashSet<>();
        Set<ContentCameraEntity> newlyCreatedCameras = new HashSet<>();
        Set<ContentLensEntity> newlyCreatedLenses = new HashSet<>();
        Set<ContentFilmTypeEntity> newlyCreatedFilmTypes = new HashSet<>();
        List<String> errors = new ArrayList<>();

        for (ImageUpdateRequest update : updates) {
            try {
                Long imageId = update.getId();
                if (imageId == null) {
                    errors.add("Missing image ID in update request");
                    continue;
                }

                ContentImageEntity image = (ContentImageEntity) contentRepository
                        .findById(imageId)
                        .orElseThrow(() -> new EntityNotFoundException("Image not found: " + imageId));

                // Apply basic image metadata updates using the processing util
                // Note: This handles camera, lens, and filmType updates via the util
                // We'll need to track which ones were created
                applyImageUpdatesWithTracking(image, update, newlyCreatedCameras, newlyCreatedLenses, newlyCreatedFilmTypes);

                // Update tags using prev/new/remove pattern (with tracking)
                if (update.getTags() != null) {
                    updateImageTags(image, update.getTags(), newlyCreatedTags);
                }

                // Update people using prev/new/remove pattern (with tracking)
                if (update.getPeople() != null) {
                    updateImagePeople(image, update.getPeople(), newlyCreatedPeople);
                }

                // Handle collection updates using prev/new/remove pattern
                if (update.getCollections() != null) {
                    ImageUpdateRequest.CollectionUpdate collectionUpdate = update.getCollections();
                    if (collectionUpdate.getPrev() != null && !collectionUpdate.getPrev().isEmpty()) {
                        contentProcessingUtil.handleCollectionVisibilityUpdates(image, collectionUpdate.getPrev());
                    }
                }

                // Save the updated image
                ContentImageEntity savedImage = contentRepository.save(image);

                // Convert to model and add to results
                ImageContentModel imageModel = (ImageContentModel) contentProcessingUtil.convertToModel(savedImage);
                updatedImages.add(imageModel);

            } catch (EntityNotFoundException e) {
                errors.add(e.getMessage());
                log.warn("Entity not found during update: {}", e.getMessage());
            } catch (ClassCastException e) {
                errors.add("Block is not an image: " + update.getId());
                log.warn("Attempted to update non-image block as image: {}", update.getId());
            } catch (Exception e) {
                errors.add("Error updating image " + update.getId() + ": " + e.getMessage());
                log.error("Error updating image {}: {}", update.getId(), e.getMessage(), e);
            }
        }

        // Build the response with updated images and new metadata
        ImageUpdateResponse.NewMetadata newMetadata = ImageUpdateResponse.NewMetadata.builder()
                .tags(newlyCreatedTags.isEmpty() ? null : newlyCreatedTags.stream().map(this::toTagModel).collect(Collectors.toList()))
                .people(newlyCreatedPeople.isEmpty() ? null : newlyCreatedPeople.stream().map(this::toPersonModel).collect(Collectors.toList()))
                .cameras(newlyCreatedCameras.isEmpty() ? null : newlyCreatedCameras.stream().map(ContentProcessingUtil::cameraEntityToCameraModel).collect(Collectors.toList()))
                .lenses(newlyCreatedLenses.isEmpty() ? null : newlyCreatedLenses.stream().map(ContentProcessingUtil::lensEntityToLensModel).collect(Collectors.toList()))
                .filmTypes(newlyCreatedFilmTypes.isEmpty() ? null : newlyCreatedFilmTypes.stream().map(this::toFilmTypeModel).collect(Collectors.toList()))
                .build();

        ImageUpdateResponse response = ImageUpdateResponse.builder()
                .updatedImages(updatedImages)
                .newMetadata(newMetadata)
                .errors(errors.isEmpty() ? null : errors)
                .build();

        // Return as Map for backward compatibility with interface
        return Map.of(
                "updatedImages", response.getUpdatedImages(),
                "newMetadata", response.getNewMetadata(),
                "errors", response.getErrors() != null ? response.getErrors() : List.of()
        );
    }

    /**
     * Apply image metadata updates and track newly created entities (cameras, lenses, film types).
     */
    private void applyImageUpdatesWithTracking(
            ContentImageEntity image,
            ImageUpdateRequest updateRequest,
            Set<ContentCameraEntity> newCameras,
            Set<ContentLensEntity> newLenses,
            Set<ContentFilmTypeEntity> newFilmTypes) {

        // Update basic metadata fields
        if (updateRequest.getTitle() != null) image.setTitle(updateRequest.getTitle());
        if (updateRequest.getRating() != null) image.setRating(updateRequest.getRating());
        if (updateRequest.getLocation() != null) image.setLocation(updateRequest.getLocation());
        if (updateRequest.getAuthor() != null) image.setAuthor(updateRequest.getAuthor());
        if (updateRequest.getIsFilm() != null) image.setIsFilm(updateRequest.getIsFilm());
        if (updateRequest.getFilmFormat() != null) image.setFilmFormat(updateRequest.getFilmFormat());
        if (updateRequest.getBlackAndWhite() != null) image.setBlackAndWhite(updateRequest.getBlackAndWhite());
        if (updateRequest.getFocalLength() != null) image.setFocalLength(updateRequest.getFocalLength());
        if (updateRequest.getFStop() != null) image.setFStop(updateRequest.getFStop());
        if (updateRequest.getShutterSpeed() != null) image.setShutterSpeed(updateRequest.getShutterSpeed());
        if (updateRequest.getIso() != null) image.setIso(updateRequest.getIso());
        if (updateRequest.getCreateDate() != null) image.setCreateDate(updateRequest.getCreateDate());

        // Handle camera update with tracking
        if (updateRequest.getCamera() != null) {
            ImageUpdateRequest.CameraUpdate cameraUpdate = updateRequest.getCamera();
            if (Boolean.TRUE.equals(cameraUpdate.getRemove())) {
                image.setCamera(null);
            } else if (cameraUpdate.getNewValue() != null && !cameraUpdate.getNewValue().trim().isEmpty()) {
                String cameraName = cameraUpdate.getNewValue().trim();
                var existing = contentCameraRepository.findByCameraNameIgnoreCase(cameraName);
                if (existing.isPresent()) {
                    image.setCamera(existing.get());
                } else {
                    ContentCameraEntity newCamera = new ContentCameraEntity(cameraName);
                    newCamera = contentCameraRepository.save(newCamera);
                    image.setCamera(newCamera);
                    newCameras.add(newCamera);
                    log.info("Created new camera: {}", cameraName);
                }
            } else if (cameraUpdate.getPrev() != null) {
                ContentCameraEntity camera = contentCameraRepository.findById(cameraUpdate.getPrev())
                        .orElseThrow(() -> new IllegalArgumentException("Camera not found: " + cameraUpdate.getPrev()));
                image.setCamera(camera);
            }
        }

        // Handle lens update with tracking
        if (updateRequest.getLens() != null) {
            ImageUpdateRequest.LensUpdate lensUpdate = updateRequest.getLens();
            if (Boolean.TRUE.equals(lensUpdate.getRemove())) {
                image.setLens(null);
            } else if (lensUpdate.getNewValue() != null && !lensUpdate.getNewValue().trim().isEmpty()) {
                String lensName = lensUpdate.getNewValue().trim();
                var existing = contentLensRepository.findByLensNameIgnoreCase(lensName);
                if (existing.isPresent()) {
                    image.setLens(existing.get());
                } else {
                    ContentLensEntity newLens = new ContentLensEntity(lensName);
                    newLens = contentLensRepository.save(newLens);
                    image.setLens(newLens);
                    newLenses.add(newLens);
                    log.info("Created new lens: {}", lensName);
                }
            } else if (lensUpdate.getPrev() != null) {
                ContentLensEntity lens = contentLensRepository.findById(lensUpdate.getPrev())
                        .orElseThrow(() -> new IllegalArgumentException("Lens not found: " + lensUpdate.getPrev()));
                image.setLens(lens);
            }
        }

        // Handle film type update with tracking
        if (updateRequest.getFilmType() != null) {
            ImageUpdateRequest.FilmTypeUpdate filmTypeUpdate = updateRequest.getFilmType();
            if (Boolean.TRUE.equals(filmTypeUpdate.getRemove())) {
                image.setFilmType(null);
            } else if (filmTypeUpdate.getNewValue() != null) {
                NewFilmTypeRequest newFilmTypeRequest = filmTypeUpdate.getNewValue();
                String displayName = newFilmTypeRequest.getFilmTypeName().trim();
                String technicalName = displayName.toUpperCase().replaceAll("\\s+", "_");

                var existing = contentFilmTypeRepository.findByFilmTypeNameIgnoreCase(technicalName);
                if (existing.isPresent()) {
                    image.setFilmType(existing.get());
                } else {
                    ContentFilmTypeEntity newFilmType = new ContentFilmTypeEntity(
                            technicalName, displayName, newFilmTypeRequest.getDefaultIso());
                    newFilmType = contentFilmTypeRepository.save(newFilmType);
                    image.setFilmType(newFilmType);
                    newFilmTypes.add(newFilmType);
                    log.info("Created new film type: {}", displayName);
                }
            } else if (filmTypeUpdate.getPrev() != null) {
                ContentFilmTypeEntity filmType = contentFilmTypeRepository.findById(filmTypeUpdate.getPrev())
                        .orElseThrow(() -> new IllegalArgumentException("Film type not found: " + filmTypeUpdate.getPrev()));
                image.setFilmType(filmType);
            }
        }
    }

    /**
     * Update image tags and track newly created ones.
     */
    private void updateImageTags(ContentImageEntity image, ImageUpdateRequest.TagUpdate tagUpdate, Set<ContentTagEntity> newTags) {
        Set<ContentTagEntity> tags = new HashSet<>(image.getTags());

        // Remove tags if specified
        if (tagUpdate.getRemove() != null && !tagUpdate.getRemove().isEmpty()) {
            tags.removeIf(tag -> tagUpdate.getRemove().contains(tag.getId()));
        }

        // Add existing tags by ID (prev)
        if (tagUpdate.getPrev() != null && !tagUpdate.getPrev().isEmpty()) {
            Set<ContentTagEntity> existingTags = tagUpdate.getPrev().stream()
                    .map(tagId -> contentTagRepository.findById(tagId)
                            .orElseThrow(() -> new EntityNotFoundException("Tag not found: " + tagId)))
                    .collect(Collectors.toSet());
            tags.addAll(existingTags);
        }

        // Create and add new tags by name (newValue) with tracking
        if (tagUpdate.getNewValue() != null && !tagUpdate.getNewValue().isEmpty()) {
            for (String tagName : tagUpdate.getNewValue()) {
                if (tagName != null && !tagName.trim().isEmpty()) {
                    String trimmedName = tagName.trim();
                    var existing = contentTagRepository.findByTagNameIgnoreCase(trimmedName);
                    if (existing.isPresent()) {
                        tags.add(existing.get());
                    } else {
                        ContentTagEntity newTag = new ContentTagEntity(trimmedName);
                        newTag = contentTagRepository.save(newTag);
                        tags.add(newTag);
                        newTags.add(newTag);
                        log.info("Created new tag: {}", trimmedName);
                    }
                }
            }
        }

        image.setTags(tags);
    }

    /**
     * Update image people and track newly created ones.
     */
    private void updateImagePeople(ContentImageEntity image, ImageUpdateRequest.PersonUpdate personUpdate, Set<ContentPersonEntity> newPeople) {
        Set<ContentPersonEntity> people = new HashSet<>(image.getPeople());

        // Remove people if specified
        if (personUpdate.getRemove() != null && !personUpdate.getRemove().isEmpty()) {
            people.removeIf(person -> personUpdate.getRemove().contains(person.getId()));
        }

        // Add existing people by ID (prev)
        if (personUpdate.getPrev() != null && !personUpdate.getPrev().isEmpty()) {
            Set<ContentPersonEntity> existingPeople = personUpdate.getPrev().stream()
                    .map(personId -> contentPersonRepository.findById(personId)
                            .orElseThrow(() -> new EntityNotFoundException("Person not found: " + personId)))
                    .collect(Collectors.toSet());
            people.addAll(existingPeople);
        }

        // Create and add new people by name (newValue) with tracking
        if (personUpdate.getNewValue() != null && !personUpdate.getNewValue().isEmpty()) {
            for (String personName : personUpdate.getNewValue()) {
                if (personName != null && !personName.trim().isEmpty()) {
                    String trimmedName = personName.trim();
                    var existing = contentPersonRepository.findByPersonNameIgnoreCase(trimmedName);
                    if (existing.isPresent()) {
                        people.add(existing.get());
                    } else {
                        ContentPersonEntity newPerson = new ContentPersonEntity(trimmedName);
                        newPerson = contentPersonRepository.save(newPerson);
                        people.add(newPerson);
                        newPeople.add(newPerson);
                        log.info("Created new person: {}", trimmedName);
                    }
                }
            }
        }

        image.setPeople(people);
    }

    @Override
    @Transactional
    public Map<String, Object> deleteImages(List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            throw new IllegalArgumentException("At least one image ID is required");
        }

        List<Long> deletedIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Long imageId : imageIds) {
            try {
                if (!contentRepository.existsById(imageId)) {
                    errors.add("Image not found: " + imageId);
                    continue;
                }

                contentRepository.deleteById(imageId);
                deletedIds.add(imageId);

            } catch (Exception e) {
                errors.add("Failed to delete image " + imageId + ": " + e.getMessage());
                log.error("Error deleting image {}: {}", imageId, e.getMessage());
            }
        }

        return Map.of(
                "deletedIds", deletedIds,
                "deletedCount", deletedIds.size(),
                "errors", errors
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentTagModel> getAllTags() {
        return contentTagRepository.findAllByOrderByTagNameAsc().stream()
                .map(this::toTagModel)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentPersonModel> getAllPeople() {
        return contentPersonRepository.findAllByOrderByPersonNameAsc().stream()
                .map(this::toPersonModel)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentCameraModel> getAllCameras() {
        return contentCameraRepository.findAllByOrderByCameraNameAsc().stream()
                .map(ContentProcessingUtil::cameraEntityToCameraModel)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentFilmTypeModel> getAllFilmTypes() {
        return contentFilmTypeRepository.findAllByOrderByDisplayNameAsc().stream()
                .map(this::toFilmTypeModel)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentLensModel> getAllLenses() {
        return contentLensRepository.findAllByOrderByLensNameAsc().stream()
                .map(ContentProcessingUtil::lensEntityToLensModel)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Map<String, Object> createFilmType(String filmTypeName, String displayName, Integer defaultIso) {
        if (filmTypeName == null || filmTypeName.trim().isEmpty()) {
            throw new IllegalArgumentException("filmTypeName is required");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("displayName is required");
        }
        if (defaultIso == null || defaultIso <= 0) {
            throw new IllegalArgumentException("defaultIso must be a positive integer");
        }

        filmTypeName = filmTypeName.trim();
        displayName = displayName.trim();

        // Check if film type already exists
        if (contentFilmTypeRepository.existsByFilmTypeNameIgnoreCase(filmTypeName)) {
            throw new DataIntegrityViolationException("Film type already exists: " + filmTypeName);
        }

        // Create and save
        ContentFilmTypeEntity filmType = new ContentFilmTypeEntity(filmTypeName, displayName, defaultIso);
        filmType = contentFilmTypeRepository.save(filmType);
        log.info("Created film type: {} (ID: {})", filmType.getDisplayName(), filmType.getId());

        // Return result
        return Map.of(
                "success", true,
                "message", "Film type created successfully",
                "filmType", toFilmTypeModel(filmType)
        );
    }

    // ========== Helper Methods: Entity to Model Conversion ==========

    /**
     * Convert ContentTagEntity to ContentTagModel
     */
    private ContentTagModel toTagModel(ContentTagEntity entity) {
        return ContentTagModel.builder()
                .id(entity.getId())
                .name(entity.getTagName())
                .build();
    }

    /**
     * Convert ContentPersonEntity to ContentPersonModel
     */
    private ContentPersonModel toPersonModel(ContentPersonEntity entity) {
        return ContentPersonModel.builder()
                .id(entity.getId())
                .name(entity.getPersonName())
                .build();
    }


    /**
     * Convert ContentFilmTypeEntity to ContentFilmTypeModel
     */
    private ContentFilmTypeModel toFilmTypeModel(ContentFilmTypeEntity entity) {
        return ContentFilmTypeModel.builder()
                .id(entity.getId())
                .filmTypeName(entity.getFilmTypeName())
                .name(entity.getDisplayName())
                .defaultIso(entity.getDefaultIso())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ImageContentModel> getAllImages() {
        return contentRepository.findAllImagesOrderByCreateDateDesc().stream()
                .map(entity -> (ImageContentModel)
                        contentProcessingUtil.convertToModel(entity))
                .collect(Collectors.toList());
    }
}
