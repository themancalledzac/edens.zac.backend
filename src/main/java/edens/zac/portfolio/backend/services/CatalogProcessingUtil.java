package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CatalogEntity;
import edens.zac.portfolio.backend.entity.ImageEntity;
import edens.zac.portfolio.backend.model.CatalogModel;
import edens.zac.portfolio.backend.model.CatalogUpdateDTO;
import edens.zac.portfolio.backend.model.ImageModel;
import edens.zac.portfolio.backend.repository.CatalogRepository;
import edens.zac.portfolio.backend.repository.ImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Component
@Slf4j
public class CatalogProcessingUtil {

    private final ImageProcessingUtil imageProcessingUtil;
    private final ImageRepository imageRepository;
    private final CatalogRepository catalogRepository;
    private final HomeService homeService;
    private final ImageService imageService;

    @Autowired
    public CatalogProcessingUtil(ImageProcessingUtil imageProcessingUtil, ImageRepository imageRepository, 
                               CatalogRepository catalogRepository, HomeService homeService, 
                               ImageService imageService) {
        this.imageProcessingUtil = imageProcessingUtil;
        this.imageRepository = imageRepository;
        this.catalogRepository = catalogRepository;
        this.homeService = homeService;
        this.imageService = imageService;
    }

    Set<ImageEntity> processNewImages(List<MultipartFile> images, CatalogEntity catalog, String catalogTitle) {
        Set<ImageEntity> catalogImages = new HashSet<>();

        if (images == null || images.isEmpty()) {
            return catalogImages;
        }

        log.info("Processing {} new uploaded images for catalog", images.size());
        for (MultipartFile image : images) {
            try {
                ImageEntity imageEntity = imageProcessingUtil.processAndSaveImage(image, "catalog", catalogTitle, catalog.getLocation());

                if (imageEntity == null) {
                    log.warn("Failed to process image {}", image.getOriginalFilename());
                    continue;
                }

                if (!imageEntity.getCatalogs().contains(catalog)) {
                    imageEntity.getCatalogs().add(catalog);
                    imageRepository.save(imageEntity);
                }

                catalogImages.add(imageEntity);

                if (catalog.getCoverImageUrl() == null && imageEntity.getImageUrlWeb() != null) {
                    catalog.setCoverImageUrl(imageEntity.getImageUrlWeb());
                    catalogRepository.save(catalog);
                }
            } catch (Exception e) {
                log.error("Error processing image for catalog: {}: {}", image.getOriginalFilename(), e.getMessage(), e);
            }
        }

        return catalogImages;
    }

    List<ImageEntity> sortImagesByCreateDate(Collection<ImageEntity> images) {
        List<ImageEntity> sortedImages = new ArrayList<>(images);
        sortedImages.sort((img1, img2) -> {
            if (img1.getCreateDate() == null) return 1;
            if (img2.getCreateDate() == null) return -1;
            return img1.getCreateDate().compareTo(img2.getCreateDate());
        });
        return sortedImages;
    }

    void handleHomeCard(CatalogEntity catalog, boolean isHomeCard) {
        if (isHomeCard) {
            try {
                log.info("{} Home card for catalog {}", catalog.getId() == null ? "Creating" : "Updating", catalog.getTitle());
                if (catalog.getId() == null) {
                    homeService.createHomeCardFromCatalog(catalog);
                } else {
                    homeService.updateHomeCard(catalog);
                }
            } catch (Exception e) {
                log.error("Error handling Home card for catalog: {}: {}", catalog.getId(), e.getMessage(), e);
                // We don't throw here to avoid failing the entire operation
            }
        }
    }

    List<ImageEntity> updateCatalogImages(CatalogEntity catalog, List<ImageModel> imagesToUpdate, List<Long> imagesToRemove) {
        List<ImageEntity> orderedImages = new ArrayList<>();

        if (imagesToUpdate != null && !imagesToUpdate.isEmpty()) {
            log.info("Updating {} images for catalog {}", imagesToUpdate.size(), catalog.getId());

            for (ImageModel imageModel : imagesToUpdate) {
                try {
                    ImageEntity updatedImage = imageService.updateImage(imageModel);

                    if (!updatedImage.getCatalogs().contains(catalog)) {
                        updatedImage.getCatalogs().add(catalog);
                        imageRepository.save(updatedImage);
                    }

                    orderedImages.add(updatedImage);
                } catch (Exception e) {
                    log.warn("Failed to update image with ID {}: {}", imageModel.getId(), e.getMessage());
                }
            }
        }

        if (imagesToRemove != null && !imagesToRemove.isEmpty()) {
            log.info("Removing {} images from catalog {}", imagesToRemove.size(), catalog.getId());

            orderedImages.removeIf(img -> imagesToRemove.contains(img.getId()));

            for (Long imageId : imagesToRemove) {
                imageRepository.findById(imageId).ifPresent(img -> {
                    img.getCatalogs().remove(catalog);
                    imageRepository.save(img);
                    log.debug("Removed catalog {} from image {}", catalog.getId(), img.getId());
                });
            }
        }

        return orderedImages;
    }




    /**
     * Converts a Catalog Entity to a Catalog Model
     *
     * @param catalogEntity CatalogEntity
     * @return CatalogModel
     */
    CatalogModel convertToCatalogModel(CatalogEntity catalogEntity) {
        // Convert the image entities to image models
        List<ImageModel> images = catalogEntity.getImages().stream()
                .filter(Objects::nonNull)
                .map(imageProcessingUtil::convertImageEntityToImageModel)
                .toList();

        return CatalogModel.builder()
                .id(catalogEntity.getId())
                .title(catalogEntity.getTitle())
                .location(catalogEntity.getLocation())
                .priority(catalogEntity.getPriority())
                .description(catalogEntity.getDescription())
                .coverImageUrl(catalogEntity.getCoverImageUrl())
                .people(catalogEntity.getPeople())
                .tags(catalogEntity.getTags())
                .slug(catalogEntity.getSlug())
                .date(catalogEntity.getDate())
                .isHomeCard(catalogEntity.isHomeCard())
                .images(images)
                .build();
    }

    /**
     * Updates Catalog Entity with changes from the user
     *
     * @param requestBody Request body from the user
     * @param catalogEntity Current catalog from the Database
     * @return Updated Catalog Entity
     */
    CatalogEntity updateCatalog(CatalogUpdateDTO requestBody, CatalogEntity catalogEntity) {
        if (requestBody.getTitle() != null) catalogEntity.setTitle(requestBody.getTitle());
        if (requestBody.getLocation() != null) catalogEntity.setLocation(requestBody.getLocation());
        if (requestBody.getPriority() != null) catalogEntity.setPriority(requestBody.getPriority());
        if (requestBody.getDescription() != null) catalogEntity.setDescription(requestBody.getDescription());
        if (requestBody.getCoverImageUrl() != null) catalogEntity.setCoverImageUrl(requestBody.getCoverImageUrl());
        if (requestBody.getPeople() != null) catalogEntity.setPeople(requestBody.getPeople());
        if (requestBody.getTags() != null) catalogEntity.setTags(requestBody.getTags());
        if (requestBody.getSlug() != null) catalogEntity.setSlug(requestBody.getSlug());
        if (requestBody.getDate() != null) catalogEntity.setDate(requestBody.getDate());
        if (requestBody.getIsHomeCard() != null) catalogEntity.setHomeCard(requestBody.getIsHomeCard());

        return catalogEntity;
    }
}
