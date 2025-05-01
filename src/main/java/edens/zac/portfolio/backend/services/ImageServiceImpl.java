package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CatalogEntity;
import edens.zac.portfolio.backend.entity.ImageEntity;
import edens.zac.portfolio.backend.model.ImageModel;
import edens.zac.portfolio.backend.model.ImageSearchModel;
import edens.zac.portfolio.backend.repository.CatalogRepository;
import edens.zac.portfolio.backend.repository.ImageRepository;
import edens.zac.portfolio.backend.specification.ImageSpecification;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Service
public class ImageServiceImpl implements ImageService {

    private final CatalogRepository catalogRepository;
    private final ImageRepository imageRepository;
    private final ImageProcessingUtil imageProcessingUtil;

    @Autowired
    public ImageServiceImpl(
            CatalogRepository catalogRepository,
            ImageRepository imageRepository,
            ImageProcessingUtil imageProcessingUtil) {
        this.imageRepository = imageRepository;
        this.imageProcessingUtil = imageProcessingUtil;
        this.catalogRepository = catalogRepository;

        log.info("ImageServiceImpl initialized");
    }

    /**
     * Processes and saves a single image file with associated metadata, categorizing it as either a blog or catalog image.
     *
     * @param file The MultipartFile containing the image to be processed
     * @param type The category type, must be either "catalog" or "blog"
     * @return A Map containing extracted metadata and processing results including image ID and URL
     * @throws IllegalArgumentException if the type parameter is not "catalog" or "blog"
     */
    @Override
    @Transactional
    public Map<String, String> postImages(MultipartFile file, String type) {
        if (type == null || (!type.equals("catalog") && !type.equals("blog"))) {
            throw new IllegalArgumentException("Type must be either 'catalog' or 'blog'");
        }

        try {
            log.info("Posting Image: {} of type: {}", file.getOriginalFilename(), type);

            // 1: Extract metadata
            // Return it even if we hit any exceptions later on
            Map<String, String> imageMetadata = imageProcessingUtil.extractImageMetadata(file);

            // 2: Determine catalog name
            String contextName = "default";

            // 3: add Catalog name to image if exists
            if ("catalog".equals(type) && imageMetadata.containsKey("data")) {
                String date = imageMetadata.get("date");
                if (date != null && date.length() >= 7) {
                    contextName = date.substring(0, 7).replace(':', '-');
                }
            }

            // 4: Process and Save the image using the shared utility
            ImageEntity savedImage = imageProcessingUtil.processAndSaveImage(file, type, contextName);
            log.info("Image processed and saved with ID: {}", savedImage.getId());

            // 5. Add ID to metadata response
            imageMetadata.put("id", savedImage.getId().toString());
            imageMetadata.put("imageUrlWeb", savedImage.getImageUrlWeb());

            return imageMetadata;
        } catch (Exception e) {
            log.error("Error in postImages: {}", e.getMessage(), e);

            // Even on error, try to return at least the basic metadata
            Map<String, String> errorMetadata = new HashMap<>();
            errorMetadata.put("title", file.getOriginalFilename());
            errorMetadata.put("error", e.getMessage());
            return errorMetadata;
        }
    }

    /**
     * Processes and associates multiple images with a specific catalog.
     *
     * @param images List of MultipartFile objects containing the images to be processed
     * @param catalogTitle The title of the catalog to associate with the images
     * @return List of ImageModel objects representing the processed images
     * @throws EntityNotFoundException if the specified catalog doesn't exist
     * @throws RuntimeException if there's an error during image processing
     */
    @Override
    @Transactional
    public List<ImageModel> postImagesForCatalog(List<MultipartFile> images, String catalogTitle) {
        try {
            if (images == null || images.isEmpty()) {
                log.warn("No images provided for catalog {}", catalogTitle);
                return Collections.emptyList();
            }
            String catalogTitleWithoutExtension = catalogTitle.replace("_", " ");

            // 1. Find the catalog
            CatalogEntity catalogEntity = catalogRepository.findByTitle(catalogTitleWithoutExtension)
                    .orElseThrow(() -> new EntityNotFoundException("Catalog not found with title: " + catalogTitleWithoutExtension));

            // 2. Process all images in a batch
            List<ImageEntity> newImages = imageProcessingUtil.batchProcessAndSaveImages(
                    images,
                    catalogTitleWithoutExtension,
                    catalogEntity
            );

            if (!newImages.isEmpty()) {
                catalogEntity.getImages().addAll(newImages);
            }
            return catalogEntity.getImages().stream()
                    .filter(Objects::nonNull)
                    .map(imageProcessingUtil::convertImageEntityToImageModel)
                    .toList();
        } catch (Exception e) {
            log.error("Error posting images for catalog {}: {}", catalogTitle, e.getMessage(), e);
            throw new RuntimeException("Failed to process images for catalog: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves an image by its unique identifier, including its associated catalogs.
     *
     * @param imageId The unique identifier of the image to retrieve
     * @return An Optional containing the ImageModel if found, or an empty Optional if not found
     */
    @Override
    @Transactional(readOnly = true) // use readOnly for fetch operations
    public Optional<ImageModel> getImageById(Long imageId) {
        return imageRepository.findByIdWithCatalogs(imageId)
                .map(imageProcessingUtil::convertImageEntityToImageModel);
    }

    /**
     * Retrieves all images associated with a specific catalog.
     *
     * @param catalogTitle The title of the catalog whose images are to be retrieved
     * @return A List of ImageModel objects representing all images in the specified catalog
     */
    @Override
    @Transactional(readOnly = true)
    public List<ImageModel> getAllImagesByCatalog(String catalogTitle) {
        return imageRepository.findImagesByCatalogTitle(catalogTitle).stream()
                .map(imageProcessingUtil::convertImageEntityToImageModel)
                .toList();
    }

    /**
     * Updates an existing image with new metadata and properties.
     *
     * @param imageModel The ImageModel containing updated information
     * @return The updated ImageEntity after persisting changes
     * @throws IllegalArgumentException if the image model or its ID is null
     * @throws EntityNotFoundException if no image exists with the specified ID
     */
    @Override
    @Transactional
    public ImageEntity updateImage(ImageModel imageModel) {
        if (imageModel == null || imageModel.getId() == null) {
            throw new IllegalArgumentException("Image model or ID cannot be null");
        }

        log.info("Updating image with ID: {}", imageModel.getId());

        Optional<ImageEntity> existingImageOpt = imageRepository.findByIdWithCatalogs(imageModel.getId());
        
        if (existingImageOpt.isEmpty()) {
            log.warn("Image with ID {} not found", imageModel.getId());
            throw new EntityNotFoundException("Image with ID " + imageModel.getId() + " not found");
        }
        
        ImageEntity existingImage = existingImageOpt.get();

        // Update only the properties we want to allow changing
        ImageEntity updatedImage = imageProcessingUtil.updateImageProperties(existingImage, imageModel);

        return imageRepository.save(updatedImage);
    }

    /**
     * Searches for images based on specified criteria.
     *
     * @param searchParams An ImageSearchModel containing the search criteria
     * @return A List of ImageModel objects matching the search criteria
     */
    @Override
    @Transactional(readOnly = true)
    public List<ImageModel> searchByData(ImageSearchModel searchParams) {

        List<ImageEntity> results = imageRepository.findAll(
                ImageSpecification.buildImageSpecification(searchParams)
        );

        return results.stream()
                .map(imageProcessingUtil::convertImageEntityToImageModel)
                .toList();
    }

    /**
     * Extracts metadata from an image file without saving it to the database.
     *
     * @param file The MultipartFile containing the image to extract metadata from
     * @return A Map containing extracted metadata or error information if extraction fails
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getImageMetadata(MultipartFile file) {
        if (file == null) {
            log.error("Null file provided to getImageMetadata");
            Map<String, String> errorMetadata = new HashMap<>();
            errorMetadata.put("error", "No file provided");
            return errorMetadata;
        }

        try {
            log.info("Extracting metadata from image: {}", file.getOriginalFilename());

            // Use the utility method to extract metadata
            // The utility method already handles errors and returns a complete metadata map
            return imageProcessingUtil.extractImageMetadata(file);
        } catch (Exception e) {
            log.error("Error extracting image metadata: {}", e.getMessage(), e);
            
            // Return basic metadata with error information on failure
            Map<String, String> errorMetadata = new HashMap<>();
            errorMetadata.put("title", file.getOriginalFilename());
            errorMetadata.put("error", e.getMessage());
            return errorMetadata;
        }
    }
}