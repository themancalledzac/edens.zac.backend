package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CatalogEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionHomeCardEntity;
import edens.zac.portfolio.backend.model.HomeCardModel;
import edens.zac.portfolio.backend.repository.ContentCollectionHomeCardRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HomeServiceImpl implements HomeService {

    private final ContentCollectionHomeCardRepository homeCardRepository;
    private final HomeProcessingUtil homeCardProcessingUtil;

    public HomeServiceImpl(ContentCollectionHomeCardRepository homeCardRepository, HomeProcessingUtil homeCardProcessingUtil) {
        this.homeCardRepository = homeCardRepository;
        this.homeCardProcessingUtil = homeCardProcessingUtil;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HomeCardModel> getHomePage() {
        return getHomePage(2);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HomeCardModel> getHomePage(int maxPriority) {
        List<ContentCollectionHomeCardEntity> entities = homeCardRepository.getHomePage(maxPriority);
        return entities.stream()
                .map(homeCardProcessingUtil::convertModel)
                .collect(Collectors.toList());
    }

    @Override
    public void createHomeCardFromCatalog(CatalogEntity catalog) {
        log.info("Creating home card from catalog: {}", catalog.getTitle());

        // Check if a HomeCard already exists for this catalog
        homeCardRepository.findByCardTypeAndReferenceId("catalog", catalog.getId())
                .ifPresent(homeCard -> log.info("Home card already exists: {}", homeCard.getId()));

        // Create new HomeCard Entity
        ContentCollectionHomeCardEntity homeCardEntity = homeCardProcessingUtil.createHomeCardFromCatalog(catalog);

        // Save the entity
        ContentCollectionHomeCardEntity savedEntity = homeCardRepository.save(homeCardEntity);
        log.info("HomeCard created successfully with ID: {}", savedEntity.getId());
    }

    @Override
    public void updateHomeCard(CatalogEntity catalog) {
        log.info("Updating home card from catalog: {}", catalog.getTitle());

        Optional<ContentCollectionHomeCardEntity> existingHomeCard = homeCardRepository
                .findByCardTypeAndReferenceId("catalog", catalog.getId());

        if (existingHomeCard.isPresent()) {
            ContentCollectionHomeCardEntity existingHomeCardEntity = getHomeCardEntity(catalog, existingHomeCard);
            homeCardRepository.save(existingHomeCardEntity);
        } else if (catalog.isHomeCard()) {
            ContentCollectionHomeCardEntity homeCardEntity = homeCardProcessingUtil.createHomeCardFromCatalog(catalog);
            ContentCollectionHomeCardEntity savedEntity = homeCardRepository.save(homeCardEntity);
            log.info("HomeCard created successfully with ID: {}", savedEntity.getId());
        }
    }

    @Override
    public void upsertHomeCardForCollection(ContentCollectionEntity collection,
                                            boolean enabled,
                                            Integer priority,
                                            String text,
                                            String coverImageUrl) {
        Optional<ContentCollectionHomeCardEntity> existingOpt = homeCardRepository
                .findByReferenceId(collection.getId());

        if (enabled) {
            ContentCollectionHomeCardEntity entity = existingOpt.orElseGet(ContentCollectionHomeCardEntity::new);
            // Always ensure cardType matches the collection type
            entity.setCardType(collection.getType() != null ? collection.getType().name() : null);
            if (entity.getId() == null) {
                entity.setReferenceId(collection.getId());
                entity.setCreatedDate(collection.getCreatedAt());
            }
            entity.setActiveHomeCard(true);
            entity.setTitle(collection.getTitle());
            entity.setSlug(collection.getSlug());
            entity.setLocation(collection.getLocation());
            entity.setDate(collection.getCollectionDate() != null
                    ? collection.getCollectionDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    : null);
            entity.setPriority(priority != null ? priority : collection.getPriority());
            entity.setCoverImageUrl(coverImageUrl != null ? coverImageUrl : collection.getCoverImageUrl());
            entity.setText(text);
            homeCardRepository.save(entity);
        } else {
            existingOpt.ifPresent(entity -> {
                entity.setActiveHomeCard(false);
                homeCardRepository.save(entity);
            });
        }
    }

    @Override
    public void syncHomeCardOnCollectionUpdate(ContentCollectionEntity collection) {
        homeCardRepository.findByReferenceId(collection.getId())
                .ifPresent(entity -> {
                    entity.setTitle(collection.getTitle());
                    entity.setSlug(collection.getSlug());
                    entity.setLocation(collection.getLocation());
                    entity.setDate(collection.getCollectionDate() != null
                            ? collection.getCollectionDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                            : null);
                    if (collection.getPriority() != null) {
                        entity.setPriority(collection.getPriority());
                    }
                    if (collection.getCoverImageUrl() != null) {
                        entity.setCoverImageUrl(collection.getCoverImageUrl());
                    }
                    // Ensure cardType stays in sync with collection type
                    if (collection.getType() != null) {
                        entity.setCardType(collection.getType().name());
                    }
                    homeCardRepository.save(entity);
                });
    }

    private static ContentCollectionHomeCardEntity getHomeCardEntity(CatalogEntity catalog, Optional<ContentCollectionHomeCardEntity> existingHomeCard) {
        ContentCollectionHomeCardEntity existingHomeCardEntity = existingHomeCard.get();
        if (catalog.getTitle() != null) existingHomeCardEntity.setTitle(catalog.getTitle());
        if (catalog.getLocation() != null) existingHomeCardEntity.setLocation(catalog.getLocation());
        if (catalog.getPriority() != null) existingHomeCardEntity.setPriority(catalog.getPriority());
        if (catalog.getCoverImageUrl() != null) existingHomeCardEntity.setCoverImageUrl(catalog.getCoverImageUrl());
        if (!catalog.isHomeCard()) existingHomeCardEntity.setActiveHomeCard(false);
        return existingHomeCardEntity;
    }
}
