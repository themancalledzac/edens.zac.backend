package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Person update wrapper using prev/new/remove pattern.
 * All fields are optional to support partial updates.
 *
 * - prev: List of existing person IDs to keep/add
 * - newValue: List of new person names to create and add
 * - remove: List of person IDs to remove
 *
 * Examples:
 * - {prev: [5, 6]} = Add existing people 5 and 6
 * - {newValue: ["John Doe"]} = Create and add new person
 * - {remove: [3]} = Remove person ID 3
 * - {prev: [5], newValue: ["Jane"], remove: [3]} = All operations at once
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonUpdate {
    private List<Long> prev;
    private List<String> newValue;
    private List<Long> remove;
}