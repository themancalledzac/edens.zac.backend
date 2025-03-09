package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.BlogEntity;
import edens.zac.portfolio.backend.entity.CatalogEntity;
import edens.zac.portfolio.backend.entity.HomeCardEntity;
import edens.zac.portfolio.backend.model.HomeCardModel;
import edens.zac.portfolio.backend.repository.HomeCardRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class HomeProcessingUtil {

    private final HomeCardRepository homeCardRepository;

    @Autowired
    public HomeProcessingUtil(HomeCardRepository homeCardRepository) {
        this.homeCardRepository = homeCardRepository;
    }

    public List<HomeCardModel> getHomePage(Integer maxPriority) {
        List<HomeCardEntity> entities = homeCardRepository.getHomePage(maxPriority);
        return entities.stream()
                .map(this::convertModel)
                .collect(Collectors.toList());
    }

    public HomeCardModel convertModel(HomeCardEntity entity) {
        return HomeCardModel.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .cardType(entity.getCardType())
                .location(entity.getLocation())
                .date(entity.getDate())
                .priority(entity.getPriority())
                .coverImageUrl(entity.getCoverImageUrl())
                .slug(entity.getSlug())
                .text(entity.getText())
                .build();
    }

    public HomeCardEntity createHomeCardFromBlog(BlogEntity blog, int priority) {
        return HomeCardEntity.builder()
                .title(blog.getTitle())
                .cardType("blog")
                .location(blog.getLocation())
                .date(blog.getDate() != null ? blog.getDate().toString() : null)
                .priority(priority)
                .coverImageUrl(blog.getCoverImageUrl())
                .text(blog.getParagraph() != null ? blog.getParagraph() : null)
                .slug(blog.getSlug())
                .referenceId(blog.getId())
                .createdDate(LocalDateTime.now())
                .build();
    }

    public HomeCardEntity createHomeCardFromCatalog(CatalogEntity catalog, int priority) {
        return HomeCardEntity.builder()
                .title(catalog.getTitle())
                .cardType("catalog")
                .location(catalog.getLocation())
                .date(catalog.getDate() != null ? catalog.getDate().toString() : null)
                .priority(priority)
                .coverImageUrl(catalog.getCoverImageUrl())
                .text(null)
                .slug(catalog.getSlug())
                .referenceId(catalog.getId())
                .createdDate(LocalDateTime.now())
                .build();
    }

    // For creating special navigation cards (one-time setup)
    public HomeCardEntity createNavigationCard(String title, String cardType, String slug, int priority) {
        return HomeCardEntity.builder()
                .title(title)
                .cardType(cardType)
                .priority(priority)
                .slug(slug)
                .createdDate(LocalDateTime.now())
                .build();
    }
}
