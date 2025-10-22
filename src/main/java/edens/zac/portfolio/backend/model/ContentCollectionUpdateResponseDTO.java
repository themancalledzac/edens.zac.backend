package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for the collection update/manage endpoint.
 * Contains the collection along with all metadata needed for the update UI:
 * tags, people, cameras, film types, and film formats.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentCollectionUpdateResponseDTO {

    /**
     * The content collection with all its data
     */
    private ContentCollectionModel collection;

    /**
     * All available tags that can be assigned to content blocks
     */
    private List<ContentTagModel> tags;

    /**
     * All available people that can be tagged in content blocks
     */
    private List<ContentPersonModel> people;

    /**
     * All available collections in the system
     */
    private List<CollectionListModel> collections;

    /**
     * All available cameras for film photography metadata
     */
    private List<ContentCameraModel> cameras;

    /**
     * All available film types with their metadata (display name, default ISO)
     */
    private List<ContentFilmTypeModel> filmTypes;

    /**
     * All available film formats (35mm, 120, etc.)
     */
    private List<FilmFormatDTO> filmFormats;
}