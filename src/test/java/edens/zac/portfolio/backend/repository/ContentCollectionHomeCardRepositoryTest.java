package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionHomeCardEntity;
import edens.zac.portfolio.backend.types.CollectionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ContentCollectionHomeCardRepositoryTest {

    @Autowired
    private ContentCollectionHomeCardRepository repository;

    @Autowired
    private ContentCollectionRepository collectionRepository;

    private ContentCollectionHomeCardEntity card(int priority, boolean active, String coverUrl, LocalDateTime created) {
        return ContentCollectionHomeCardEntity.builder()
                .title("t" + priority)
                .cardType("PORTFOLIO")
                .location("loc")
                .date("2025-09-20")
                .priority(priority)
                .coverImageUrl(coverUrl)
                .text(null)
                .isActiveHomeCard(active)
                .slug("slug-" + priority)
                .referenceId((long) priority)
                .createdDate(created)
                .build();
    }

    private ContentCollectionEntity collection(String slug, boolean visible) {
        ContentCollectionEntity entity = new ContentCollectionEntity();
        entity.setType(CollectionType.PORTFOLIO);
        entity.setTitle("Collection " + slug);
        entity.setSlug(slug);
        entity.setCollectionDate(LocalDate.now());
        entity.setVisible(visible);
        entity.setPriority(1);
        return entity;
    }

    @Test
    @DisplayName("getHomePage: maxPriority=2 returns only priority 1 and 2, excludes inactive/blank cover, orders by priority ASC then createdDate DESC")
    void getHomePage_maxPriority2_filtersAndOrdersCorrectly() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();

        // Create visible collections for the cards to reference
        ContentCollectionEntity collection1 = collectionRepository.save(collection("collection-1", true));
        ContentCollectionEntity collection2 = collectionRepository.save(collection("collection-2", true));
        ContentCollectionEntity collection3 = collectionRepository.save(collection("collection-3", true));

        // valid priorities 1..2
        ContentCollectionHomeCardEntity card1 = card(1, true, "https://img/1.jpg", now.minusHours(2));
        card1.setReferenceId(collection1.getId());
        repository.save(card1);

        ContentCollectionHomeCardEntity card2a = card(2, true, "https://img/2a.jpg", now.minusHours(1));
        card2a.setReferenceId(collection2.getId());
        repository.save(card2a);

        ContentCollectionHomeCardEntity card2b = card(2, true, "https://img/2b.jpg", now.minusMinutes(10));
        card2b.setReferenceId(collection2.getId());
        repository.save(card2b);

        // outside maxPriority (should be excluded)
        ContentCollectionHomeCardEntity card3 = card(3, true, "https://img/3.jpg", now.minusHours(3));
        card3.setReferenceId(collection3.getId());
        repository.save(card3);

        // inactive or blank cover (should be excluded)
        repository.save(card(1, false, "https://img/inactive.jpg", now.minusHours(4)));
        repository.save(card(2, true, "", now.minusHours(5)));
        repository.save(card(2, true, null, now.minusHours(6)));

        // Act
        List<ContentCollectionHomeCardEntity> results = repository.getHomePage(2);

        // Assert
        assertThat(results)
                .extracting(ContentCollectionHomeCardEntity::getPriority)
                .containsExactly(1, 2, 2);

        // First should be priority 1; then within priority 2, createdDate DESC so the newer (2b) comes before (2a)
        assertThat(results.get(0).getPriority()).isEqualTo(1);
        assertThat(results.get(1).getPriority()).isEqualTo(2);
        assertThat(results.get(2).getPriority()).isEqualTo(2);
        assertThat(results.get(1).getCoverImageUrl()).isEqualTo("https://img/2b.jpg");
        assertThat(results.get(2).getCoverImageUrl()).isEqualTo("https://img/2a.jpg");

        // Ensure excluded ones are not present
        assertThat(results)
                .noneMatch(e -> e.getPriority() == 3)
                .noneMatch(e -> !e.isActiveHomeCard())
                .noneMatch(e -> e.getCoverImageUrl() == null || e.getCoverImageUrl().isBlank());
    }

    @Test
    @DisplayName("getHomePage: excludes home cards with visible=false collections")
    void getHomePage_excludesInvisibleCollections() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();

        // Create collections - some visible, some not
        ContentCollectionEntity visibleCollection = collectionRepository.save(collection("visible-collection", true));
        ContentCollectionEntity invisibleCollection = collectionRepository.save(collection("invisible-collection", false));

        // Create home cards referencing these collections
        ContentCollectionHomeCardEntity visibleCard = card(1, true, "https://img/visible.jpg", now.minusHours(1));
        visibleCard.setReferenceId(visibleCollection.getId());
        repository.save(visibleCard);

        ContentCollectionHomeCardEntity invisibleCard = card(1, true, "https://img/invisible.jpg", now.minusHours(2));
        invisibleCard.setReferenceId(invisibleCollection.getId());
        repository.save(invisibleCard);

        // Create a home card with no referenceId (should be included)
        ContentCollectionHomeCardEntity noRefCard = card(1, true, "https://img/noref.jpg", now.minusHours(3));
        noRefCard.setReferenceId(null);
        repository.save(noRefCard);

        // Act
        List<ContentCollectionHomeCardEntity> results = repository.getHomePage(2);

        // Assert
        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(ContentCollectionHomeCardEntity::getCoverImageUrl)
                .containsExactlyInAnyOrder("https://img/visible.jpg", "https://img/noref.jpg")
                .doesNotContain("https://img/invisible.jpg");
    }
}
