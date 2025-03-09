package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CatalogEntity;
import edens.zac.portfolio.backend.entity.ImageEntity;
import edens.zac.portfolio.backend.model.CatalogCreateDTO;
import edens.zac.portfolio.backend.model.CatalogModel;
import edens.zac.portfolio.backend.repository.CatalogRepository;
import edens.zac.portfolio.backend.repository.ImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
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

    public CatalogServiceImpl(
            ImageRepository imageRepository,
            CatalogRepository catalogRepository,
            ImageProcessingUtil imageProcessingUtil,
            CatalogProcessingUtil catalogProcessingUtil, HomeService homeService) {
        this.imageRepository = imageRepository;
        this.catalogRepository = catalogRepository;
        this.imageProcessingUtil = imageProcessingUtil;
        this.catalogProcessingUtil = catalogProcessingUtil;
        this.homeService = homeService;
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

        // Part 6: Update the saved catalog with the final images collection
        savedCatalog.setImages(catalogImages);
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
        log.info("Fetching catalog with slug {}", slug);

        // Get the catalog entity with all its images
        Optional<CatalogEntity> catalogEntityOpt = catalogRepository.findBySlugWithImages(slug);

        if (catalogEntityOpt.isEmpty()) {
            log.warn("No catalog found with slug {}", slug);

            // Fallback to regular find
            Optional<CatalogEntity> catalogOpt = catalogRepository.findCatalogBySlug(slug);
            if (catalogOpt.isEmpty()) {
                log.warn("No catalog found with slug {}", slug);
                return null;
            }
            return catalogProcessingUtil.convertToCatalogModel(catalogOpt.get());
        }
        return catalogProcessingUtil.convertToCatalogModel(catalogEntityOpt.get());
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
        List<CatalogEntity> entities = catalogRepository.getAllCatalogs();
        return entities.stream()
                .map(catalogProcessingUtil::convertToCatalogModel)
                .collect(Collectors.toList());
    }
}
