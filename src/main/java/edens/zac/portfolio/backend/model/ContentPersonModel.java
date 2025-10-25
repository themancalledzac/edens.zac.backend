package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Model representing a person for API responses.
 * Contains the person's ID, name, and related image content block IDs.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentPersonModel {

    private Long id;
    private String name;

    /**
     * List of image content block IDs that this person is tagged in.
     */
    @Builder.Default
    private List<Long> imageContentBlockIds = new ArrayList<>();
}