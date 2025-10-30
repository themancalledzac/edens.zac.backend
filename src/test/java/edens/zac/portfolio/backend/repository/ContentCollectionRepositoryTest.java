package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.entity.TextContentEntity;
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
class ContentCollectionRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ContentCollectionRepository repository;

    private ContentCollectionEntity blogCollection;
    private ContentCollectionEntity portfolioCollection;

    @BeforeEach
    void setUp() {
        // Create test collections
        blogCollection = new ContentCollectionEntity();
        blogCollection.setType(CollectionType.BLOG);
        blogCollection.setTitle("Test Blog");
        blogCollection.setSlug("test-blog");
        blogCollection.setVisible(true);
        blogCollection.setPriority(1);
        blogCollection.setCollectionDate(LocalDate.now().minusDays(1));

        portfolioCollection = new ContentCollectionEntity();
        portfolioCollection.setType(CollectionType.PORTFOLIO);
        portfolioCollection.setTitle("Test Portfolio");
        portfolioCollection.setSlug("test-portfolio");
        portfolioCollection.setVisible(true);
        portfolioCollection.setPriority(2);

        ContentCollectionEntity hiddenCollection = new ContentCollectionEntity();
        hiddenCollection.setType(CollectionType.BLOG);
        hiddenCollection.setTitle("Hidden Blog");
        hiddenCollection.setSlug("hidden-blog");
        hiddenCollection.setVisible(false);
        hiddenCollection.setPriority(1);
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
        List<ContentCollectionEntity> results = repository.findTop50ByTypeAndVisibleTrueOrderByPriorityAsc(CollectionType.BLOG);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getSlug()).isEqualTo("test-blog");
        assertThat(results.getFirst().getVisible()).isTrue();
    }

    @Test
    void findByTypeOrderByPriorityAsc_ShouldReturnAllCollectionsOrderedByPriority() {
        List<ContentCollectionEntity> results = repository.findTop50ByTypeOrderByPriorityAsc(CollectionType.BLOG);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getSlug()).isEqualTo("test-blog");
        assertThat(results.get(1).getSlug()).isEqualTo("hidden-blog");
    }

    @Test
    void findByTypeAndVisibleTrueOrderByCollectionDateDesc_ShouldReturnVisibleBlogsOrderedByDate() {
        List<ContentCollectionEntity> results = repository.findTop50ByTypeAndVisibleTrueOrderByCollectionDateDesc(CollectionType.BLOG);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSlug()).isEqualTo("test-blog");
    }

    @Test
    void findByTypeOrderByCollectionDateDesc_ShouldReturnAllBlogsOrderedByDate() {
        List<ContentCollectionEntity> results = repository.findTop50ByTypeOrderByCollectionDateDesc(CollectionType.BLOG);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getSlug()).isEqualTo("hidden-blog"); // newer date first
        assertThat(results.get(1).getSlug()).isEqualTo("test-blog");
    }

    @Test
    void findBySlugAndPasswordHash_ShouldReturnCollectionWhenPasswordMatches() {
        portfolioCollection.setPasswordHash("hashed-password");
        portfolioCollection.setPasswordProtected(true);
        entityManager.flush(); // Since portfolioCollection is already persisted in setUp()

        Optional<ContentCollectionEntity> result = repository.findBySlugAndPasswordHash("test-portfolio", "hashed-password");

        assertThat(result).isPresent();
        assertThat(result.get().getSlug()).isEqualTo("test-portfolio");
    }

    @Test
    void findBySlugAndPasswordHash_ShouldReturnEmptyWhenPasswordDoesNotMatch() {
        portfolioCollection.setPasswordHash("hashed-password");
        entityManager.flush(); // Since portfolioCollection is already persisted in setUp()

        Optional<ContentCollectionEntity> result = repository.findBySlugAndPasswordHash("test-portfolio", "wrong-password");

        assertThat(result).isEmpty();
    }

    @Test
    void findBySlug_ShouldReturnCollectionMetadata() {
        Optional<ContentCollectionEntity> result = repository.findBySlug("test-blog");

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Test Blog");
        assertThat(result.get().getContentBlocks()).isEmpty(); // No blocks fetched
    }

    @Test
    void findBySlugWithContentBlocks_ShouldReturnCollectionWithBlocks() {
        // Add content blocks
        TextContentEntity block1 = TextContentEntity.builder()
                .collectionId(blogCollection.getId())
                .orderIndex(0)
                .blockType(ContentType.TEXT)
                .content("First block content")
                .build();

        TextContentEntity block2 = TextContentEntity.builder()
                .collectionId(blogCollection.getId())
                .orderIndex(1)
                .blockType(ContentType.TEXT)
                .content("Second block content")
                .build();

        entityManager.persist(block1);
        entityManager.persist(block2);
        entityManager.flush();
        
        // Clear the persistence context to force a fresh query
        entityManager.clear();

        Optional<ContentCollectionEntity> result = repository.findBySlugWithContentBlocks("test-blog");

        assertThat(result).isPresent();
        assertThat(result.get().getContentBlocks()).hasSize(2);
        assertThat(result.get().getContentBlocks().get(0).getOrderIndex()).isEqualTo(0);
        assertThat(result.get().getContentBlocks().get(1).getOrderIndex()).isEqualTo(1);
    }

    @Test
    void countByType_ShouldReturnCorrectCount() {
        long blogCount = repository.countByType(CollectionType.BLOG);
        long portfolioCount = repository.countByType(CollectionType.PORTFOLIO);

        assertThat(blogCount).isEqualTo(2);
        assertThat(portfolioCount).isEqualTo(1);
    }
}