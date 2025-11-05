package edens.zac.portfolio.backend.services;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.xmp.XmpDirectory;
import edens.zac.portfolio.backend.entity.*;
import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.repository.*;
import edens.zac.portfolio.backend.repository.ContentTagRepository;
import edens.zac.portfolio.backend.repository.ContentPersonRepository;
import edens.zac.portfolio.backend.services.validator.ContentImageUpdateValidator;
import edens.zac.portfolio.backend.services.validator.ContentValidator;
import edens.zac.portfolio.backend.types.ContentType;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.model.TagUpdate;
import edens.zac.portfolio.backend.model.PersonUpdate;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for processing content.
 * Handles conversion between entities and models, content validation,
 * and specialized processing for different content types.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContentProcessingUtil {

    // Dependencies for S3 upload and repositories
    private final AmazonS3 amazonS3;
    private final ContentRepository contentRepository;
    private final ContentCameraRepository contentCameraRepository;
    private final ContentLensRepository contentLensRepository;
    private final ContentFilmTypeRepository contentFilmTypeRepository;
    private final ContentTagRepository contentTagRepository;
    private final ContentPersonRepository contentPersonRepository;
    private final CollectionContentRepository collectionContentRepository;
    private final ContentImageUpdateValidator contentImageUpdateValidator;
    private final ContentValidator contentValidator;

    @Value("${aws.portfolio.s3.bucket}")
    private String bucketName;

    @Value("${cloudfront.domain}")
    private String cloudfrontDomain;

    // Default values for image metadata
    public static final class DEFAULT {
        public static final String AUTHOR = "Zechariah Edens";
    }


    /**
     * Convert a ContentEntity to its corresponding ContentModel based on type.
     * This version does not have collection-specific metadata (orderIndex, caption, visible).
     * Use the overloaded version that accepts CollectionContentEntity for full metadata.
     *
     * @param entity The content entity to convert
     * @return The corresponding content model
     */
    public ContentModel convertRegularContentEntityToModel(ContentEntity entity) {
        if (entity == null) {
            return null;
        }

        if (entity.getContentType() == null) {
            log.error("Unknown content type: null");
            throw new IllegalArgumentException("Unknown content type: null");
        }

        // Un-proxy the entity first to avoid ClassCastException with HibernateProxy
        entity = unproxyContentEntity(entity);

        return switch (entity.getContentType()) {
            case IMAGE -> convertImageToModel((ContentImageEntity) entity);
            case TEXT -> convertTextToModel((ContentTextEntity) entity);
            case GIF -> convertGifToModel((ContentGifEntity) entity);
            case COLLECTION -> null;
        };
    }

    /**
     * Resolve a Hibernate proxy to its actual entity type.
     * This is necessary when dealing with JOINED inheritance strategy where proxies
     * may not be properly initialized to the correct subclass type.
     * 
     * Optimized to skip processing if entity is already properly initialized (not a proxy).
     *
     * @param entity The entity (may be a proxy)
     * @return The actual entity instance (not a proxy)
     */
    public ContentEntity unproxyContentEntity(ContentEntity entity) {
        if (entity == null) {
            return null;
        }
        
        // Optimize: Skip if already properly initialized (not a proxy)
        // This avoids unnecessary work when entities are bulk-loaded
        if (!(entity instanceof HibernateProxy)) {
            return entity;
        }
        
        // Use Hibernate.unproxy() to get the actual implementation
        // This works with JOINED inheritance strategy to get the correct subclass
        ContentEntity unproxied = (ContentEntity) Hibernate.unproxy(entity);
        
        // If still a proxy or not the right type, reload from repository
        if (unproxied instanceof HibernateProxy) {
            log.debug("Entity {} is still a proxy after unproxy, reloading from repository", entity.getId());
            ContentEntity reloaded = contentRepository.findById(entity.getId())
                    .orElse(null);
            
            if (reloaded != null) {
                return reloaded;
            }
            
            log.warn("Could not reload entity {} from repository", entity.getId());
            return unproxied; // Return unproxied version even if still a proxy
        }
        
        return unproxied;
    }

    /**
     * Convert a ContentEntity to its corresponding ContentModel with join table metadata.
     * This version populates collection-specific fields (orderIndex, visible) from the join table.
     *
     * @param entity            The join table entry (CollectionContentEntity) containing the content and metadata
     * @return The corresponding content model with join table metadata populated
     */
    public ContentModel convertEntityToModel(CollectionContentEntity entity) {
        if (entity == null) {
            return null;
        }

        // Extract the actual content entity from the join table entry
        ContentEntity content = entity.getContent();
        if (content == null) {
            log.error("CollectionContentEntity {} has null content", entity.getId());
            return null;
        }

        // Resolve Hibernate proxy to actual entity type if needed
        // This is critical for JOINED inheritance strategy where proxies may not be properly typed
        content = unproxyContentEntity(content);

        // Use the bulk-loaded conversion method for efficiency
        return convertBulkLoadedContentToModel(content, entity);
    }

    /**
     * Convert a bulk-loaded ContentEntity to its corresponding ContentModel with join table metadata.
     * This method is optimized for bulk-loaded entities that are already properly initialized (not proxies).
     * However, it defensively resolves proxies if needed, especially for COLLECTION types which may still be proxies.
     *
     * @param content   The bulk-loaded content entity (should be properly typed, but may still be a proxy)
     * @param joinEntry The join table entry containing collection-specific metadata (orderIndex, visible)
     * @return The corresponding content model with join table metadata populated
     */
    public ContentModel convertBulkLoadedContentToModel(ContentEntity content, CollectionContentEntity joinEntry) {
        if (content == null) {
            return null;
        }

        if (joinEntry == null) {
            log.warn("Join entry is null for content {}, converting without join table metadata", content.getId());
            return convertRegularContentEntityToModel(content);
        }

        // For COLLECTION type, we need to ensure the entity is properly resolved (not a proxy)
        // before doing instanceof check, as COLLECTION entities may still be proxies even after bulk loading
        if (content.getContentType() == ContentType.COLLECTION) {
            // Resolve proxy if needed before instanceof check
            content = unproxyContentEntity(content);
            if (content instanceof ContentCollectionEntity contentCollectionEntity) {
                ContentModel model = convertCollectionToModel(contentCollectionEntity, joinEntry);
                if (model != null) {
                    model.setOrderIndex(joinEntry.getOrderIndex());
                    model.setVisible(joinEntry.getVisible());
                }
                return model;
            } else {
                log.error("Content type is COLLECTION but entity is not ContentCollectionEntity after unproxy: {}", content.getClass());
                return null;
            }
        }

        // First convert to basic model (will handle IMAGE, TEXT, GIF)
        ContentModel model = convertRegularContentEntityToModel(content);

        if (model == null) {
            log.error("Failed to convert content entity {} to model", content.getId());
            return null;
        }

        // Populate join table metadata from the join table entry
        model.setOrderIndex(joinEntry.getOrderIndex());
        model.setVisible(joinEntry.getVisible());

        return model;
    }

    /**
     * Copy base properties from a ContentEntity to a ContentModel.
     * Note: This does NOT populate join table fields (orderIndex, visible).
     * Those must be set separately using the overloaded convertToModel method with CollectionContentEntity.
     *
     * @param entity The source entity
     * @param model  The target model
     */
    private void copyBaseProperties(ContentEntity entity, ContentModel model) {
        model.setId(entity.getId());
        model.setContentType(entity.getContentType());
        model.setCreatedAt(entity.getCreatedAt());
        model.setUpdatedAt(entity.getUpdatedAt());

        // Join table fields (orderIndex, visible, description/caption) are NOT on ContentEntity
        // They must be populated from CollectionContentEntity in the overloaded convertToModel method
    }

    public static ContentCameraModel cameraEntityToCameraModel(ContentCameraEntity entity) {
        return ContentCameraModel.builder()
                .id(entity.getId())
                .name(entity.getCameraName())
                .build();
    }

    public static ContentLensModel lensEntityToLensModel(ContentLensEntity entity) {
        return ContentLensModel.builder()
                .id(entity.getId())
                .name(entity.getLensName())
                .build();
    }

    /**
     * Convert a ContentImageEntity to a ContentImageModel.
     * Public method for use by other utilities (e.g., CollectionProcessingUtil).
     *
     * @param entity The image content entity to convert
     * @return The corresponding image content model
     */
    public ContentImageModel convertImageEntityToModel(ContentImageEntity entity) {
        return convertImageToModel(entity);
    }

    /**
     * Convert an ImageContentEntity to an ImageContentModel.
     *
     * @param entity The image content entity to convert
     * @return The corresponding image content model
     */
    private ContentImageModel convertImageToModel(ContentImageEntity entity) {
        if (entity == null) {
            return null;
        }

        ContentImageModel model = new ContentImageModel();

        // Copy base properties
        copyBaseProperties(entity, model);

        // Copy image-specific properties
        model.setTitle(entity.getTitle());
        model.setImageWidth(entity.getImageWidth());
        model.setImageHeight(entity.getImageHeight());
        model.setIso(entity.getIso());
        model.setAuthor(entity.getAuthor());
        model.setRating(entity.getRating());
        model.setFStop(entity.getFStop());
        model.setLens(entity.getLens() != null ? lensEntityToLensModel(entity.getLens()) : null);
        model.setBlackAndWhite(entity.getBlackAndWhite());
        model.setIsFilm(entity.getIsFilm());
        // Convert film type entity to display name
        model.setFilmType(entity.getFilmType() != null ? entity.getFilmType().getDisplayName() : null);
        model.setFilmFormat(entity.getFilmFormat());
        model.setShutterSpeed(entity.getShutterSpeed());
        // Map entity's imageUrlWeb to model's imageUrl field
        model.setImageUrl(entity.getImageUrlWeb());
        model.setCamera(entity.getCamera() != null ? cameraEntityToCameraModel(entity.getCamera()) : null);
        model.setFocalLength(entity.getFocalLength());
        model.setLocation(entity.getLocation());
        model.setCreateDate(entity.getCreateDate());

        // Map tags - convert entities to simplified tag objects (id and name only)
        model.setTags(convertTagsToModels(entity.getTags()));

        // Map people - convert entities to simplified person objects (id and name only)
        model.setPeople(convertPeopleToModels(entity.getPeople()));

        // TODO: Populate collections array using join table
        // This requires querying CollectionContentRepository to find all collections containing this content
        // For now, set empty list for minimum functionality
        model.setCollections(new ArrayList<>());

        return model;
    }

    /**
     * Convert a TextContentEntity to a TextContentModel.
     *
     * @param entity The text content entity to convert
     * @return The corresponding text content model
     */
    private ContentTextModel convertTextToModel(ContentTextEntity entity) {
        if (entity == null) {
            return null;
        }

        ContentTextModel model = new ContentTextModel();

        // Copy base properties
        copyBaseProperties(entity, model);

        // Copy text-specific properties
        model.setTextContent(entity.getTextContent());
        model.setFormatType(entity.getFormatType());

        return model;
    }


    /**
     * Convert a GifContentEntity to a GifContentModel.
     *
     * @param entity The gif content entity to convert
     * @return The corresponding gif content model
     */
    private ContentGifModel convertGifToModel(ContentGifEntity entity) {
        if (entity == null) {
            return null;
        }

        ContentGifModel model = new ContentGifModel();

        // Copy base properties
        copyBaseProperties(entity, model);

        // Copy gif-specific properties
        model.setTitle(entity.getTitle());
        model.setGifUrl(entity.getGifUrl());
        model.setThumbnailUrl(entity.getThumbnailUrl());
        model.setWidth(entity.getWidth());
        model.setHeight(entity.getHeight());
        model.setAuthor(entity.getAuthor());
        model.setCreateDate(entity.getCreateDate());

        // Map tags - convert entities to simplified tag objects (id and name only)
        model.setTags(convertTagsToModels(entity.getTags()));

        return model;
    }

    /**
     * Convert a ContentCollectionEntity to a ContentCollectionModel.
     * This handles the COLLECTION content type by extracting data from the referenced collection.
     *
     * @param contentEntity The ContentCollectionEntity containing the reference to another collection
     * @param joinEntry     The join table entry containing collection-specific metadata (orderIndex, visible)
     * @return The converted ContentCollectionModel
     */
    private ContentCollectionModel convertCollectionToModel(ContentCollectionEntity contentEntity, CollectionContentEntity joinEntry) {
        if (contentEntity == null) {
            return null;
        }

        CollectionEntity referencedCollection = contentEntity.getReferencedCollection();
        if (referencedCollection == null) {
            log.error("ContentCollectionEntity {} has null referencedCollection", contentEntity.getId());
            return null;
        }

        // Extract all needed fields early to avoid lazy loading issues
        Long collectionId;
        String title;
        String slug;
        CollectionType collectionType;
        String description;
        ContentImageEntity coverImageEntity;
        
        try {
            collectionId = referencedCollection.getId();
            title = referencedCollection.getTitle();
            slug = referencedCollection.getSlug();
            collectionType = referencedCollection.getType();
            description = referencedCollection.getDescription();
            // Get cover image entity from ContentImageEntity relationship
            coverImageEntity = referencedCollection.getCoverImage();
        } catch (Exception e) {
            log.error("Error accessing referencedCollection fields for ContentCollectionEntity {}: {}", 
                    contentEntity.getId(), e.getMessage(), e);
            return null;
        }

        ContentCollectionModel model = new ContentCollectionModel();

        // Copy base properties from ContentEntity
        copyBaseProperties(contentEntity, model);

        // Set collection-specific fields from the referenced collection
        model.setId(collectionId);
        model.setTitle(title);
        model.setSlug(slug);
        model.setCollectionType(collectionType);
        model.setDescription(description);
        
        // Set full cover image model with dimensions and metadata
        if (coverImageEntity != null) {
            ContentImageModel coverImageModel = convertImageEntityToModel(coverImageEntity);
            model.setCoverImage(coverImageModel);
        }

        // Join table metadata (orderIndex, visible) are set in the calling method
        // Timestamps from ContentEntity are already set via copyBaseProperties

        return model;
    }


    /**
     * Process and save a gif content.
     *
     * @param file         The gif file to process
     * @param collectionId The ID of the collection this content belongs to
     * @param orderIndex   The order index of this content within the collection
     * @param title        The title of the gif
     * @param caption      The caption for the gif
     * @return The saved gif content entity
     */
    public ContentGifEntity processGifContent(
            MultipartFile file,
            Long collectionId,
            Integer orderIndex,
            String title,
            String caption
    ) {
        log.info("Processing gif content for collection {}", collectionId);

        try {
            // Validate input
            contentValidator.validateGifFile(file);

            // Upload GIF to S3 and get URLs
            Map<String, String> urls = uploadGifToS3(file);
            String gifUrl = urls.get("gifUrl");
            String thumbnailUrl = urls.get("thumbnailUrl");

            // Get dimensions from the first frame
            BufferedImage firstFrame = ImageIO.read(file.getInputStream());
            int width = firstFrame.getWidth();
            int height = firstFrame.getHeight();

            // Create the gif content entity
            ContentGifEntity entity = new ContentGifEntity();
            entity.setContentType(ContentType.GIF);
            entity.setTitle(title != null ? title : file.getOriginalFilename());
            entity.setGifUrl(gifUrl);
            entity.setThumbnailUrl(thumbnailUrl);
            entity.setWidth(width);
            entity.setHeight(height);
            entity.setAuthor("System"); // Default author
            entity.setCreateDate(LocalDate.now().toString());

            // Save the entity using the repository
            return contentRepository.save(entity);

        } catch (Exception e) {
            log.error("Error processing gif content: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process GIF content", e);
        }
    }


    /**
     * Upload a GIF to S3.
     *
     * @param file The GIF file to upload
     * @return A map containing the S3 URLs for the GIF and its thumbnail
     * @throws IOException If there's an error processing the file
     */
    private Map<String, String> uploadGifToS3(MultipartFile file) throws IOException {
        log.info("Uploading GIF to S3: {}", file.getOriginalFilename());

        // Validate file is a GIF
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("gif")) {
            throw new IllegalArgumentException("File is not a GIF");
        }

        // Read the file
        byte[] fileBytes = file.getBytes();

        // Generate S3 keys
        String date = LocalDate.now().toString();
        String gifS3Key = generateS3Key(date, file.getOriginalFilename(), "gif");
        String thumbnailS3Key = generateS3Key(date, Objects.requireNonNull(file.getOriginalFilename()).replace(".gif", "-thumbnail.jpg"), "thumbnail");

        // Upload GIF to S3
        ByteArrayInputStream gifStream = new ByteArrayInputStream(fileBytes);
        ObjectMetadata gifMetadata = new ObjectMetadata();
        gifMetadata.setContentType(contentType);
        gifMetadata.setContentLength(fileBytes.length);

        PutObjectRequest putGifRequest = new PutObjectRequest(
                bucketName,
                gifS3Key,
                gifStream,
                gifMetadata
        );

        amazonS3.putObject(putGifRequest);

        // Generate thumbnail from first frame of GIF
        BufferedImage firstFrame = ImageIO.read(new ByteArrayInputStream(fileBytes));
        ByteArrayOutputStream thumbnailOutput = new ByteArrayOutputStream();
        ImageIO.write(firstFrame, "jpg", thumbnailOutput);
        byte[] thumbnailBytes = thumbnailOutput.toByteArray();

        // Upload thumbnail to S3
        ByteArrayInputStream thumbnailStream = new ByteArrayInputStream(thumbnailBytes);
        ObjectMetadata thumbnailMetadata = new ObjectMetadata();
        thumbnailMetadata.setContentType("image/jpeg");
        thumbnailMetadata.setContentLength(thumbnailBytes.length);

        PutObjectRequest putThumbnailRequest = new PutObjectRequest(
                bucketName,
                thumbnailS3Key,
                thumbnailStream,
                thumbnailMetadata
        );

        amazonS3.putObject(putThumbnailRequest);

        // Return CloudFront URLs
        String gifUrl = "https://" + cloudfrontDomain + "/" + gifS3Key;
        String thumbnailUrl = "https://" + cloudfrontDomain + "/" + thumbnailS3Key;

        log.info("GIF uploaded successfully. URL: {}", gifUrl);
        log.info("Thumbnail uploaded successfully. URL: {}", thumbnailUrl);

        Map<String, String> urls = new HashMap<>();
        urls.put("gifUrl", gifUrl);
        urls.put("thumbnailUrl", thumbnailUrl);

        return urls;
    }

    /**
     * Generate an S3 key for a file.
     *
     * @param date     The date to use in the key
     * @param filename The filename
     * @param type     The type of file (gif, thumbnail, etc.)
     * @return The generated S3 key
     */
    private String generateS3Key(String date, String filename, String type) {
        return String.format("%s/%s/%s", date, type, filename);
    }

    /**
     * Check if a file is a JPG/JPEG file.
     *
     * @param file The file to check
     * @return true if the file is a JPG/JPEG, false otherwise
     */
    private boolean isJpgFile(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        return (contentType != null && (contentType.equals("image/jpeg") || contentType.equals("image/jpg"))) ||
                (filename != null && (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")));
    }

    // ============================================================================
    // NEW STREAMLINED IMAGE PROCESSING METHODS
    // ============================================================================

    /**
     * STREAMLINED: Process and save an image content.
     * <p>
     * NOTE: This method only creates the ContentImageEntity. The caller is responsible for
     * creating the CollectionContentEntity to link this image to a collection.
     * <p>
     * Flow:
     * 1. Extract metadata from original file
     * 2. Upload original full-size image to S3
     * 3. Convert JPG -> WebP (with compression)
     * 4. Resize if needed
     * 5. Upload web-optimized image to S3
     * 6. Save metadata with both URLs to database
     * 7. Return ImageContentEntity
     *
     * @param file  The image file to process
     * @param title The title of the image (optional, will use filename if null)
     * @return The saved image content entity
     */
    public ContentImageEntity processImageContent(
            MultipartFile file,
            String title
    ) {
        log.info("Processing image content");

        try {
            // STEP 1: Extract metadata from original file (before any conversion)
            log.info("Step 1: Extracting metadata from original file");
            Map<String, String> metadata = extractImageMetadata(file);

            // STEP 2: Upload original full-size image to S3
            // TODO: May re-implement full-size image storage later
//            log.info("Step 2: Uploading original full-size image to S3");
            String originalFilename = file.getOriginalFilename();
//            String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
//            String imageUrlFullSize = uploadImageToS3(file.getBytes(), originalFilename, contentType, "full");

            // STEP 3: Resize FIRST if needed (max 2500px on longest side)
            // We resize BEFORE converting to WebP to avoid having to decode WebP back to BufferedImage
            log.info("Step 3: Resizing original image if needed");
            BufferedImage originalImage = ImageIO.read(file.getInputStream());
            if (originalImage == null) {
                throw new IOException("Failed to read image: " + originalFilename);
            }

            BufferedImage resizedImage = resizeImage(originalImage, metadata, 2500);

            // STEP 4: Convert to WebP (includes compression)
            log.info("Step 4: Converting to WebP and compressing");
            byte[] processedImageBytes;
            String finalFilename;

            if (isJpgFile(file) || isWebPFile(file)) {
                processedImageBytes = convertJpgToWebP(resizedImage);
                assert originalFilename != null;
                finalFilename = originalFilename.replaceAll("(?i)\\.(jpg|jpeg|webp)$", ".webp");
            } else {
                throw new IOException("Unsupported file format. Only JPG and WebP are supported.");
            }

            // STEP 5: Upload web-optimized image to S3
            log.info("Step 5: Uploading web-optimized image to S3");
            String imageUrlWeb = uploadImageToS3(processedImageBytes, finalFilename, "image/webp", "webP");

            // STEP 6: Create and save ImageContentEntity with metadata
            log.info("Step 6: Saving to database");

            // Generate file identifier for duplicate detection (format: "YYYY-MM-DD/filename.jpg")
            String date = LocalDate.now().toString();
            String fileIdentifier = date + "/" + originalFilename;

            ContentImageEntity entity = ContentImageEntity.builder()
                    .title(title != null ? title : metadata.getOrDefault("title", finalFilename))
                    .imageWidth(parseIntegerOrDefault(metadata.get("imageWidth"), 0))
                    .imageHeight(parseIntegerOrDefault(metadata.get("imageHeight"), 0))
                    .iso(parseIntegerOrDefault(metadata.get("iso"), null))
                    .author(metadata.getOrDefault("author", DEFAULT.AUTHOR))
                    .rating(parseIntegerOrDefault(metadata.get("rating"), null))
                    .fStop(metadata.get("fStop"))
                    .blackAndWhite(parseBooleanOrDefault(metadata.get("blackAndWhite"), false))
                    .isFilm(metadata.get("fStop") == null)
                    .shutterSpeed(metadata.get("shutterSpeed"))
//                    .imageUrlFullSize(imageUrlFullSize)
                    .focalLength(metadata.get("focalLength"))
                    .location(metadata.get("location"))
                    .imageUrlWeb(imageUrlWeb)
                    .createDate(metadata.getOrDefault("createDate", LocalDate.now().toString()))
                    .fileIdentifier(fileIdentifier)
                    .build();

            // Handle camera - find existing or create new from metadata
            String cameraName = metadata.get("camera");
            if (cameraName != null && !cameraName.trim().isEmpty()) {
                ContentCameraEntity camera = contentCameraRepository.findByCameraNameIgnoreCase(cameraName.trim())
                        .orElseGet(() -> {
                            log.info("Creating new camera from metadata: {}", cameraName);
                            ContentCameraEntity newCamera = new ContentCameraEntity(cameraName.trim());
                            return contentCameraRepository.save(newCamera);
                        });
                entity.setCamera(camera);
            }

            // Handle lens - find existing or create new from metadata
            String lensName = metadata.get("lens");
            if (lensName != null && !lensName.trim().isEmpty()) {
                ContentLensEntity lens = contentLensRepository.findByLensNameIgnoreCase(lensName.trim())
                        .orElseGet(() -> {
                            log.info("Creating new lens from metadata: {}", lensName);
                            ContentLensEntity newLens = new ContentLensEntity(lensName.trim());
                            return contentLensRepository.save(newLens);
                        });
                entity.setLens(lens);
            }

            // STEP 7: Save and return
            ContentImageEntity savedEntity = contentRepository.save(entity);
            log.info("Successfully processed image content with ID: {}", savedEntity.getId());
            return savedEntity;

        } catch (Exception e) {
            log.error("Error processing image content: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extract metadata from an image file using Drew Noakes metadata-extractor.
     *
     * @param file The image file to extract metadata from
     * @return Map of metadata key-value pairs
     * @throws IOException If there's an error reading the file
     */
    private Map<String, String> extractImageMetadata(MultipartFile file) throws IOException {
        Map<String, String> metadata = new HashMap<>();

        try (InputStream inputStream = file.getInputStream()) {
            Metadata imageMetadata = ImageMetadataReader.readMetadata(inputStream);

            // Extract all metadata tags
            for (Directory directory : imageMetadata.getDirectories()) {
                for (Tag tag : directory.getTags()) {
                    String tagName = tag.getTagName();
                    String description = tag.getDescription();

                    if (description != null && !description.isEmpty()) {
                        // Map common EXIF tags to our metadata keys
                        switch (tagName) {
                            case "Image Width" -> metadata.put("imageWidth", description.replaceAll("[^0-9]", ""));
                            case "Image Height" -> metadata.put("imageHeight", description.replaceAll("[^0-9]", ""));
                            case "ISO Speed Ratings" -> metadata.put("iso", description);
                            case "Artist" -> metadata.put("author", description);
                            case "Rating" -> metadata.put("rating", description);
                            case "F-Number" -> metadata.put("fStop", description);
                            case "Lens Model", "Lens" -> metadata.put("lens", description);
                            case "Exposure Time" -> metadata.put("shutterSpeed", description);
                            case "Model" -> metadata.put("camera", description);
                            case "Focal Length" -> metadata.put("focalLength", description);
                            case "GPS Latitude", "GPS Longitude" -> {
                                String existingLocation = metadata.getOrDefault("location", "");
                                metadata.put("location", existingLocation + " " + description);
                            }
                            case "Date/Time Original", "Date/Time" -> metadata.put("createDate", description);
                        }
                    }
                }
            }

            // Check for XMP data for additional metadata
            for (Directory directory : imageMetadata.getDirectories()) {
                if (directory instanceof XmpDirectory xmpDirectory) {
                    String xmpXml = xmpDirectory.getXMPMeta().dumpObject();

                    // Check for film simulation indicators
                    if (xmpXml.contains("Film") || xmpXml.contains("film")) {
                        metadata.put("isFilm", "true");
                    }

                    // Check for black and white
                    if (xmpXml.contains("Monochrome") || xmpXml.contains("BlackAndWhite")) {
                        metadata.put("blackAndWhite", "true");
                    }
                }
            }

            // If we couldn't get dimensions from metadata, read from BufferedImage
            if (!metadata.containsKey("imageWidth") || !metadata.containsKey("imageHeight")) {
                try (InputStream is2 = file.getInputStream()) {
                    BufferedImage img = ImageIO.read(is2);
                    if (img != null) {
                        metadata.put("imageWidth", String.valueOf(img.getWidth()));
                        metadata.put("imageHeight", String.valueOf(img.getHeight()));
                    }
                }
            }

            log.info("Extracted metadata: {} tags", metadata.size());

        } catch (Exception e) {
            log.warn("Failed to extract full metadata: {}", e.getMessage());

            // Fallback: At minimum, get dimensions from BufferedImage
            try (InputStream is = file.getInputStream()) {
                BufferedImage img = ImageIO.read(is);
                if (img != null) {
                    metadata.put("imageWidth", String.valueOf(img.getWidth()));
                    metadata.put("imageHeight", String.valueOf(img.getHeight()));
                }
            }
        }

        return metadata;
    }

    /**
     * Check if a file is a WebP file.
     *
     * @param file The file to check
     * @return true if the file is WebP, false otherwise
     */
    private boolean isWebPFile(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        return (contentType != null && contentType.equals("image/webp")) ||
                (filename != null && filename.toLowerCase().endsWith(".webp"));
    }

    /**
     * Upload an image to S3 and return the CloudFront URL.
     *
     * @param imageBytes  The image bytes to upload
     * @param filename    The filename
     * @param contentType The content type of the image (e.g., "image/jpeg", "image/webp")
     * @param folderType  The folder type for S3 path ("full" for originals, "webP" for optimized)
     * @return The CloudFront URL of the uploaded image
     */
    private String uploadImageToS3(byte[] imageBytes, String filename, String contentType, String folderType) {
        String date = LocalDate.now().toString();
        String s3Key = String.format("images/%s/%s/%s", folderType, date, filename);

        log.info("Uploading {} image to S3: {}", folderType, s3Key);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(imageBytes.length);

        PutObjectRequest putRequest = new PutObjectRequest(
                bucketName,
                s3Key,
                inputStream,
                metadata
        );

        amazonS3.putObject(putRequest);

        String cloudfrontUrl = "https://" + cloudfrontDomain + "/" + s3Key;
        log.info("Successfully uploaded {} image: {}", folderType, cloudfrontUrl);

        return cloudfrontUrl;
    }

    /**
     * Resize a BufferedImage to fit within the maximum dimension.
     * This method resizes the ORIGINAL image format (JPG/PNG) before WebP conversion,
     * avoiding the need to decode WebP back to BufferedImage which causes native library crashes.
     * If the image is already within the size limits, it returns the original unchanged.
     *
     * @param originalImage The original BufferedImage to resize
     * @param metadata      The image metadata containing dimensions (will be updated)
     * @param maxDimension  The maximum allowed dimension (width or height)
     * @return Resized BufferedImage, or original if no resize needed
     */
    private BufferedImage resizeImage(BufferedImage originalImage, Map<String, String> metadata, int maxDimension) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        // Calculate new dimensions
        int newWidth, newHeight;
        boolean needsResize = false;

        if (originalWidth > originalHeight) {
            if (originalWidth > maxDimension) {
                newWidth = maxDimension;
                newHeight = (int) (originalHeight * (((double) maxDimension / originalWidth)));
                needsResize = true;
            } else {
                newWidth = originalWidth;
                newHeight = originalHeight;
            }
        } else {
            if (originalHeight > maxDimension) {
                newHeight = maxDimension;
                newWidth = (int) (originalWidth * (((double) maxDimension / originalHeight)));
                needsResize = true;
            } else {
                newHeight = originalHeight;
                newWidth = originalWidth;
            }
        }

        if (!needsResize) {
            log.info("Image is within size limits ({}x{}), no resize needed", originalWidth, originalHeight);
            return originalImage;
        }

        log.info("Resizing image from {}x{} to {}x{}", originalWidth, originalHeight, newWidth, newHeight);

        // Create resized image with proper color model
        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g.dispose();

        // Update metadata with new dimensions
        metadata.put("imageWidth", String.valueOf(newWidth));
        metadata.put("imageHeight", String.valueOf(newHeight));

        return resizedImage;
    }

    /**
     * Convert a BufferedImage to WebP format with compression.
     * This method accepts an already-resized BufferedImage, avoiding the need to decode WebP.
     *
     * @param bufferedImage The BufferedImage to convert
     * @return byte array containing the WebP image data
     * @throws IOException If there's an error during conversion
     */
    private byte[] convertJpgToWebP(BufferedImage bufferedImage) throws IOException {
        log.info("Converting BufferedImage to WebP: {}x{}", bufferedImage.getWidth(), bufferedImage.getHeight());

        // Create a ByteArrayOutputStream to capture the WebP bytes
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Get a WebP ImageWriter from ImageIO
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
        if (!writers.hasNext()) {
            throw new IOException("No WebP writer found. Make sure webp-imageio is on the classpath.");
        }

        ImageWriter writer = writers.next();
        log.info("Using WebP writer: {}", writer.getClass().getName());

        // Configure compression settings for the WebP output
        ImageWriteParam writeParam = writer.getDefaultWriteParam();

        if (writeParam.canWriteCompressed()) {
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            String[] compressionTypes = writeParam.getCompressionTypes();
            if (compressionTypes != null && compressionTypes.length > 0) {
                writeParam.setCompressionType(compressionTypes[0]);
            }
            writeParam.setCompressionQuality(0.85f); // 85% quality
            log.info("Set WebP compression quality to 85%");
        } else {
            log.warn("WebP writer does not support compression settings");
        }

        // Write the BufferedImage to the ByteArrayOutputStream in WebP format
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(bufferedImage, null, null), writeParam);
            writer.dispose();
        }

        byte[] webpBytes = outputStream.toByteArray();
        log.info("Successfully converted BufferedImage to WebP. WebP size: {} bytes", webpBytes.length);

        return webpBytes;
    }

    /**
     * Parse a string to an Integer, returning a default value if parsing fails.
     *
     * @param value        The string value to parse
     * @param defaultValue The default value to return if parsing fails
     * @return The parsed integer or default value
     */
    private Integer parseIntegerOrDefault(String value, Integer defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            // Remove any non-numeric characters except minus sign
            String cleaned = value.replaceAll("[^0-9-]", "");
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parse a string to a Boolean, returning a default value if parsing fails.
     *
     * @param value        The string value to parse
     * @param defaultValue The default value to return if parsing fails
     * @return The parsed boolean or default value
     */
    private Boolean parseBooleanOrDefault(String value, Boolean defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value) || value.equalsIgnoreCase("true") || value.equals("1");
    }

    // =============================================================================
    // IMAGE UPDATE HELPERS (following the pattern from CollectionProcessingUtil)
    // =============================================================================

    /**
     * Apply partial updates from ImageUpdateRequest to an ImageContentEntity.
     * Only fields provided in the update request will be updated.
     * This uses the new prev/new/remove pattern for entity relationships.
     *
     * @param entity        The image entity to update
     * @param updateRequest The update request containing the fields to update
     */
    public void applyImageUpdates(ContentImageEntity entity, ContentImageUpdateRequest updateRequest) {
        // Update basic image metadata fields if provided
        if (updateRequest.getTitle() != null) {
            entity.setTitle(updateRequest.getTitle());
        }
        if (updateRequest.getRating() != null) {
            entity.setRating(updateRequest.getRating());
        }
        if (updateRequest.getLocation() != null) {
            entity.setLocation(updateRequest.getLocation());
        }
        if (updateRequest.getAuthor() != null) {
            entity.setAuthor(updateRequest.getAuthor());
        }
        if (updateRequest.getIsFilm() != null) {
            entity.setIsFilm(updateRequest.getIsFilm());
        }
        if (updateRequest.getFilmFormat() != null) {
            entity.setFilmFormat(updateRequest.getFilmFormat());
        }

        // Validate: if isFilm is being set to true in the update request,
        // filmFormat must also be provided in the update request (or already exist on the entity)
        if (updateRequest.getIsFilm() != null && updateRequest.getIsFilm()) {
            contentImageUpdateValidator.validateFilmFormatRequired(
                    updateRequest.getIsFilm(),
                    updateRequest.getFilmFormat(),
                    entity.getFilmFormat()
            );
        }

        if (updateRequest.getBlackAndWhite() != null) {
            entity.setBlackAndWhite(updateRequest.getBlackAndWhite());
        }

        // Handle camera update using prev/new/remove pattern
        if (updateRequest.getCamera() != null) {
            ContentImageUpdateRequest.CameraUpdate cameraUpdate = updateRequest.getCamera();

            if (Boolean.TRUE.equals(cameraUpdate.getRemove())) {
                // Remove camera association
                entity.setCamera(null);
                log.info("Removed camera association from image {}", entity.getId());
            } else if (cameraUpdate.getNewValue() != null && !cameraUpdate.getNewValue().trim().isEmpty()) {
                // Create new camera by name
                String cameraName = cameraUpdate.getNewValue().trim();
                ContentCameraEntity camera = contentCameraRepository.findByCameraNameIgnoreCase(cameraName)
                        .orElseGet(() -> {
                            log.info("Creating new camera: {}", cameraName);
                            ContentCameraEntity newCamera = new ContentCameraEntity(cameraName);
                            return contentCameraRepository.save(newCamera);
                        });
                entity.setCamera(camera);
            } else if (cameraUpdate.getPrev() != null) {
                // Use existing camera by ID
                ContentCameraEntity camera = contentCameraRepository.findById(cameraUpdate.getPrev())
                        .orElseThrow(() -> new IllegalArgumentException("Camera not found with ID: " + cameraUpdate.getPrev()));
                entity.setCamera(camera);
            }
        }

        // Handle lens update using prev/new/remove pattern
        if (updateRequest.getLens() != null) {
            ContentImageUpdateRequest.LensUpdate lensUpdate = updateRequest.getLens();

            if (Boolean.TRUE.equals(lensUpdate.getRemove())) {
                // Remove lens association
                entity.setLens(null);
                log.info("Removed lens association from image {}", entity.getId());
            } else if (lensUpdate.getNewValue() != null && !lensUpdate.getNewValue().trim().isEmpty()) {
                // Create new lens by name
                String lensName = lensUpdate.getNewValue().trim();
                ContentLensEntity lens = contentLensRepository.findByLensNameIgnoreCase(lensName)
                        .orElseGet(() -> {
                            log.info("Creating new lens: {}", lensName);
                            ContentLensEntity newLens = new ContentLensEntity(lensName);
                            return contentLensRepository.save(newLens);
                        });
                entity.setLens(lens);
            } else if (lensUpdate.getPrev() != null) {
                // Use existing lens by ID
                ContentLensEntity lens = contentLensRepository.findById(lensUpdate.getPrev())
                        .orElseThrow(() -> new IllegalArgumentException("Lens not found with ID: " + lensUpdate.getPrev()));
                entity.setLens(lens);
            }
        }

        // Handle film type update using prev/new/remove pattern
        if (updateRequest.getFilmType() != null) {
            ContentImageUpdateRequest.FilmTypeUpdate filmTypeUpdate = updateRequest.getFilmType();

            if (Boolean.TRUE.equals(filmTypeUpdate.getRemove())) {
                // Remove film type association
                entity.setFilmType(null);
                log.info("Removed film type association from image {}", entity.getId());
            } else if (filmTypeUpdate.getNewValue() != null) {
                // Create new film type
                NewFilmTypeRequest newFilmTypeRequest = filmTypeUpdate.getNewValue();
                String displayName = newFilmTypeRequest.getFilmTypeName().trim();
                // Generate technical name from display name: "Kodak Portra 400" -> "KODAK_PORTRA_400"
                String technicalName = displayName.toUpperCase().replaceAll("\\s+", "_");

                ContentFilmTypeEntity filmType = contentFilmTypeRepository
                        .findByFilmTypeNameIgnoreCase(technicalName)
                        .orElseGet(() -> {
                            log.info("Creating new film type: {} (technical name: {})", displayName, technicalName);
                            ContentFilmTypeEntity newFilmType = new ContentFilmTypeEntity(
                                    technicalName,
                                    displayName,
                                    newFilmTypeRequest.getDefaultIso()
                            );
                            return contentFilmTypeRepository.save(newFilmType);
                        });
                entity.setFilmType(filmType);
            } else if (filmTypeUpdate.getPrev() != null) {
                // Use existing film type by ID
                ContentFilmTypeEntity filmType = contentFilmTypeRepository.findById(filmTypeUpdate.getPrev())
                        .orElseThrow(() -> new IllegalArgumentException("Film type not found with ID: " + filmTypeUpdate.getPrev()));
                entity.setFilmType(filmType);
            }
        }

        if (updateRequest.getFocalLength() != null) {
            entity.setFocalLength(updateRequest.getFocalLength());
        }
        if (updateRequest.getFStop() != null) {
            entity.setFStop(updateRequest.getFStop());
        }
        if (updateRequest.getShutterSpeed() != null) {
            entity.setShutterSpeed(updateRequest.getShutterSpeed());
        }
        if (updateRequest.getIso() != null) {
            entity.setIso(updateRequest.getIso());
        }
        if (updateRequest.getCreateDate() != null) {
            entity.setCreateDate(updateRequest.getCreateDate());
        }

        // Note: Tag and person relationship updates are handled separately in the service layer
        // Collection visibility updates are also handled separately in handleCollectionVisibilityUpdates
    }

    /**
     * Handle collection visibility and orderIndex updates for an image.
     * This method updates the 'visible' flag and 'orderIndex' for the content entry in the current collection.
     * Note: For cross-collection updates (updating the same image in multiple collections),
     * you would need to add a repository method to find content by fileIdentifier.
     * For now, this handles visibility and orderIndex for the current image/collection relationship.
     *
     * Typically used for single-image updates where we're adjusting the orderIndex (drag-and-drop reordering)
     * or toggling visibility within a specific collection. The API call is very lightweight:
     * ContentImageUpdateRequest with a single CollectionUpdate.prev(ChildCollection) containing orderIndex/visible.
     *
     * @param image             The image entity being updated
     * @param collectionUpdates List of collection updates containing visibility and orderIndex information
     */
    public void handleContentChildCollectionUpdates(ContentImageEntity image, List<ChildCollection> collectionUpdates) {
        if (collectionUpdates == null || collectionUpdates.isEmpty()) {
            return;
        }

        // Update visibility and orderIndex for the current image if its collection is in the updates
        for (ChildCollection collectionUpdate : collectionUpdates) {
            if (collectionUpdate.getCollectionId() != null) {
                Long collectionId = collectionUpdate.getCollectionId();
                Integer orderIndex = collectionUpdate.getOrderIndex();
                Boolean visible = collectionUpdate.getVisible();

                // Find the join table entry for this image in this collection
                CollectionContentEntity joinEntry = collectionContentRepository
                        .findByCollectionIdAndContentId(collectionId, image.getId());

                if (joinEntry == null) {
                    log.warn("No join table entry found for content {} in collection {}. Skipping update.",
                            image.getId(), collectionId);
                    continue;
                }

                boolean updated = false;

                // Update Order Index if provided
                if (orderIndex != null) {
                    collectionContentRepository.updateOrderIndex(
                            joinEntry.getId(),  // Use join table entry ID, not collection ID
                            orderIndex
                    );
                    log.info("Updated orderIndex for image {} in collection {} to {}",
                            image.getId(), collectionId, orderIndex);
                    updated = true;
                }

                // Update visibility if provided
                if (visible != null) {
                    collectionContentRepository.updateVisible(
                            joinEntry.getId(),  // Use join table entry ID, not collection ID
                            visible
                    );
                    log.info("Updated visibility for image {} in collection {} to {}",
                            image.getId(),
                            collectionId,
                            visible
                    );
                    updated = true;
                }

                if (updated) {
                    break; // Only update once for the matching collection
                }
            }
        }
    }

    // =============================================================================
    // TAG AND PEOPLE UPDATE HELPERS (Shared utility methods)
    // =============================================================================

    /**
     * Update tags on an entity using the prev/new/remove pattern.
     * This is a shared utility method used by both Collection and Content update operations.
     *
     * @param currentTags Current set of tags on the entity
     * @param tagUpdate   The tag update containing remove/prev/newValue operations
     * @param newTags     Optional set to track newly created tags (for response metadata)
     * @return Updated set of tags
     */
    public Set<ContentTagEntity> updateTags(
            Set<ContentTagEntity> currentTags,
            TagUpdate tagUpdate,
            Set<ContentTagEntity> newTags) {
        if (tagUpdate == null) {
            return currentTags;
        }

        Set<ContentTagEntity> tags = new HashSet<>(currentTags);

        // Remove tags if specified
        if (tagUpdate.getRemove() != null && !tagUpdate.getRemove().isEmpty()) {
            tags.removeIf(tag -> tagUpdate.getRemove().contains(tag.getId()));
        }

        // Add existing tags by ID (prev)
        if (tagUpdate.getPrev() != null && !tagUpdate.getPrev().isEmpty()) {
            Set<ContentTagEntity> existingTags = tagUpdate.getPrev().stream()
                    .map(tagId -> contentTagRepository.findById(tagId)
                            .orElseThrow(() -> new EntityNotFoundException("Tag not found: " + tagId)))
                    .collect(Collectors.toSet());
            tags.addAll(existingTags);
        }

        // Create and add new tags by name (newValue) with optional tracking
        if (tagUpdate.getNewValue() != null && !tagUpdate.getNewValue().isEmpty()) {
            for (String tagName : tagUpdate.getNewValue()) {
                if (tagName != null && !tagName.trim().isEmpty()) {
                    String trimmedName = tagName.trim();
                    var existing = contentTagRepository.findByTagNameIgnoreCase(trimmedName);
                    if (existing.isPresent()) {
                        tags.add(existing.get());
                    } else {
                        ContentTagEntity newTag = new ContentTagEntity(trimmedName);
                        newTag = contentTagRepository.save(newTag);
                        tags.add(newTag);
                        if (newTags != null) {
                            newTags.add(newTag);
                        }
                        log.info("Created new tag: {}", trimmedName);
                    }
                }
            }
        }

        return tags;
    }

    /**
     * Update people on an entity using the prev/new/remove pattern.
     * This is a shared utility method used by both Collection and Content update operations.
     *
     * @param currentPeople Current set of people on the entity
     * @param personUpdate  The person update containing remove/prev/newValue operations
     * @param newPeople     Optional set to track newly created people (for response metadata)
     * @return Updated set of people
     */
    public Set<ContentPersonEntity> updatePeople(
            Set<ContentPersonEntity> currentPeople,
            PersonUpdate personUpdate,
            Set<ContentPersonEntity> newPeople) {
        if (personUpdate == null) {
            return currentPeople;
        }

        Set<ContentPersonEntity> people = new HashSet<>(currentPeople);

        // Remove people if specified
        if (personUpdate.getRemove() != null && !personUpdate.getRemove().isEmpty()) {
            people.removeIf(person -> personUpdate.getRemove().contains(person.getId()));
        }

        // Add existing people by ID (prev)
        if (personUpdate.getPrev() != null && !personUpdate.getPrev().isEmpty()) {
            Set<ContentPersonEntity> existingPeople = personUpdate.getPrev().stream()
                    .map(personId -> contentPersonRepository.findById(personId)
                            .orElseThrow(() -> new EntityNotFoundException("Person not found: " + personId)))
                    .collect(Collectors.toSet());
            people.addAll(existingPeople);
        }

        // Create and add new people by name (newValue) with optional tracking
        if (personUpdate.getNewValue() != null && !personUpdate.getNewValue().isEmpty()) {
            for (String personName : personUpdate.getNewValue()) {
                if (personName != null && !personName.trim().isEmpty()) {
                    String trimmedName = personName.trim();
                    var existing = contentPersonRepository.findByPersonNameIgnoreCase(trimmedName);
                    if (existing.isPresent()) {
                        people.add(existing.get());
                    } else {
                        ContentPersonEntity newPerson = new ContentPersonEntity(trimmedName);
                        newPerson = contentPersonRepository.save(newPerson);
                        people.add(newPerson);
                        if (newPeople != null) {
                            newPeople.add(newPerson);
                        }
                        log.info("Created new person: {}", trimmedName);
                    }
                }
            }
        }

        return people;
    }

    // =============================================================================
    // TAG AND PEOPLE MODEL CONVERSION HELPERS
    // =============================================================================

    /**
     * Convert a set of ContentTagEntity to a sorted list of ContentTagModel.
     * Returns empty list if tags is null or empty.
     *
     * @param tags Set of tag entities to convert
     * @return Sorted list of tag models (alphabetically by name)
     */
    public List<ContentTagModel> convertTagsToModels(Set<ContentTagEntity> tags) {
        if (tags == null || tags.isEmpty()) {
            return new ArrayList<>();
        }
        return tags.stream()
                .map(tag -> ContentTagModel.builder()
                        .id(tag.getId())
                        .name(tag.getTagName())
                        .build())
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .collect(Collectors.toList());
    }

    /**
     * Convert a set of ContentPersonEntity to a sorted list of ContentPersonModel.
     * Returns empty list if people is null or empty.
     *
     * @param people Set of person entities to convert
     * @return Sorted list of person models (alphabetically by name)
     */
    public List<ContentPersonModel> convertPeopleToModels(Set<ContentPersonEntity> people) {
        if (people == null || people.isEmpty()) {
            return new ArrayList<>();
        }
        return people.stream()
                .map(person -> ContentPersonModel.builder()
                        .id(person.getId())
                        .name(person.getPersonName())
                        .build())
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .collect(Collectors.toList());
    }
}
