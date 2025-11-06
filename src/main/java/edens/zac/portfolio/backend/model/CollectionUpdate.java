package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Collection update wrapper using prev/new/remove pattern.
 * All fields are optional to support partial updates.
 *
 * - prev: Collections to keep/update (with visibility/order)
 * - newValue: New collections to add
 * - remove: Collection IDs to remove the entity from
 *
 * Can be used for:
 * - Adding content to collections
 * - Adding collections to parent collections
 *
 * Examples:
 * - {prev: [{collectionId: 1, visible: true, orderIndex: 0}]} = Update visibility/order
 * - {newValue: [{collectionId: 2, visible: true, orderIndex: 5}]} = Add to new collection
 * - {remove: [3]} = Remove from collection ID 3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionUpdate {
    private List<ChildCollection> prev;
    private List<ChildCollection> newValue;
    private List<Long> remove;
}