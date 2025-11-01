package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for batch image update operations.
 * Returns full image blocks for all updated images,
 * plus metadata for any newly created entities (tags, people, cameras, lenses, film types).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentImageUpdateResponse {

    /**
     * Full image content blocks for all successfully updated images.
     * Includes all metadata (tags, people, camera, lens, film type, etc.)
     */
    private List<ContentImageModel> updatedImages;

    /**
     * Metadata for newly created entities during the update operation.
     * Only includes entities that were created (not existing ones that were referenced).
     */
    private NewMetadata newMetadata;

    /**
     * List of error messages if any updates failed
     */
    private List<String> errors;

    /**
     * Container for newly created metadata entities
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NewMetadata {
        /**
         * Tags created during the update
         */
        private List<ContentTagModel> tags;

        /**
         * People created during the update
         */
        private List<ContentPersonModel> people;

        /**
         * Cameras created during the update
         */
        private List<ContentCameraModel> cameras;

        /**
         * Lenses created during the update
         */
        private List<ContentLensModel> lenses;

        /**
         * Film types created during the update
         */
        private List<ContentFilmTypeModel> filmTypes;
    }
}