package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentTextEntity;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.ContentType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CollectionRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CollectionRepository repository;

    private CollectionEntity blogCollection;
    private CollectionEntity portfolioCollection;

    @BeforeEach
    void setUp() {
        // Create test collections
        blogCollection = new CollectionEntity();
        blogCollection.setType(CollectionType.BLOG);
        blogCollection.setTitle("Test Blog");
        blogCollection.setSlug("test-blog");
        blogCollection.setVisible(true);
        blogCollection.setCollectionDate(LocalDate.now().minusDays(1));

        portfolioCollection = new CollectionEntity();
        portfolioCollection.setType(CollectionType.PORTFOLIO);
        portfolioCollection.setTitle("Test Portfolio");
        portfolioCollection.setSlug("test-portfolio");
        portfolioCollection.setVisible(true);

        CollectionEntity hiddenCollection = new CollectionEntity();
        hiddenCollection.setType(CollectionType.BLOG);
        hiddenCollection.setTitle("Hidden Blog");
        hiddenCollection.setSlug("hidden-blog");
        hiddenCollection.setVisible(false);
        hiddenCollection.setCollectionDate(LocalDate.now());

        entityManager.persist(blogCollection);
        entityManager.flush();
        entityManager.persist(portfolioCollection);
        entityManager.flush();
        entityManager.persist(hiddenCollection);
        entityManager.flush();
    }

    @Test
    void findByTypeAndVisibleTrueOrderByPriorityAsc_ShouldReturnVisibleCollectionsOrderedByPriority() {
        List<CollectionEntity> results = repository.findTop50ByTypeAndVisibleTrueOrderByPriorityAsc(CollectionType.BLOG);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getSlug()).isEqualTo("test-blog");
        assertThat(results.getFirst().getVisible()).isTrue();
    }

    @Test
    void findByTypeOrderByPriorityAsc_ShouldReturnAllCollectionsOrderedByPriority() {
        List<CollectionEntity> results = repository.findTop50ByTypeOrderByPriorityAsc(CollectionType.BLOG);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getSlug()).isEqualTo("test-blog");
        assertThat(results.get(1).getSlug()).isEqualTo("hidden-blog");
    }

    @Test
    void findByTypeAndVisibleTrueOrderByCollectionDateDesc_ShouldReturnVisibleBlogsOrderedByDate() {
        List<CollectionEntity> results = repository.findTop50ByTypeAndVisibleTrueOrderByCollectionDateDesc(CollectionType.BLOG);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSlug()).isEqualTo("test-blog");
    }

    @Test
    void findByTypeOrderByCollectionDateDesc_ShouldReturnAllBlogsOrderedByDate() {
        List<CollectionEntity> results = repository.findTop50ByTypeOrderByCollectionDateDesc(CollectionType.BLOG);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getSlug()).isEqualTo("hidden-blog"); // newer date first
        assertThat(results.get(1).getSlug()).isEqualTo("test-blog");
    }

    @Test
    void findBySlugAndPasswordHash_ShouldReturnCollectionWhenPasswordMatches() {
        entityManager.flush(); // Since portfolioCollection is already persisted in setUp()

        Optional<CollectionEntity> result = repository.findBySlugAndPasswordHash("test-portfolio", "hashed-password");

        assertThat(result).isPresent();
        assertThat(result.get().getSlug()).isEqualTo("test-portfolio");
    }

    @Test
    void findBySlugAndPasswordHash_ShouldReturnEmptyWhenPasswordDoesNotMatch() {
        entityManager.flush(); // Since portfolioCollection is already persisted in setUp()

        Optional<CollectionEntity> result = repository.findBySlugAndPasswordHash("test-portfolio", "wrong-password");

        assertThat(result).isEmpty();
    }

    @Test
    void findBySlug_ShouldReturnCollectionMetadata() {
        Optional<CollectionEntity> result = repository.findBySlug("test-blog");

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Test Blog");
        assertThat(result.get().getContent()).isEmpty(); // No content fetched
    }

    @Test
    void findBySlugWithContent_ShouldReturnCollectionWith() {
        // Add content
        ContentTextEntity content1 = ContentTextEntity.builder()
                .contentType(ContentType.TEXT)
                .textContent("First content")
                .build();

        ContentTextEntity content2 = ContentTextEntity.builder()
                .contentType(ContentType.TEXT)
                .textContent("Second content")
                .build();

        entityManager.persist(content1);
        entityManager.persist(content2);
        entityManager.flush();
        
        // Clear the persistence context to force a fresh query
        entityManager.clear();

        Optional<CollectionEntity> result = repository.findBySlugWithContent("test-blog");

        assertThat(result).isPresent();
        assertThat(result.get().getContent()).hasSize(2);
    }

    @Test
    void countByType_ShouldReturnCorrectCount() {
        long blogCount = repository.countByType(CollectionType.BLOG);
        long portfolioCount = repository.countByType(CollectionType.PORTFOLIO);

        assertThat(blogCount).isEqualTo(2);
        assertThat(portfolioCount).isEqualTo(1);
    }
}