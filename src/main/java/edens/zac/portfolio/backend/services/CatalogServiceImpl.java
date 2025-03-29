package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CatalogEntity;
import edens.zac.portfolio.backend.entity.HomeCardEntity;
import edens.zac.portfolio.backend.entity.ImageEntity;
import edens.zac.portfolio.backend.model.CatalogCreateDTO;
import edens.zac.portfolio.backend.model.CatalogModel;
import edens.zac.portfolio.backend.model.CatalogUpdateDTO;
import edens.zac.portfolio.backend.model.ImageModel;
import edens.zac.portfolio.backend.repository.CatalogRepository;
import edens.zac.portfolio.backend.repository.HomeCardRepository;
import edens.zac.portfolio.backend.repository.ImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CatalogServiceImpl implements CatalogService {

    private final ImageRepository imageRepository;
    private final CatalogRepository catalogRepository;
    private final ImageProcessingUtil imageProcessingUtil;
    private final CatalogProcessingUtil catalogProcessingUtil;
    private final HomeService homeService;
    private final HomeCardRepository homeCardRepository;

    public CatalogServiceImpl(
            ImageRepository imageRepository,
            CatalogRepository catalogRepository,
            ImageProcessingUtil imageProcessingUtil,
            CatalogProcessingUtil catalogProcessingUtil, HomeService homeService, HomeCardRepository homeCardRepository) {
        this.imageRepository = imageRepository;
        this.catalogRepository = catalogRepository;
        this.imageProcessingUtil = imageProcessingUtil;
        this.catalogProcessingUtil = catalogProcessingUtil;
        this.homeService = homeService;
        this.homeCardRepository = homeCardRepository;
    }

    @Override
    @Transactional
    public CatalogModel createCatalogWithImages(CatalogCreateDTO catalogDTO, List<MultipartFile> images) {

        // Part 1: Initialize the catalog entity
        CatalogEntity catalogEntity = CatalogEntity.builder()
                .title(catalogDTO.getTitle())
                .location(catalogDTO.getLocation())
                .priority(catalogDTO.getPriority() != null ? catalogDTO.getPriority() : 3) // Default to the lowest priority
                .coverImageUrl(catalogDTO.getCoverImageUrl())
                .people(catalogDTO.getPeople())
                .tags(catalogDTO.getTags())
                .slug(imageProcessingUtil.generateSlug(catalogDTO.getTitle()))
                .date(LocalDate.now()) // Set current date
                .createdDate(LocalDateTime.now())
                .build();

        // Part 2: Save the catalog first to get an ID
        CatalogEntity savedCatalog = catalogRepository.save(catalogEntity);
        log.info("Catalog saved successfully with ID: {}", savedCatalog.getId());


        // Part 3: Initialize image collection
        Set<ImageEntity> catalogImages = new HashSet<>();

        // Part 4: Process existing images if IDs were provided
        if (catalogDTO.getExistingImageIds() != null && !catalogDTO.getExistingImageIds().isEmpty()) {
            log.info("Processing {} existing images for catalog", catalogDTO.getExistingImageIds().size());

            // Get all eisting images in one query
            Set<ImageEntity> existingImages = catalogDTO.getExistingImageIds().stream()
                    .map(imageRepository::findById)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());

            // Update both sides of the relationship
            for (ImageEntity image : existingImages) {
                // Add the catalog to the image's catalog collection
                image.getCatalogs().add(savedCatalog);
                imageRepository.save(image);

                // If no cover image is set yet, use the image URL from this image
                if (savedCatalog.getCoverImageUrl() == null && image.getImageUrlWeb() != null) {
                    savedCatalog.setCoverImageUrl(image.getImageUrlWeb());
                    savedCatalog = catalogRepository.save(savedCatalog);
                }
            }

            // Add all existing images to the catalog's images collection
            catalogImages.addAll(existingImages);
        }


        // Part 5: Process and upload new images, if any
        if (images != null && !images.isEmpty()) {
            log.info("Processing {} new uploaded images for catalog", images.size());
            for (MultipartFile image : images) {
                try {
                    // Process Image using the catalog name as the context
                    ImageEntity imageEntity = imageProcessingUtil.processAndSaveImage(image, "catalog", catalogDTO.getTitle());

                    if (imageEntity == null) {
                        log.warn("Failed to process image {}", image.getOriginalFilename());
                        continue;
                    }

                    // Ensure the image has this catalog in its catalogs collection
                    if (!imageEntity.getCatalogs().contains(savedCatalog)) {
                        imageEntity.getCatalogs().add(savedCatalog);
                        imageRepository.save(imageEntity);
                    }

                    catalogImages.add(imageEntity);

                    // If no cover image is set yet, use this one
                    if (savedCatalog.getCoverImageUrl() == null && imageEntity.getImageUrlWeb() != null) {
                        savedCatalog.setCoverImageUrl(imageEntity.getImageUrlWeb());
                        savedCatalog = catalogRepository.save(savedCatalog);
                    }
                } catch (
                        Exception e) {
                    log.error("Error processing image for catalog: {}: {}", image.getOriginalFilename(), e.getMessage(), e);
                    // Continue with other images
                }
            }
        }

        // Part 6: Add image order
        // Sort images by date if no explicit order is provided
        List<ImageEntity> sortedImages = new ArrayList<>(catalogImages);
        sortedImages.sort((img1, img2) -> {
            if (img1.getCreateDate() == null) return 1;
            if (img2.getCreateDate() == null) return -1;
            return img1.getCreateDate().compareTo(img2.getCreateDate());
        });

        savedCatalog.getImages().clear();
        savedCatalog.getImages().addAll(sortedImages);
        savedCatalog = catalogRepository.save(savedCatalog);

        // Part 7: Create HomeCard if requested
        if (catalogDTO.getCreateHomeCard() != null && catalogDTO.getCreateHomeCard()) {
            try {
                log.info("Creating Home card for catalog {}", savedCatalog.getTitle());
                homeService.createHomeCardFromCatalog(savedCatalog, catalogDTO.getPriority());
            } catch (Exception e) {
                log.error("Error creating Home card for catalog: {}: {}", savedCatalog.getId(), e.getMessage(), e);
            }
        }

        // Part 8: Convert and return the catalog model
        return catalogProcessingUtil.convertToCatalogModel(savedCatalog);
    }

    @Override
    public CatalogModel getCatalogBySlug(String slug) {
        try {
            log.info("Fetching catalog with slug {}", slug);
            Optional<CatalogEntity> catalogOpt = catalogRepository.findBySlugWithImages(slug);
            if (catalogOpt.isEmpty()) {
                log.info("No catalog found for slug {}", slug);
                return null;
            }
            CatalogEntity catalogEntity = catalogOpt.get();
            log.info("Catalog found for slug {} with {} images", slug, catalogEntity.getImages().size());

            // filter out any null images
            catalogEntity.setImages(catalogEntity.getImages().stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));

            return catalogProcessingUtil.convertToCatalogModel(catalogEntity);

        } catch (Exception e) {
            log.error("Error fetching catalog for slug: {}", slug, e);
            return null;
        }
    }

    @Transactional
    @Override
    public CatalogModel updateCatalog(CatalogUpdateDTO requestBody) {

        // 1. Find the catalog
        CatalogEntity catalogEntity = catalogRepository.findCatalogById(requestBody.getId())
                .orElseThrow(() -> new RuntimeException("No catalog found for slug " + requestBody.getSlug()));

        // 2. Update basic properties
        if (requestBody.getTitle() != null) catalogEntity.setTitle(requestBody.getTitle());
        if (requestBody.getLocation() != null) catalogEntity.setLocation(requestBody.getLocation());
        if (requestBody.getPriority() != null) catalogEntity.setPriority(requestBody.getPriority());
        if (requestBody.getCoverImageUrl() != null) catalogEntity.setCoverImageUrl(requestBody.getCoverImageUrl());
        if (requestBody.getPeople() != null) catalogEntity.setPeople(requestBody.getPeople());
        if (requestBody.getTags() != null) catalogEntity.setTags(requestBody.getTags());
        if (requestBody.getSlug() != null) catalogEntity.setSlug(requestBody.getSlug());
        if (requestBody.getDate() != null) catalogEntity.setDate(requestBody.getDate());

        // Part 3: Store original images for relationship management
        List<ImageEntity> originalImages = catalogEntity.getImages().stream()
                .filter(Objects::nonNull)
                .toList();

        // Part 4: Update images based on request
        if (requestBody.getImages() != null && !requestBody.getImages().isEmpty()) {

            // Clear current images
            catalogEntity.getImages().clear();

            // Force a flush to apply changes
            catalogRepository.flush();

            for (ImageModel image : requestBody.getImages()) {
                // Get the managed entity from repository
                ImageEntity imgEntity = imageRepository.findById(image.getId())
                        .orElseThrow(() -> new RuntimeException("No image found for id " + image.getId()));

                // Update image properties
                if (image.getTitle() != null) imgEntity.setTitle(image.getTitle());
                if (image.getLocation() != null) imgEntity.setLocation(image.getLocation());
                if (image.getRating() != null) imgEntity.setRating(image.getRating());

                // Add to catalog
                catalogEntity.getImages().add(imgEntity);

                // Update other side of relationship
                imgEntity.getCatalogs().add(catalogEntity);

                // Save updated image
                imageRepository.save(imgEntity);
            }
        }

        // 5. Handle images to remove
        if (requestBody.getImagesToRemove() != null) {
            for (Long imageId : requestBody.getImagesToRemove()) {
                // Remove fom catalog's images if present
                catalogEntity.getImages().removeIf(img -> img.getId().equals(imageId));

                // Update other side of relationship
                imageRepository.findById(imageId).ifPresent(img -> {
                    img.getCatalogs().remove(catalogEntity);
                    imageRepository.save(img);
                });
            }
        }

        // 5. For remaining original images not in the updated list, clean up relationship
        //  - Finds any images in original collection, but not in the updated collection.
        for (ImageEntity originalImage : originalImages) {
            if (originalImage != null && !catalogEntity.getImages().contains(originalImage)) {
                originalImage.getCatalogs().remove(catalogEntity);
                imageRepository.save(originalImage);
            }
        }

        // 7. Update catalog in database with all changes.
        CatalogEntity savedCatalog = catalogRepository.save(catalogEntity);

        // 8. Update HomeCard if requested
        if (requestBody.getUpdateHomeCard() != null && requestBody.getUpdateHomeCard()) {

            // Find existing home card first
            Optional<HomeCardEntity> existingCard = homeCardRepository
                    .findByCardTypeAndReferenceId("catalog", savedCatalog.getId());

            if (existingCard.isPresent()) {
                HomeCardEntity existingCardEntity = existingCard.get();
                existingCardEntity.setTitle(savedCatalog.getTitle());
                existingCardEntity.setLocation(savedCatalog.getLocation());
                existingCardEntity.setPriority(savedCatalog.getPriority());
                existingCardEntity.setCoverImageUrl(savedCatalog.getCoverImageUrl());
                existingCardEntity.setDate(savedCatalog.getDate() != null ? savedCatalog.getDate().toString() : null);
                homeCardRepository.save(existingCardEntity);
            } else {
                homeService.createHomeCardFromCatalog(savedCatalog, savedCatalog.getPriority());
            }

        }

        return catalogProcessingUtil.convertToCatalogModel(savedCatalog);
    }

    @Override
    public CatalogModel getCatalogById(Long id) {
        log.info("Getting catalog by Id {}", id);

        // Get the catalog entity with all its images
        Optional<CatalogEntity> catalogEntityOpt = catalogRepository.findByIdWithImages(id);

        if (catalogEntityOpt.isEmpty()) {
            log.warn("Catalog with ID {} not found with custom query", id);

            // Fallback to regular find
            Optional<CatalogEntity> catalogOpt = catalogRepository.findCatalogById(id);
            if (catalogOpt.isEmpty()) {
                log.warn("Catalog with ID {} not found", id);
                return null;
            }
            return catalogProcessingUtil.convertToCatalogModel(catalogOpt.get());
        }

        return catalogProcessingUtil.convertToCatalogModel(catalogEntityOpt.get());
    }

    @Override
    public List<CatalogModel> getAllCatalogs() {
        Integer catalogPagePriority = 3;

        // TODO: Add error handling at this step
        List<CatalogEntity> entities = catalogRepository.getAllCatalogs(catalogPagePriority);
        return entities.stream()
                .map(catalogProcessingUtil::convertToCatalogModel)
                .collect(Collectors.toList());
    }
}
