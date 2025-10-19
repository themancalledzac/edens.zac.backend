package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.entity.ContentTagEntity;
import edens.zac.portfolio.backend.entity.ImageContentBlockEntity;
import edens.zac.portfolio.backend.model.ImageUpdateRequest;
import edens.zac.portfolio.backend.repository.ContentBlockRepository;
import edens.zac.portfolio.backend.repository.ContentPersonRepository;
import edens.zac.portfolio.backend.repository.ContentTagRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
                if (update.getTagIds() != null) {
                    Set<ContentTagEntity> tags = update.getTagIds().stream()
                            .map(tagId -> contentTagRepository.findById(tagId)
                                    .orElseThrow(() -> new EntityNotFoundException("Tag not found: " + tagId)))
                            .collect(Collectors.toSet());
                    image.setTags(tags);
                }

                // Update people if provided
                if (update.getPersonIds() != null) {
                    Set<ContentPersonEntity> people = update.getPersonIds().stream()
                            .map(personId -> contentPersonRepository.findById(personId)
                                    .orElseThrow(() -> new EntityNotFoundException("Person not found: " + personId)))
                            .collect(Collectors.toSet());
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
    public List<ContentTagEntity> getAllTags() {
        return contentTagRepository.findAllByOrderByTagNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentPersonEntity> getAllPeople() {
        return contentPersonRepository.findAllByOrderByPersonNameAsc();
    }
}
