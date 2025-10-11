package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionHomeCardEntity;
import edens.zac.portfolio.backend.entity.ImageContentBlockEntity;
import edens.zac.portfolio.backend.model.HomeCardModel;
import edens.zac.portfolio.backend.repository.ContentBlockRepository;
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
    private final ContentBlockRepository contentBlockRepository;

    public HomeServiceImpl(ContentCollectionHomeCardRepository homeCardRepository,
                          HomeProcessingUtil homeCardProcessingUtil,
                          ContentBlockRepository contentBlockRepository) {
        this.homeCardRepository = homeCardRepository;
        this.homeCardProcessingUtil = homeCardProcessingUtil;
        this.contentBlockRepository = contentBlockRepository;
    }

    /**
     * Helper method to get cover image URL from collection's coverImageBlockId
     */
    private String getCoverImageUrl(ContentCollectionEntity collection) {
        if (collection.getCoverImageBlockId() == null) {
            return null;
        }

        return contentBlockRepository.findById(collection.getCoverImageBlockId())
                .filter(block -> block instanceof ImageContentBlockEntity)
                .map(block -> ((ImageContentBlockEntity) block).getImageUrlWeb())
                .orElse(null);
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
    public void upsertHomeCardForCollection(ContentCollectionEntity collection,
                                            boolean enabled,
                                            Integer priority,
                                            String text) {
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
            entity.setCoverImageUrl(getCoverImageUrl(collection));
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
                    String coverImageUrl = getCoverImageUrl(collection);
                    if (coverImageUrl != null) {
                        entity.setCoverImageUrl(coverImageUrl);
                    }
                    // Ensure cardType stays in sync with collection type
                    if (collection.getType() != null) {
                        entity.setCardType(collection.getType().name());
                    }
                    homeCardRepository.save(entity);
                });
    }
}
