package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.CollectionRequests;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The day-blog get-after-create round trip. The read half keys on is_blog; the create half must set
 * it, or every upload batch for the same capture day creates another slug-suffixed public blog.
 */
class DayBlogFlagIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private CollectionService collectionService;
  @Autowired private CollectionRepository collectionRepository;

  @Test
  @DisplayName("a create with isBlog=true is found by findBlogsByCollectionDate")
  void createWithBlogFlag_isFoundByDateLookup() {
    LocalDate day = LocalDate.of(2019, 3, 14);

    CollectionRequests.Create request =
        new CollectionRequests.Create(
            null, day.toString(), null, null, null, day, null, Boolean.TRUE);
    collectionService.createCollection(request);

    List<CollectionEntity> found = collectionRepository.findBlogsByCollectionDate(day);

    assertThat(found).hasSize(1);
    assertThat(found.get(0).isBlog()).isTrue();
    assertThat(found.get(0).isClient()).isFalse();
  }
}
