package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Tag update wrapper using prev/new/remove pattern.
 * All fields are optional to support partial updates.
 *
 * - prev: List of existing tag IDs to keep/add
 * - newValue: List of new tag names to create and add
 * - remove: List of tag IDs to remove
 *
 * Examples:
 * - {prev: [2, 3]} = Add existing tags 2 and 3
 * - {newValue: ["landscape", "nature"]} = Create and add new tags
 * - {remove: [1]} = Remove tag ID 1
 * - {prev: [2], newValue: ["landscape"], remove: [1]} = All operations at once
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagUpdate {
    private List<Long> prev;
    private List<String> newValue;
    private List<Long> remove;
}