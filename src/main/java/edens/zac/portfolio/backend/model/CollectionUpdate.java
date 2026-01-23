package edens.zac.portfolio.backend.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Collection update wrapper using prev/new/remove pattern. All fields are optional to support
 * partial updates.
 *
 * <p>- prev: Collections to keep/update (with visibility/order) - newValue: New collections to add
 * - remove: Collection IDs to remove the entity from
 *
 * <p>Can be used for: - Adding content to collections - Adding collections to parent collections
 *
 * <p>Examples: - {prev: [{collectionId: 1, visible: true}]} = Update visibility - {newValue:
 * [{collectionId: 2, visible: true, orderIndex: 5}]} = Add to new collection at position 5 -
 * {remove: [3]} = Remove from collection ID 3
 *
 * <p>Note: For reordering content within a collection, use the dedicated reorder endpoint instead.
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
