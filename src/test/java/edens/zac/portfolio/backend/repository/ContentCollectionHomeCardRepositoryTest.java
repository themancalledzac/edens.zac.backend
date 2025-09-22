package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.ContentCollectionHomeCardEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ContentCollectionHomeCardRepositoryTest {

    @Autowired
    private ContentCollectionHomeCardRepository repository;

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

    @Test
    @DisplayName("getHomePage: maxPriority=2 returns only priority 1 and 2, excludes inactive/blank cover, orders by priority ASC then createdDate DESC")
    void getHomePage_maxPriority2_filtersAndOrdersCorrectly() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        // valid priorities 1..2
        repository.save(card(1, true, "https://img/1.jpg", now.minusHours(2)));
        repository.save(card(2, true, "https://img/2a.jpg", now.minusHours(1)));
        repository.save(card(2, true, "https://img/2b.jpg", now.minusMinutes(10))); // newer within same priority
        // outside maxPriority (should be excluded)
        repository.save(card(3, true, "https://img/3.jpg", now.minusHours(3)));
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
}
