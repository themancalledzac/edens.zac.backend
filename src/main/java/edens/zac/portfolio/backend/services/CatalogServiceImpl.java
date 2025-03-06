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

    public CatalogServiceImpl(
            ImageRepository imageRepository,
            CatalogRepository catalogRepository,
            ImageProcessingUtil imageProcessingUtil,
            CatalogProcessingUtil catalogProcessingUtil) {
        this.imageRepository = imageRepository;
        this.catalogRepository = catalogRepository;
        this.imageProcessingUtil = imageProcessingUtil;
        this.catalogProcessingUtil = catalogProcessingUtil;
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
                .slug(catalogDTO.getSlug() != null && !catalogDTO.getSlug().isEmpty() ?
                        catalogDTO.getSlug() : imageProcessingUtil.generateSlug(catalogDTO.getTitle()))
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

        // Part 7: Convert and return the catalog model
        return catalogProcessingUtil.convertToCatalogModel(savedCatalog);
    }

    @Override
    public CatalogModel getCatalogBySlug(String slug) {
        log.info("Getting catalog by slug {}", slug);

        // Get the catalog entity with all its images
        Optional<CatalogEntity> catalogEntityOpt = catalogRepository.findBySlugWIthImages(slug);


        return null;
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
    public List<CatalogModel> getMainPageCatalogList() {
        return List.of();
    }

//    @Override
//    @Transactional
//    public String createCatalog(CatalogModalDTO catalog) {
//        // TODO: Make optional number of additions later on, such as location, stravaLink, etc
//        CatalogEntity catalogEntity = CatalogEntity.builder()
//                .name(catalog.getName())
//                .priority(catalog.getPriority())
//                .mainCatalog(catalog.getMainCatalog())
//                .build();
//        CatalogEntity savedCatalog = catalogRepository.save(catalogEntity);
//        return savedCatalog.getName();
//    }

//    @Override
//    public List<CatalogModel> catalogSearch(String search) {
//
//        // TODO: Search Catalog DB, return List of matching Catalogs
//        //  - such as (search="nyc") we return ["nyc_2011", "nyc_2024];
//        return null;
//    }
//
//    @Override
//    public CatalogModel updateCatalog(CatalogModel catalog) {
//
//        // TODO: Update Catalog
//        return null;
//    }
//
//    @Override
//    public CatalogImagesDTO updateCatalogWithImages(CatalogImagesDTO catalogWithImages) {
//
//        // TODO: Figure out a Update strategy. probably...
//        //  - 2db calls, 1 updates catalog, the next loops through each image and updates accordingly, OR, we update each image all at once in one db call. might be more difficult, but probably the more correct option.
//        return null;
//    }

//    @Override
//    @Transactional
//    public List<CatalogModel> getMainPageCatalogList() {
//
//        List<CatalogModalDTO> mainCatalogs = catalogRepository.findMainCatalogs();
//        return mainCatalogs.stream().map(catalogProcessingUtil::convertToCatalogModel)
//                .collect(Collectors.toList());
//    }

//    private CatalogModel catalogConvertToModel(CatalogModalDTO catalogEntity) {
//
//        ImageModel catalogImageConverted = null;
//        if (catalogEntity.getImageMainTitle() != null) {
//            Optional<ImageEntity> catalogImageOpt = imageRepository.findByTitle(catalogEntity.getImageMainTitle());
//            catalogImageConverted = catalogImageOpt.map(this::convertToModalImage)
//                    .orElse(null);
//        }
//        return new CatalogModel(catalogEntity.getId(), catalogEntity.getName(), catalogImageConverted, catalogEntity.getPriority());
//
//    }

//    // This is a duplicate of ImageServiceImpl. It needs to be moved elsewhere
//    private ImageModel convertToModalImage(ImageEntity image) {
//        List<String> catalogNames = image.getCatalogs().stream().map(CatalogEntity::getName).collect(Collectors.toList());
//
//        ImageModel modalImage = new ImageModel(); //  is not public in 'edens.zac.portfolio.backend.model.ModalImage'. Cannot be accessed from outside package
//        modalImage.setTitle(image.getTitle());
//        modalImage.setImageWidth(image.getImageWidth());
//        modalImage.setImageHeight(image.getImageHeight());
//        modalImage.setIso(image.getIso());
//        modalImage.setAuthor(image.getAuthor());
//        modalImage.setRating(image.getRating());
//        modalImage.setFStop(image.getFStop());
//        modalImage.setLens(image.getLens());
//        modalImage.setCatalog(catalogNames);
//        modalImage.setBlackAndWhite(image.getBlackAndWhite());
//        modalImage.setShutterSpeed(image.getShutterSpeed());
//        modalImage.setRawFileName(image.getRawFileName());
//        modalImage.setCamera(image.getCamera());
//        modalImage.setFocalLength(image.getFocalLength());
//        modalImage.setLocation(image.getLocation());
//        modalImage.setImageUrlWeb(image.getImageUrlWeb());
//        modalImage.setImageUrlSmall(image.getImageUrlSmall());
//        modalImage.setImageUrlRaw(image.getImageUrlRaw());
//        modalImage.setCreateDate(image.getCreateDate());
//        modalImage.setUpdateDate(image.getUpdateDate());
//        modalImage.setId(image.getId());
//        return modalImage;
//    }
}
