package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.*;
import edens.zac.portfolio.backend.model.ContentCameraModel;
import edens.zac.portfolio.backend.model.ContentFilmTypeModel;
import edens.zac.portfolio.backend.model.ContentPersonModel;
import edens.zac.portfolio.backend.model.ContentTagModel;
import edens.zac.portfolio.backend.model.ImageUpdateRequest;
import edens.zac.portfolio.backend.repository.ContentBlockRepository;
import edens.zac.portfolio.backend.repository.ContentCameraRepository;
import edens.zac.portfolio.backend.repository.ContentFilmTypeRepository;
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
class ContentBlockServiceImpl implements ContentBlockService {

    private final ContentTagRepository contentTagRepository;
    private final ContentPersonRepository contentPersonRepository;
    private final ContentCameraRepository contentCameraRepository;
    private final ContentFilmTypeRepository contentFilmTypeRepository;
    private final ContentBlockRepository contentBlockRepository;
    private final ContentBlockProcessingUtil contentBlockProcessingUtil;

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

        List<Long> updatedIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (ImageUpdateRequest update : updates) {
            try {
                Long imageId = update.getId();
                if (imageId == null) {
                    errors.add("Missing image ID in update request");
                    continue;
                }

                ImageContentBlockEntity image = (ImageContentBlockEntity) contentBlockRepository
                        .findById(imageId)
                        .orElseThrow(() -> new EntityNotFoundException("Image not found: " + imageId));

                // Apply basic image metadata updates using the processing util
                // This follows the same pattern as ContentCollectionProcessingUtil.applyBasicUpdates
                contentBlockProcessingUtil.applyImageUpdates(image, update);

                // Update tags if provided
                // Start with the image's existing tags to preserve them
                Set<ContentTagEntity> tags = new HashSet<>(image.getTags());

                // If tagIds is explicitly provided (even if empty), it means we want to replace the tags
                // Otherwise, we're adding to the existing tags
                if (update.getTagIds() != null) {
                    // Replace with the specified tags
                    tags.clear();
                    Set<ContentTagEntity> existingTags = update.getTagIds().stream()
                            .map(tagId -> contentTagRepository.findById(tagId)
                                    .orElseThrow(() -> new EntityNotFoundException("Tag not found: " + tagId)))
                            .collect(Collectors.toSet());
                    tags.addAll(existingTags);
                }

                // Create or find new tags by name and ADD them to the collection
                if (update.getNewTags() != null) {
                    Set<ContentTagEntity> newTags = update.getNewTags().stream()
                            .filter(tagName -> tagName != null && !tagName.trim().isEmpty())
                            .map(tagName -> {
                                String trimmedName = tagName.trim();
                                return contentTagRepository.findByTagNameIgnoreCase(trimmedName)
                                        .orElseGet(() -> {
                                            log.info("Creating new tag: {}", trimmedName);
                                            ContentTagEntity newTag = new ContentTagEntity(trimmedName);
                                            return contentTagRepository.save(newTag);
                                        });
                            })
                            .collect(Collectors.toSet());
                    tags.addAll(newTags);
                }

                // Update the image's tags with the modified collection
                if (update.getTagIds() != null || update.getNewTags() != null) {
                    image.setTags(tags);
                }

                // Update people if provided
                // Start with the image's existing people to preserve them
                Set<ContentPersonEntity> people = new HashSet<>(image.getPeople());

                // If personIds is explicitly provided (even if empty), it means we want to replace the people
                // Otherwise, we're adding to the existing people
                if (update.getPersonIds() != null) {
                    // Replace with the specified people
                    people.clear();
                    Set<ContentPersonEntity> existingPeople = update.getPersonIds().stream()
                            .map(personId -> contentPersonRepository.findById(personId)
                                    .orElseThrow(() -> new EntityNotFoundException("Person not found: " + personId)))
                            .collect(Collectors.toSet());
                    people.addAll(existingPeople);
                }

                // Create or find new people by name and ADD them to the collection
                if (update.getNewPeople() != null) {
                    Set<ContentPersonEntity> newPeople = update.getNewPeople().stream()
                            .filter(personName -> personName != null && !personName.trim().isEmpty())
                            .map(personName -> {
                                String trimmedName = personName.trim();
                                return contentPersonRepository.findByPersonNameIgnoreCase(trimmedName)
                                        .orElseGet(() -> {
                                            log.info("Creating new person: {}", trimmedName);
                                            ContentPersonEntity newPerson = new ContentPersonEntity(trimmedName);
                                            return contentPersonRepository.save(newPerson);
                                        });
                            })
                            .collect(Collectors.toSet());
                    people.addAll(newPeople);
                }

                // Update the image's people with the modified collection
                if (update.getPersonIds() != null || update.getNewPeople() != null) {
                    image.setPeople(people);
                }

                // Handle collection visibility updates if provided
                if (update.getCollections() != null && !update.getCollections().isEmpty()) {
                    contentBlockProcessingUtil.handleCollectionVisibilityUpdates(image, update.getCollections());
                }

                contentBlockRepository.save(image);
                updatedIds.add(imageId);

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

        return Map.of(
                "updatedIds", updatedIds,
                "updatedCount", updatedIds.size(),
                "errors", errors
        );
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
                if (!contentBlockRepository.existsById(imageId)) {
                    errors.add("Image not found: " + imageId);
                    continue;
                }

                contentBlockRepository.deleteById(imageId);
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
                .map(ContentBlockProcessingUtil::cameraEntityToCameraModel)
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
     * Convert ContentTagEntity to ContentTagModel with associated content IDs
     */
    private ContentTagModel toTagModel(ContentTagEntity entity) {
        // Collect associated content collection IDs
        List<Long> contentCollectionIds = entity.getContentCollections().stream()
                .map(ContentCollectionEntity::getId)
                .sorted()
                .toList();

        // Collect associated image content block IDs
        List<Long> imageContentBlockIds = entity.getImageContentBlocks().stream()
                .map(ImageContentBlockEntity::getId)
                .sorted()
                .toList();

        // Collect associated gif content block IDs
        List<Long> gifContentBlockIds = entity.getGifContentBlocks().stream()
                .map(GifContentBlockEntity::getId)
                .sorted()
                .toList();

        return ContentTagModel.builder()
                .id(entity.getId())
                .name(entity.getTagName())
                .contentCollectionIds(contentCollectionIds)
                .imageContentBlockIds(imageContentBlockIds)
                .gifContentBlockIds(gifContentBlockIds)
                .build();
    }

    /**
     * Convert ContentPersonEntity to ContentPersonModel with associated content IDs
     */
    private ContentPersonModel toPersonModel(ContentPersonEntity entity) {
        // Collect associated image content block IDs
        List<Long> imageContentBlockIds = entity.getImageContentBlocks().stream()
                .map(ImageContentBlockEntity::getId)
                .sorted()
                .toList();

        return ContentPersonModel.builder()
                .id(entity.getId())
                .name(entity.getPersonName())
                .imageContentBlockIds(imageContentBlockIds)
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
    public List<edens.zac.portfolio.backend.model.ImageContentBlockModel> getAllImages() {
        return contentBlockRepository.findAllImagesOrderByCreateDateDesc().stream()
                .map(entity -> (edens.zac.portfolio.backend.model.ImageContentBlockModel)
                        contentBlockProcessingUtil.convertToModel(entity))
                .collect(Collectors.toList());
    }
}
