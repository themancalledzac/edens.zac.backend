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
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HomeServiceImpl implements HomeService {

    private final HomeCardRepository homeCardRepository;
    private final HomeProcessingUtil homeCardProcessingUtil;

    @Autowired
    public HomeServiceImpl(HomeCardRepository homeCardRepository, HomeProcessingUtil homeCardProcessingUtil) {
        this.homeCardRepository = homeCardRepository;
        this.homeCardProcessingUtil = homeCardProcessingUtil;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HomeCardModel> getHomePage() {
        Integer homePagePriority = 2;

        // TODO: Add error handling at this step
        List<HomeCardEntity> entities = homeCardRepository.getHomePage(homePagePriority);
        return entities.stream()
                .map(homeCardProcessingUtil::convertModel)
                .collect(Collectors.toList());
    }

    @Override
    public void createHomeCardFromCatalog(CatalogEntity catalog) {
        log.info("Creating home card from catalog: {}", catalog.getTitle());

        // Check if a HomeCard already exists for this catalog
        homeCardRepository.findByCardTypeAndReferenceId("catalog", catalog.getId())
                .ifPresent(homeCard -> {
                    log.info("Home card already exists: {}", homeCard.getId());
                });

        // Create new HomeCard Entity
        HomeCardEntity homeCardEntity = homeCardProcessingUtil.createHomeCardFromCatalog(catalog);

        // Save the entity
        HomeCardEntity savedEntity = homeCardRepository.save(homeCardEntity);
        log.info("HomeCard created successfully with ID: {}", savedEntity.getId());
    }

    @Override
    public void updateHomeCard(CatalogEntity catalog) {
        log.info("Updating home card from catalog: {}", catalog.getTitle());

        // 1. Find the Home Card
        Optional<HomeCardEntity> existingHomeCard = homeCardRepository
                .findByCardTypeAndReferenceId("catalog", catalog.getId());

        // 2. Update
        if (existingHomeCard.isPresent()) {
            HomeCardEntity existingHomeCardEntity = getHomeCardEntity(catalog, existingHomeCard);

            homeCardRepository.save(existingHomeCardEntity);
        } else {

            // if isHomeCard is true, create a new home card
            if (catalog.isHomeCard()) {
                HomeCardEntity homeCardEntity = homeCardProcessingUtil.createHomeCardFromCatalog(catalog);

                // Save the entity
                HomeCardEntity savedEntity = homeCardRepository.save(homeCardEntity);
                log.info("HomeCard created successfully with ID: {}", savedEntity.getId());
            }
        }
    }

    private static HomeCardEntity getHomeCardEntity(CatalogEntity catalog, Optional<HomeCardEntity> existingHomeCard) {
        HomeCardEntity existingHomeCardEntity = existingHomeCard.get();
        if (catalog.getTitle() != null) existingHomeCardEntity.setTitle(catalog.getTitle());
        if (catalog.getLocation() != null) existingHomeCardEntity.setLocation(catalog.getLocation());
        if (catalog.getPriority() != null) existingHomeCardEntity.setPriority(catalog.getPriority());
        if (catalog.getCoverImageUrl() != null) existingHomeCardEntity.setCoverImageUrl(catalog.getCoverImageUrl());
        if (!catalog.isHomeCard()) existingHomeCardEntity.setActiveHomeCard(false);
        return existingHomeCardEntity;
    }

    @Override
    public void createHomeCardFromBlog(BlogEntity blog) {
        log.info("Creating HomeCard for blog: {}", blog.getTitle());

        // Check if a HomeCard already exists for this blog
        homeCardRepository.findByCardTypeAndReferenceId("blog", blog.getId())
                .ifPresent(existing -> {
                    log.info("HomeCard already exists for blog ID: {}", blog.getId());
                    return;
                });

        // Create new HomeCard entity
        HomeCardEntity homeCardEntity = homeCardProcessingUtil.createHomeCardFromBlog(blog); // Default priority 2

        // Save the entity
        HomeCardEntity savedEntity = homeCardRepository.save(homeCardEntity);
        log.info("HomeCard created successfully with ID: {}", savedEntity.getId());

        // Convert and return model
        homeCardProcessingUtil.convertModel(savedEntity);
    }
}
