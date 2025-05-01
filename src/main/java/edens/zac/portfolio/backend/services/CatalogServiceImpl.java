package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CatalogEntity;
import edens.zac.portfolio.backend.entity.ImageEntity;
import edens.zac.portfolio.backend.model.CatalogCreateDTO;
import edens.zac.portfolio.backend.model.CatalogModel;
import edens.zac.portfolio.backend.model.CatalogUpdateDTO;
import edens.zac.portfolio.backend.repository.CatalogRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class CatalogServiceImpl implements CatalogService {

    private static final int DEFAULT_PRIORITY = 3;
    private static final String DEFAULT_DESCRIPTION = "";
    private static final String DEFAULT_COVER_IMAGE_URL = "";
    private static final boolean DEFAULT_IS_HOME_CARD = false;
    private static final int DEFAULT_CATALOG_PAGE_PRIORITY = 3;


    private final CatalogRepository catalogRepository;
    private final ImageProcessingUtil imageProcessingUtil;
    private final CatalogProcessingUtil catalogProcessingUtil;
    private final HomeService homeService;

    public CatalogServiceImpl(
            CatalogRepository catalogRepository,
            ImageProcessingUtil imageProcessingUtil,
            CatalogProcessingUtil catalogProcessingUtil, HomeService homeService) {
        this.catalogRepository = catalogRepository;
        this.imageProcessingUtil = imageProcessingUtil;
        this.catalogProcessingUtil = catalogProcessingUtil;
        this.homeService = homeService;
    }


    /**
     * Creates a new catalog with the provided data and processes any images to be associated with it.
     *
     * @param catalogDTO Data Transfer Object containing catalog information including title,
     *                  description, priority, location, tags, people and other metadata
     * @param images List of image files to be processed, optimized and attached to the catalog
     * @return The created catalog as a CatalogModel with all associated data
     * @throws IllegalArgumentException if the catalog title is null or empty
     * @throws RuntimeException if there's an error during image processing or database operations
     */
    @Override
    @Transactional
    public CatalogModel createCatalogWithImages(CatalogCreateDTO catalogDTO, List<MultipartFile> images) {

        // Validate required fields
        validateCatalogData(catalogDTO.getTitle(), "creation");
        if (catalogDTO.getTitle() == null || catalogDTO.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Catalog title is required");
        }

        // 1: Initialize the catalog entity
        CatalogEntity catalogEntity = CatalogEntity.builder()
                .title(catalogDTO.getTitle())
                .location(catalogDTO.getLocation())
                .priority(getValueOrDefault(catalogDTO.getPriority(), DEFAULT_PRIORITY))
                .description(getValueOrDefault(catalogDTO.getDescription(), DEFAULT_DESCRIPTION))
                .coverImageUrl(getValueOrDefault(catalogDTO.getCoverImageUrl(), DEFAULT_COVER_IMAGE_URL))
                .people(getValueOrDefault(catalogDTO.getPeople(), new ArrayList<>()))
                .tags(getValueOrDefault(catalogDTO.getTags(), new ArrayList<>()))
                .slug(imageProcessingUtil.generateSlug(catalogDTO.getTitle()))
                .date(LocalDate.now())
                .isHomeCard(catalogDTO.getIsHomeCard() != null ? catalogDTO.getIsHomeCard() : DEFAULT_IS_HOME_CARD)
                .createdDate(LocalDateTime.now())
                .build();

        // 2: Save the catalog first to get an ID
        CatalogEntity savedCatalog = catalogRepository.save(catalogEntity);
        log.info("Catalog saved successfully with ID: {}", savedCatalog.getId());

        // 3: Process and upload new images, if any
        Set<ImageEntity> catalogImages = catalogProcessingUtil.processNewImages(images, savedCatalog, catalogDTO.getTitle());

        // 4: Add image order. Sort images by date if no explicit order is provided
        List<ImageEntity> sortedImages = catalogProcessingUtil.sortImagesByCreateDate(catalogImages);

        savedCatalog.setImages(sortedImages);
        savedCatalog = catalogRepository.save(savedCatalog);

        // 5: Create HomeCard if requested
        catalogProcessingUtil.handleHomeCard(savedCatalog, catalogDTO.getIsHomeCard());

        // 6: Convert and return the catalog model
        return catalogProcessingUtil.convertToCatalogModel(savedCatalog);
    }

    /**
     * Updates an existing catalog with new information and manages its associated images.
     *
     * @param requestBody DTO containing the updated catalog data, including ID, title, metadata, images to update
     *                    and images to remove
     * @return The updated CatalogModel with all changes applied
     * @throws EntityNotFoundException if no catalog exists with the specified ID
     * @throws RuntimeException if there's an error during the update process
     */
    @Transactional
    @Override
    public CatalogModel updateCatalog(CatalogUpdateDTO requestBody) {
        return catalogProcessingUtil.handleExceptions("update catalog", () -> {

            // 1. Find the catalog
            CatalogEntity catalogEntity = catalogRepository.findCatalogById(requestBody.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Catalog not found with ID " + requestBody.getId()));

            // 2. Update basic properties
            validateCatalogData(requestBody.getTitle(), "update");
            CatalogEntity updateCatalogEntity = catalogProcessingUtil.updateCatalog(requestBody, catalogEntity);

            // 3. If images are provided, update the entire list to maintain order
            if (requestBody.getImages() != null && !requestBody.getImages().isEmpty() ||
                    requestBody.getImagesToRemove() != null && !requestBody.getImagesToRemove().isEmpty()) {

                List<ImageEntity> orderedImages = catalogProcessingUtil.updateCatalogImages(
                        updateCatalogEntity,
                        requestBody.getImages(),
                        requestBody.getImagesToRemove()
                );

                updateCatalogEntity.setImages(orderedImages);
            }


            // 4. Update catalog in database with all changes
            CatalogEntity savedCatalog = catalogRepository.save(updateCatalogEntity);

            // 5. Update HomeCard if requested
            if (Boolean.TRUE.equals(requestBody.getIsHomeCard())) {
                try {
                    log.info("Updating Home card for catalog {}", savedCatalog.getTitle());
                    homeService.updateHomeCard(savedCatalog);
                } catch (Exception e) {
                    log.error("Error updating Home card for catalog: {}: {}", savedCatalog.getId(), e.getMessage(), e);
                }
            }

            return catalogProcessingUtil.convertToCatalogModel(savedCatalog);
        });
    }

    /**
     * Retrieves a catalog by its unique slug identifier.
     *
     * @param slug The URL-friendly string identifier for the catalog
     * @return A CatalogModel representing the found catalog with all its images, or null if not found
     * @throws RuntimeException if there's an error during the retrieval process, handled by handleExceptions
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogModel> getCatalogBySlug(String slug) {
        return catalogProcessingUtil.handleExceptions("get catalog by slug", () -> {
            log.info("Fetching catalog with slug {}", slug);
            return catalogRepository.findBySlugWithImages(slug)
                    .map(catalogEntity -> {
                        log.info("Catalog found for slug {} with {} images", slug, catalogEntity.getImages().size());

                        // filter out any null images
                        catalogEntity.setImages(catalogEntity.getImages().stream()
                                .filter(Objects::nonNull)
                                .toList());

                        return catalogProcessingUtil.convertToCatalogModel(catalogEntity);
                    });
        });
    }

    /**
     * Retrieves a catalog by its unique numeric ID.
     *
     * @param id The unique identifier of the catalog to retrieve
     * @return An Optional containing the CatalogModel if found, or an empty Optional if not found
     * @throws RuntimeException if there's an error during the retrieval process
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogModel> getCatalogById(Long id) {
        log.info("Getting catalog by Id {}", id);

        return catalogRepository.findByIdWithImages(id)
                .or(() -> catalogRepository.findCatalogById(id))
                .map(catalogProcessingUtil::convertToCatalogModel);
    }

    /**
     * Retrieves all catalogs in the system, ordered by priority.
     *
     * @return A List of CatalogModel objects representing all available catalogs
     * @throws RuntimeException if there's an error during the retrieval process, handled by handleExceptions
     */
    @Override
    @Transactional(readOnly = true)
    public List<CatalogModel> getAllCatalogs() {
        return catalogProcessingUtil.handleExceptions("get all catalogs", () -> {
            List<CatalogEntity> entities = catalogRepository.getAllCatalogs(DEFAULT_CATALOG_PAGE_PRIORITY);
            return entities.stream()
                    .map(catalogProcessingUtil::convertToCatalogModel)
                    .toList();
        });
    }

    private void validateCatalogData(String title, String operation) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Catalog title is required for " + operation);
        }
    }

    private <T> T getValueOrDefault(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }
}