package edens.zac.portfolio.backend.services;

public class ContentCollectionService {

    // TODO: Moving these methods here for later use
    //    /**
//     * Add a content block to this collection.
//     * Automatically sets the orderIndex to the next available value.
//     *
//     * @param contentBlock The content block to add
//     * @return The updated collection entity
//     */
//    public ContentCollectionEntity addContentBlock(ContentBlockEntity contentBlock) {
//        if (contentBlock.getOrderIndex() == null) {
//            contentBlock.setOrderIndex(contentBlocks.size());
//        }
//
//        contentBlocks.add(contentBlock);
//        updateTotalBlocks();
//        return this;
//    }
//
//    /**
//     * Remove a content block from this collection.
//     *
//     * @param contentBlock The content block to remove
//     * @return The updated collection entity
//     */
//    public ContentCollectionEntity removeContentBlock(ContentBlockEntity contentBlock) {
//        if (contentBlocks.remove(contentBlock)) {
//            reorderContentBlocks();
//            updateTotalBlocks();
//        }
//        return this;
//    }
//
//    /**
//     * Reorder content blocks to ensure sequential orderIndex values.
//     * This is useful after removing content blocks or when reorganizing.
//     */
//    public void reorderContentBlocks() {
//        contentBlocks.sort(Comparator.comparing(ContentBlockEntity::getOrderIndex));
//
//        for (int i = 0; i < contentBlocks.size(); i++) {
//            contentBlocks.get(i).setOrderIndex(i);
//        }
//    }
//
//    /**
//     * Move a content block to a new position in the collection.
//     *
//     * @param contentBlockId The ID of the content block to move
//     * @param newIndex The new position index
//     * @return True if the content block was moved successfully
//     */
//    public boolean moveContentBlock(Long contentBlockId, int newIndex) {
//        // Find the content block by ID
//        ContentBlockEntity blockToMove = contentBlocks.stream()
//                .filter(block -> block.getId().equals(contentBlockId))
//                .findFirst()
//                .orElse(null);
//
//        if (blockToMove == null || newIndex < 0 || newIndex >= contentBlocks.size()) {
//            return false;
//        }
//
//        // Get the current index
//        int currentIndex = contentBlocks.indexOf(blockToMove);
//        if (currentIndex == newIndex) {
//            return true; // Already in the correct position
//        }
//
//        // Remove from the current position
//        contentBlocks.remove(currentIndex);
//
//        // Insert at new position
//        contentBlocks.add(newIndex, blockToMove);
//
//        // Update all orderIndex values
//        for (int i = 0; i < contentBlocks.size(); i++) {
//            contentBlocks.get(i).setOrderIndex(i);
//        }
//
//        return true;
//    }

//    /**
//     * Update the total blocks count.
//     * This should be called whenever content blocks are added or removed.
//     */
//    public void updateTotalBlocks() {
//        this.totalBlocks = contentBlocks.size();
//    }
}
