package edens.zac.portfolio.backend.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Collection update wrapper using prev/new/remove pattern for managing child collections within a
 * parent collection. All fields are optional to support partial updates.
 *
 * <p>- prev: Update existing child collections (visibility/order) - newValue: Add new child
 * collections to the parent - remove: IDs of child collections to remove from the parent
 *
 * <p>The remove field accepts either: - The content ID (ContentCollectionEntity.id) - matches the
 * "id" field in API responses - The referenced collection ID (referencedCollectionId) - matches the
 * "referencedCollectionId" field
 *
 * <p>Examples: - {prev: [{collectionId: 1, visible: true}]} = Update visibility of child collection
 * - {newValue: [{collectionId: 2, visible: true, orderIndex: 5}]} = Add child collection at
 * position 5 - {remove: [3]} = Remove child collection with ID 3 from parent
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
