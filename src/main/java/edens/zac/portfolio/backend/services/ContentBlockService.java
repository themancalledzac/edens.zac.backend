package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.entity.ContentTagEntity;

import java.util.List;
import java.util.Map;

/**
 * Service interface for managing content blocks, tags, and people.
 * Provides methods for CRUD operations on tags, people, and image content blocks.
 */
public interface ContentBlockService {

    /**
     * Create a new tag
     *
     * @param tagName The name of the tag to create
     * @return Map containing created tag data
     */
    Map<String, Object> createTag(String tagName);

    /**
     * Create a new person
     *
     * @param personName The name of the person to create
     * @return Map containing created person data
     */
    Map<String, Object> createPerson(String personName);

    /**
     * Update one or more images
     *
     * @param updates List of image update requests
     * @return Map containing update results (updatedIds, updatedCount, errors)
     */
    Map<String, Object> updateImages(List<ImageUpdateRequest> updates);

    /**
     * Delete one or more images
     *
     * @param imageIds List of image IDs to delete
     * @return Map containing deletion results (deletedIds, deletedCount, errors)
     */
    Map<String, Object> deleteImages(List<Long> imageIds);

    /**
     * Get all tags ordered alphabetically
     *
     * @return List of all tags
     */
    List<ContentTagEntity> getAllTags();

    /**
     * Get all people ordered alphabetically
     *
     * @return List of all people
     */
    List<ContentPersonEntity> getAllPeople();

    /**
     * Request DTO for image updates
     */
    @lombok.Data
    class ImageUpdateRequest {
        private Long id;
        private String title;
        private Integer rating;
        private String location;
        private String author;
        private Boolean isFilm;
        private Boolean blackAndWhite;
        private String camera;
        private String lens;
        private String focalLength;
        private String fStop;
        private String shutterSpeed;
        private Integer iso;
        private String createDate;
        private List<Long> tagIds;
        private List<Long> personIds;
    }
}
