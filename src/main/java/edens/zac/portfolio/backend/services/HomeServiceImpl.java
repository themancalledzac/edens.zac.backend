package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.BlogEntity;
import edens.zac.portfolio.backend.entity.CatalogEntity;
import edens.zac.portfolio.backend.entity.HomeCardEntity;
import edens.zac.portfolio.backend.model.HomeCardModel;
import edens.zac.portfolio.backend.repository.HomeCardRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HomeServiceImpl implements HomeService {

    private final HomeCardRepository homeCardRepository;
    private final HomeCardProcessingUtil homeCardProcessingUtil;

    @Autowired
    public HomeServiceImpl(HomeCardRepository homeCardRepository, HomeCardProcessingUtil homeCardProcessingUtil) {
        this.homeCardRepository = homeCardRepository;
        this.homeCardProcessingUtil = homeCardProcessingUtil;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HomeCardModel> getHomePage() {
        Integer homePagePriority = 3;

        List<HomeCardEntity> entities = homeCardRepository.getHomePage(homePagePriority);
        return entities.stream()
                .map(homeCardProcessingUtil::convertModel)
                .collect(Collectors.toList());
    }

    @Override
    public HomeCardModel createHomeCardFromCatalog(CatalogEntity catalog, Integer priority) {
        log.info("Creating home card from catalog: {}", catalog.getTitle());

        // Check if a HomeCard already exists for this catalog
        homeCardRepository.findByCardTypeAndReferenceId("catalog", catalog.getId())
                .ifPresent(homeCard -> {
                    log.info("Home card already exists: {}", homeCard.getId());
                    return;
                });

        // Create new HomeCard Entity
        HomeCardEntity homeCardEntity = homeCardProcessingUtil.createHomeCardFromCatalog(catalog, priority);

        // Save the entity
        HomeCardEntity savedEntity = homeCardRepository.save(homeCardEntity);
        log.info("HomeCard created successfully with ID: {}", savedEntity.getId());

        // Convert and return model
        return homeCardProcessingUtil.convertModel(savedEntity);
    }

    @Override
    public HomeCardModel createHomeCardFromBlog(BlogEntity blog, Integer priority) {
        log.info("Creating HomeCard for blog: {}", blog.getTitle());

        // Check if a HomeCard already exists for this blog
        homeCardRepository.findByCardTypeAndReferenceId("blog", blog.getId())
                .ifPresent(existing -> {
                    log.info("HomeCard already exists for blog ID: {}", blog.getId());
                    return;
                });

        // Create new HomeCard entity
        HomeCardEntity homeCardEntity = homeCardProcessingUtil.createHomeCardFromBlog(blog,
                priority != null ? priority : 2); // Default priority 2

        // Save the entity
        HomeCardEntity savedEntity = homeCardRepository.save(homeCardEntity);
        log.info("HomeCard created successfully with ID: {}", savedEntity.getId());

        // Convert and return model
        return homeCardProcessingUtil.convertModel(savedEntity);
    }

    @Transactional
    public HomeCardModel createHomeCard(HomeCardModel homeCardModel) {
        // TODO: Will probably be handled in createCatalog or createBlog
        //  - will we have a use for 'createHomeCard', other than 'createNavigationCard'?
        return null;
    }

    @Transactional
    public HomeCardModel createHomeCardFromPrevious(HomeCardModel homeCardModel) {
        return null;
    }
}
