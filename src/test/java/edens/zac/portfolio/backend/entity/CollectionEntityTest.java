package edens.zac.portfolio.backend.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Covers CollectionEntity.getTotalPages, the entity's only hand-written logic. */
class CollectionEntityTest {

  @Test
  void testGetTotalPages() {
    // Test with typical values
    CollectionEntity collection = new CollectionEntity();
    collection.setTitle("Test Collection");
    collection.setSlug("test-collection");
    collection.setTotalContent(100);
    collection.setContentPerPage(30);

    assertEquals(4, collection.getTotalPages()); // 100/30 = 3.33, rounded up to 4

    // Test with exact division
    collection.setTotalContent(90);
    assertEquals(3, collection.getTotalPages()); // 90/30 = 3.0

    // Test with zero blocks
    collection.setTotalContent(0);
    assertEquals(0, collection.getTotalPages());

    // Test with null values
    collection.setTotalContent(null);
    assertEquals(0, collection.getTotalPages());

    collection.setTotalContent(100);
    collection.setContentPerPage(null);
    assertEquals(0, collection.getTotalPages());

    // Test with zero blocks per page
    collection.setContentPerPage(0);
    assertEquals(0, collection.getTotalPages());
  }
}
