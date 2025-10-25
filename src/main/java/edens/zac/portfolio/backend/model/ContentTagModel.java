package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Model representing a content tag for API responses.
 * Contains the tag's ID, name, and related entity IDs.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentTagModel {

    private Long id;
    private String name;

    /**
     * List of content collection IDs that use this tag.
     */
    @Builder.Default
    private List<Long> contentCollectionIds = new ArrayList<>();

    /**
     * List of image content block IDs that use this tag.
     */
    @Builder.Default
    private List<Long> imageContentBlockIds = new ArrayList<>();

    /**
     * List of gif content block IDs that use this tag.
     */
    @Builder.Default
    private List<Long> gifContentBlockIds = new ArrayList<>();
}