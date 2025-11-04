package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.*;
import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.repository.CollectionContentRepository;
import edens.zac.portfolio.backend.repository.CollectionRepository;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of ContentService that provides methods for
 * managing content, tags, and people.
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
    private final CollectionContentRepository collectionContentRepository;
    private final CollectionRepository collectionRepository;
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
    public Map<String, Object> updateImages(List<ContentImageUpdateRequest> updates) {
        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("At least one image update is required");
        }

        // Track updated images and newly created metadata
        List<ContentImageModel> updatedImages = new ArrayList<>();
        Set<ContentTagEntity> newlyCreatedTags = new HashSet<>();
        Set<ContentPersonEntity> newlyCreatedPeople = new HashSet<>();
        Set<ContentCameraEntity> newlyCreatedCameras = new HashSet<>();
        Set<ContentLensEntity> newlyCreatedLenses = new HashSet<>();
        Set<ContentFilmTypeEntity> newlyCreatedFilmTypes = new HashSet<>();
        List<String> errors = new ArrayList<>();

        for (ContentImageUpdateRequest update : updates) {
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
                    CollectionUpdate collectionUpdate = update.getCollections();

                    // Remove from collections if specified
                    if (collectionUpdate.getRemove() != null && !collectionUpdate.getRemove().isEmpty()) {
                        for (Long collectionIdToRemove : collectionUpdate.getRemove()) {
                            collectionContentRepository.removeContentFromCollection(
                                collectionIdToRemove,
                                List.of(image.getId())
                            );
                            log.info("Removed image {} from collection {}", image.getId(), collectionIdToRemove);
                        }
                    }

                    // Update existing collection relationships (visibility, orderIndex)
                    if (collectionUpdate.getPrev() != null && !collectionUpdate.getPrev().isEmpty()) {
                        contentProcessingUtil.handleContentChildCollectionUpdates(image, collectionUpdate.getPrev());
                    }

                    // Add to new collections if specified
                    if (collectionUpdate.getNewValue() != null && !collectionUpdate.getNewValue().isEmpty()) {
                        handleAddToCollections(image, collectionUpdate.getNewValue());
                    }
                }

                // Save the updated image
                ContentImageEntity savedImage = contentRepository.save(image);

                // Convert to model and add to results
                ContentImageModel imageModel = (ContentImageModel) contentProcessingUtil.convertRegularContentEntityToModel(savedImage);
                updatedImages.add(imageModel);

            } catch (EntityNotFoundException e) {
                errors.add(e.getMessage());
                log.warn("Entity not found during update: {}", e.getMessage());
            } catch (ClassCastException e) {
                errors.add("Content is not an image: " + update.getId());
                log.warn("Attempted to update non-image Content as image: {}", update.getId());
            } catch (Exception e) {
                errors.add("Error updating image " + update.getId() + ": " + e.getMessage());
                log.error("Error updating image {}: {}", update.getId(), e.getMessage(), e);
            }
        }

        // Build the response with updated images and new metadata
        ContentImageUpdateResponse.NewMetadata newMetadata = ContentImageUpdateResponse.NewMetadata.builder()
                .tags(newlyCreatedTags.isEmpty() ? null : newlyCreatedTags.stream().map(this::toTagModel).collect(Collectors.toList()))
                .people(newlyCreatedPeople.isEmpty() ? null : newlyCreatedPeople.stream().map(this::toPersonModel).collect(Collectors.toList()))
                .cameras(newlyCreatedCameras.isEmpty() ? null : newlyCreatedCameras.stream().map(ContentProcessingUtil::cameraEntityToCameraModel).collect(Collectors.toList()))
                .lenses(newlyCreatedLenses.isEmpty() ? null : newlyCreatedLenses.stream().map(ContentProcessingUtil::lensEntityToLensModel).collect(Collectors.toList()))
                .filmTypes(newlyCreatedFilmTypes.isEmpty() ? null : newlyCreatedFilmTypes.stream().map(this::toFilmTypeModel).collect(Collectors.toList()))
                .build();

        ContentImageUpdateResponse response = ContentImageUpdateResponse.builder()
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
            ContentImageUpdateRequest updateRequest,
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
            ContentImageUpdateRequest.CameraUpdate cameraUpdate = updateRequest.getCamera();
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
            ContentImageUpdateRequest.LensUpdate lensUpdate = updateRequest.getLens();
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
            ContentImageUpdateRequest.FilmTypeUpdate filmTypeUpdate = updateRequest.getFilmType();
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
     * Uses shared utility method from ContentProcessingUtil.
     */
    private void updateImageTags(ContentImageEntity image, TagUpdate tagUpdate, Set<ContentTagEntity> newTags) {
        Set<ContentTagEntity> updatedTags = contentProcessingUtil.updateTags(
                image.getTags(),
                tagUpdate,
                newTags // Track newly created tags for response
        );
        image.setTags(updatedTags);
    }

    /**
     * Add content (image) to new collections with specified visibility and orderIndex.
     * Creates join table entries for the content in the specified collections.
     *
     * @param image The image to add to collections
     * @param collections List of ChildCollection objects containing collectionId, visible, and orderIndex
     */
    private void handleAddToCollections(ContentImageEntity image, List<ChildCollection> collections) {
        for (ChildCollection childCollection : collections) {
            if (childCollection.getCollectionId() == null) {
                log.warn("Skipping collection addition: collectionId is null");
                continue;
            }

            // Verify collection exists
            CollectionEntity collection = collectionRepository.findById(childCollection.getCollectionId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Collection not found: " + childCollection.getCollectionId()));

            // Check if this content is already in the collection
            CollectionContentEntity existing = collectionContentRepository
                    .findByCollectionIdAndContentId(childCollection.getCollectionId(), image.getId());

            if (existing != null) {
                log.warn("Image {} is already in collection {}. Skipping duplicate add.",
                        image.getId(), childCollection.getCollectionId());
                continue;
            }

            // Determine orderIndex: use provided value or append to end
            Integer orderIndex = childCollection.getOrderIndex();
            if (orderIndex == null) {
                orderIndex = collectionContentRepository
                        .getNextOrderIndexForCollection(childCollection.getCollectionId());
            }

            // Create join table entry
            CollectionContentEntity joinEntry = CollectionContentEntity.builder()
                    .collection(collection)
                    .content(image)
                    .orderIndex(orderIndex)
                    .visible(childCollection.getVisible() != null ? childCollection.getVisible() : true)
                    .imageUrl(null)  // Image URL not applicable for image content (image has its own URL)
                    .build();

            collectionContentRepository.save(joinEntry);
            log.info("Added image {} to collection {} at orderIndex {} with visible={}",
                    image.getId(), childCollection.getCollectionId(), orderIndex, joinEntry.getVisible());
        }
    }

    /**
     * Update image people and track newly created ones.
     * Uses shared utility method from ContentProcessingUtil.
     */
    private void updateImagePeople(ContentImageEntity image, PersonUpdate personUpdate, Set<ContentPersonEntity> newPeople) {
        Set<ContentPersonEntity> updatedPeople = contentProcessingUtil.updatePeople(
                image.getPeople(),
                personUpdate,
                newPeople // Track newly created people for response
        );
        image.setPeople(updatedPeople);
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
    public List<ContentImageModel> getAllImages() {
        return contentRepository.findAllImagesOrderByCreateDateDesc().stream()
                .map(entity -> (ContentImageModel)
                        contentProcessingUtil.convertRegularContentEntityToModel(entity))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<ContentImageModel> createImages(Long collectionId, List<MultipartFile> files) {
        log.debug("Creating images for collection ID: {}", collectionId);

        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one file is required");
        }

        // Verify collection exists
        CollectionEntity collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new EntityNotFoundException("Collection not found: " + collectionId));

        List<ContentImageModel> createdImages = new ArrayList<>();

        // Get the next order index for this collection
        Integer orderIndex = collectionContentRepository.getNextOrderIndexForCollection(collectionId);

        for (MultipartFile file : files) {
            try {
                // First, check if this image already exists in the database (duplicate detection)
                if (file.getContentType() != null && file.getContentType().startsWith("image/") && !file.getContentType().equals("image/gif")) {
                    // Generate file identifier to check for duplicates
                    String originalFilename = file.getOriginalFilename();
                    if (originalFilename != null) {
                        String date = java.time.LocalDate.now().toString();
                        String fileIdentifier = date + "/" + originalFilename;

                        // Check if image already exists
                        if (contentRepository.existsByFileIdentifier(fileIdentifier)) {
                            log.info("Skipping duplicate image: {}", originalFilename);
                            continue; // Skip this file and move to the next
                        }
                    }
                }

                // Process file based on content type
                if (file.getContentType() != null && file.getContentType().startsWith("image/")) {
                    if (file.getContentType().equals("image/gif")) {
                        // Process as GIF - skip for now (future implementation)
                        log.debug("Skipping GIF file (not yet implemented): {}", file.getOriginalFilename());
                    } else {
                        // STEP 1: Process and save the image content (NO collection reference)
                        ContentImageEntity img = contentProcessingUtil.processImageContent(file, null);

                        if (img != null && img.getId() != null) {
                            // STEP 2: Create join table entry linking content to collection
                            CollectionContentEntity joinEntry = CollectionContentEntity.builder()
                                    .collection(collection)
                                    .content(img)
                                    .orderIndex(orderIndex)
                                    .imageUrl(null)  // Image URL not applicable for image content
                                    .visible(true)   // Visible by default
                                    .build();

                            collectionContentRepository.save(joinEntry);

                            log.debug("Created join table entry for image {} in collection {} at orderIndex {}",
                                    img.getId(), collectionId, orderIndex);

                            // STEP 3: Convert to model with join table metadata and add to results
                            ContentModel contentModel = contentProcessingUtil.convertEntityToModel(joinEntry);
                            if (contentModel instanceof ContentImageModel imageModel) {
                                createdImages.add(imageModel);
                            } else {
                                log.error("Expected ContentImageModel but got {}", contentModel != null ? contentModel.getClass() : "null");
                            }

                            // Increment order index for next image
                            orderIndex++;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error processing file: {}", e.getMessage(), e);
            }
        }

        return createdImages;
    }

//    @Override
//    @Transactional
//    public ContentModel createContent(CreateTextContentRequest request) {
//        log.debug("Creating content for collection ID: {}", request.getCollectionId());
//
//        if (request.getCollectionId() == null || request.getCollectionId() <= 0) {
//            throw new IllegalArgumentException("Collection ID is required");
//        }
//
//        if (request.)
//    }

    @Override
    @Transactional
    public ContentTextModel createTextContent(CreateTextContentRequest request) {
        log.debug("Creating text content for collection ID: {}", request.getCollectionId());

        if (request.getTextContent() == null || request.getTextContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Text content is required");
        }

        // Verify collection exists
        CollectionEntity collection = collectionRepository.findById(request.getCollectionId())
                .orElseThrow(() -> new EntityNotFoundException("Collection not found: " + request.getCollectionId()));

        // Get the next order index for this collection
        Integer orderIndex = collectionContentRepository.getNextOrderIndexForCollection(request.getCollectionId());

        // Create text content entity
        ContentTextEntity textEntity = ContentTextEntity.builder()
                .textContent(request.getTextContent().trim())
                .formatType(request.getFormType() != null ? request.getFormType().name() : "PLAIN")
                .build();

        // Save the text content
        textEntity = contentRepository.save(textEntity);

        // Create join table entry linking content to collection
        CollectionContentEntity joinEntry = CollectionContentEntity.builder()
                .collection(collection)
                .content(textEntity)
                .orderIndex(orderIndex)
                .imageUrl(null)  // Image URL not applicable for text content
                .visible(true)
                .build();

        collectionContentRepository.save(joinEntry);

        log.info("Created text content {} in collection {} at orderIndex {}",
                textEntity.getId(), request.getCollectionId(), orderIndex);

        // Convert to model and return
        ContentModel contentModel = contentProcessingUtil.convertEntityToModel(joinEntry);
        if (contentModel instanceof ContentTextModel textModel) {
            return textModel;
        } else {
            throw new IllegalStateException("Expected ContentTextModel but got " + 
                    (contentModel != null ? contentModel.getClass() : "null"));
        }
    }

//    @Override
//    @Transactional
//    public ContentCodeModel createCodeContent(CreateCodeContentRequest request) {
//        log.debug("Creating code content for collection ID: {}", request.getCollectionId());
//
//        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
//            throw new IllegalArgumentException("Code content is required");
//        }
//
//        // Verify collection exists
//        CollectionEntity collection = collectionRepository.findById(request.getCollectionId())
//                .orElseThrow(() -> new EntityNotFoundException("Collection not found: " + request.getCollectionId()));
//
//        // Get the current highest order index for this collection from the join table
//        Integer orderIndex = collectionContentRepository.getMaxOrderIndexForCollection(request.getCollectionId());
//        orderIndex = (orderIndex != null) ? orderIndex + 1 : 0;
//
//        // Create code content entity
//        ContentCodeEntity codeEntity = ContentCodeEntity.builder()
//                .code(request.getCode().trim())
//                .language(request.getLanguage() != null ? request.getLanguage() : "plaintext")
//                .title(request.getTitle())
//                .build();
//
//        // Save the code content
//        codeEntity = (ContentCodeEntity) contentRepository.save(codeEntity);
//
//        // Create join table entry linking content to collection
//        CollectionContentEntity joinEntry = CollectionContentEntity.builder()
//                .collection(collection)
//                .content(codeEntity)
//                .orderIndex(orderIndex)
//                .caption(request.getDescription())
//                .visible(true)
//                .build();
//
//        collectionContentRepository.save(joinEntry);
//
//        log.info("Created code content {} in collection {} at orderIndex {}",
//                codeEntity.getId(), request.getCollectionId(), orderIndex);
//
//        // Convert to model and return
//        return (ContentCodeModel) contentProcessingUtil.convertToModel(codeEntity, joinEntry);
//    }
}
