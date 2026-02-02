package edens.zac.portfolio.backend.services;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.xmp.XmpDirectory;
import edens.zac.portfolio.backend.dao.CollectionContentDao;
import edens.zac.portfolio.backend.dao.CollectionDao;
import edens.zac.portfolio.backend.dao.ContentCameraDao;
import edens.zac.portfolio.backend.dao.ContentCollectionDao;
import edens.zac.portfolio.backend.dao.ContentDao;
import edens.zac.portfolio.backend.dao.ContentFilmTypeDao;
import edens.zac.portfolio.backend.dao.ContentGifDao;
import edens.zac.portfolio.backend.dao.ContentLensDao;
import edens.zac.portfolio.backend.dao.ContentPersonDao;
import edens.zac.portfolio.backend.dao.ContentTagDao;
import edens.zac.portfolio.backend.dao.ContentTextDao;
import edens.zac.portfolio.backend.dao.LocationDao;
import edens.zac.portfolio.backend.dao.TagDao;
import edens.zac.portfolio.backend.entity.*;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.model.LocationUpdate;
import edens.zac.portfolio.backend.model.PersonUpdate;
import edens.zac.portfolio.backend.model.TagUpdate;
import edens.zac.portfolio.backend.services.validator.ContentImageUpdateValidator;
import edens.zac.portfolio.backend.services.validator.ContentValidator;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.ContentType;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Utility class for processing content. Handles conversion between entities and
 * models, content
 * validation, and specialized processing for different content types.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContentProcessingUtil {

  // Dependencies for S3 upload and DAOs
  private final S3Client s3Client;
  private final ContentDao contentDao;
  private final CollectionDao collectionDao;
  private final ContentCameraDao contentCameraDao;
  private final ContentLensDao contentLensDao;
  private final ContentFilmTypeDao contentFilmTypeDao;
  private final ContentTagDao contentTagDao;
  private final TagDao tagDao;
  private final ContentPersonDao contentPersonDao;
  private final LocationDao locationDao;
  private final CollectionContentDao collectionContentDao;
  private final ContentTextDao contentTextDao;
  private final ContentCollectionDao contentCollectionDao;
  private final ContentGifDao contentGifDao;
  private final ContentImageUpdateValidator contentImageUpdateValidator;
  private final ContentValidator contentValidator;

  @Value("${aws.portfolio.s3.bucket}")
  private String bucketName;

  @Value("${cloudfront.domain}")
  private String cloudfrontDomain;

  // S3 path constants for content type hierarchy:
  // {ContentType}/{Quality}/{Year}/{Month}/{filename}
  private static final String PATH_IMAGE_FULL = "Image/Full";
  private static final String PATH_IMAGE_WEB = "Image/Web";
  // private static final String PATH_IMAGE_RAW = "Image/Raw"; // future use
  private static final String PATH_GIF_FULL = "Gif/Full";

  // Default values for image metadata
  public static final class DEFAULT {
    public static final String AUTHOR = "Zechariah Edens";
  }

  /**
   * Convert a ContentEntity to its corresponding ContentModel based on type. This
   * version does not
   * have collection-specific metadata (orderIndex, caption, visible). Use the
   * overloaded version
   * that accepts CollectionContentEntity for full metadata.
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

    // No need to unproxy - entities are loaded directly from DAOs

    return switch (entity.getContentType()) {
      case IMAGE -> convertImageToModel((ContentImageEntity) entity);
      case TEXT -> convertTextToModel((ContentTextEntity) entity);
      case GIF -> convertGifToModel((ContentGifEntity) entity);
      case COLLECTION -> null;
    };
  }

  /**
   * No-op method for backward compatibility. With raw SQL/DAOs, entities are
   * never proxies, so this
   * just returns the entity as-is.
   *
   * @param entity The entity (never a proxy with DAOs)
   * @return The entity unchanged
   */
  public ContentEntity unproxyContentEntity(ContentEntity entity) {
    // With raw SQL DAOs, entities are never Hibernate proxies
    // This method exists for backward compatibility with code that calls it
    return entity;
  }

  /**
   * Convert a ContentEntity to its corresponding ContentModel with join table
   * metadata. This
   * version populates collection-specific fields (orderIndex, visible) from the
   * join table.
   *
   * @param entity The join table entry (CollectionContentEntity) containing the
   *               content and
   *               metadata
   * @return The corresponding content model with join table metadata populated
   */
  public ContentModel convertEntityToModel(CollectionContentEntity entity) {
    if (entity == null) {
      return null;
    }

    // Load the content entity from DAO using contentId
    Long contentId = entity.getContentId();
    if (contentId == null) {
      log.error("CollectionContentEntity {} has null contentId", entity.getId());
      return null;
    }

    // Load content entity - try loading as different types based on contentType
    // First, get the base content to determine type
    Optional<ContentEntity> baseContentOpt = contentDao.findAllByIds(List.of(contentId)).stream().findFirst();
    if (baseContentOpt.isEmpty()) {
      log.error(
          "Content entity {} not found for CollectionContentEntity {}", contentId, entity.getId());
      return null;
    }

    ContentEntity baseContent = baseContentOpt.get();
    ContentEntity content;

    // Load typed entity based on contentType
    switch (baseContent.getContentType()) {
      case IMAGE -> {
        Optional<ContentImageEntity> imageOpt = contentDao.findImageById(contentId);
        content = imageOpt.orElse(null);
      }
      case TEXT -> {
        Optional<ContentTextEntity> textOpt = contentTextDao.findById(contentId);
        content = textOpt.orElse(null);
      }
      case GIF -> {
        Optional<ContentGifEntity> gifOpt = contentGifDao.findById(contentId);
        content = gifOpt.orElse(null);
      }
      case COLLECTION -> {
        Optional<ContentCollectionEntity> collectionOpt = contentCollectionDao.findById(contentId);
        content = collectionOpt.orElse(null);
      }
      default -> {
        log.error(
            "Unknown content type {} for content {}", baseContent.getContentType(), contentId);
        return null;
      }
    }

    if (content == null) {
      log.error(
          "Failed to load typed content entity {} for CollectionContentEntity {}",
          contentId,
          entity.getId());
      return null;
    }

    // Use the bulk-loaded conversion method for efficiency
    return convertBulkLoadedContentToModel(content, entity);
  }

  /**
   * Convert a bulk-loaded ContentEntity to its corresponding ContentModel with
   * join table metadata.
   * This method is optimized for bulk-loaded entities that are already properly
   * initialized (not
   * proxies). However, it defensively resolves proxies if needed, especially for
   * COLLECTION types
   * which may still be proxies.
   *
   * @param content   The bulk-loaded content entity (should be properly typed,
   *                  but may still be a
   *                  proxy)
   * @param joinEntry The join table entry containing collection-specific metadata
   *                  (orderIndex,
   *                  visible)
   * @return The corresponding content model with join table metadata populated
   */
  public ContentModel convertBulkLoadedContentToModel(
      ContentEntity content, CollectionContentEntity joinEntry) {
    if (content == null) {
      return null;
    }

    if (joinEntry == null) {
      log.warn(
          "Join entry is null for content {}, converting without join table metadata",
          content.getId());
      return convertRegularContentEntityToModel(content);
    }

    // For COLLECTION type, we need to ensure the entity is properly resolved (not a
    // proxy)
    // before doing instanceof check, as COLLECTION entities may still be proxies
    // even after bulk loading
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
        log.error(
            "Content type is COLLECTION but entity is not ContentCollectionEntity after unproxy: {}",
            content.getClass());
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
   * Copy base properties from a ContentEntity to a ContentModel. Note: This does
   * NOT populate join
   * table fields (orderIndex, visible). Those must be set separately using the
   * overloaded
   * convertToModel method with CollectionContentEntity.
   *
   * @param entity The source entity
   * @param model  The target model
   */
  private void copyBaseProperties(ContentEntity entity, ContentModel model) {
    model.setId(entity.getId());
    model.setContentType(entity.getContentType());
    model.setCreatedAt(entity.getCreatedAt());
    model.setUpdatedAt(entity.getUpdatedAt());

    // Join table fields (orderIndex, visible, description/caption) are NOT on
    // ContentEntity
    // They must be populated from CollectionContentEntity in the overloaded
    // convertToModel method
  }

  public static ContentCameraModel cameraEntityToCameraModel(ContentCameraEntity entity) {
    return ContentCameraModel.builder().id(entity.getId()).name(entity.getCameraName()).build();
  }

  public static ContentLensModel lensEntityToLensModel(ContentLensEntity entity) {
    return ContentLensModel.builder().id(entity.getId()).name(entity.getLensName()).build();
  }

  /**
   * Convert a ContentImageEntity to a ContentImageModel. Public method for use by
   * other utilities
   * (e.g., CollectionProcessingUtil).
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

    // Load tags from database if not already loaded
    if (entity.getTags() == null || entity.getTags().isEmpty()) {
      loadContentTags(entity);
    }

    // Load people from database if not already loaded
    if (entity.getPeople() == null || entity.getPeople().isEmpty()) {
      loadContentPeople(entity);
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
    model.setCamera(
        entity.getCamera() != null ? cameraEntityToCameraModel(entity.getCamera()) : null);
    model.setFocalLength(entity.getFocalLength());
    // Convert locationId to LocationModel
    if (entity.getLocationId() != null) {
      LocationEntity locationEntity = locationDao.findById(entity.getLocationId()).orElse(null);
      if (locationEntity != null) {
        model.setLocation(
            LocationModel.builder()
                .id(locationEntity.getId())
                .name(locationEntity.getLocationName())
                .build());
      }
    }
    model.setCreateDate(entity.getCreateDate());

    // Map tags - convert entities to simplified tag objects (id and name only)
    model.setTags(convertTagsToModels(entity.getTags()));

    // Map people - convert entities to simplified person objects (id and name only)
    model.setPeople(convertPeopleToModels(entity.getPeople()));

    // TODO: Populate collections array using join table
    // This requires querying CollectionContentDao to find all collections
    // containing this content
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

    // Load tags from database if not already loaded
    if (entity.getTags() == null || entity.getTags().isEmpty()) {
      loadContentTags(entity);
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
   * Convert a ContentCollectionEntity to a ContentCollectionModel. This handles
   * the COLLECTION
   * content type by extracting data from the referenced collection.
   *
   * @param contentEntity The ContentCollectionEntity containing the reference to
   *                      another collection
   * @param joinEntry     The join table entry containing collection-specific
   *                      metadata (orderIndex,
   *                      visible)
   * @return The converted ContentCollectionModel
   */
  private ContentCollectionModel convertCollectionToModel(
      ContentCollectionEntity contentEntity, CollectionContentEntity joinEntry) {
    if (contentEntity == null) {
      return null;
    }

    CollectionEntity referencedCollection = contentEntity.getReferencedCollection();
    if (referencedCollection == null) {
      log.error("ContentCollectionEntity {} has null referencedCollection", contentEntity.getId());
      return null;
    }

    // Check if we only have the ID (loaded from bulk query via
    // ContentDao.findAllByIds)
    // and load full collection data from database
    Long referencedCollectionId = referencedCollection.getId();
    if (referencedCollectionId != null && referencedCollection.getTitle() == null) {
      log.debug(
          "Loading full collection data for referencedCollectionId: {}", referencedCollectionId);
      referencedCollection = collectionDao
          .findById(referencedCollectionId)
          .orElse(referencedCollection); // Fall back to partial data if not found
    }

    // Extract all needed fields (now from fully-loaded collection)
    Long collectionId = referencedCollection.getId();
    String title = referencedCollection.getTitle();
    String slug = referencedCollection.getSlug();
    CollectionType collectionType = referencedCollection.getType();
    String description = referencedCollection.getDescription();

    // Load cover image entity using coverImageId
    ContentImageEntity coverImageEntity = null;
    if (referencedCollection.getCoverImageId() != null) {
      coverImageEntity = contentDao.findImageById(referencedCollection.getCoverImageId()).orElse(null);
    }

    ContentCollectionModel model = new ContentCollectionModel();

    // Copy base properties from ContentEntity (sets id to content table ID)
    copyBaseProperties(contentEntity, model);

    // Set collection-specific fields from the referenced collection
    // Note: id is already set by copyBaseProperties to contentEntity.getId()
    // (content table ID)
    // We set referencedCollectionId separately for navigation to the actual
    // collection
    model.setReferencedCollectionId(collectionId);
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
      MultipartFile file, Long collectionId, Integer orderIndex, String title, String caption) {
    log.info("Processing gif content for collection {}", collectionId);

    try {
      // Validate input
      contentValidator.validateGifFile(file);

      // Upload GIF to S3 and get URLs
      Map<String, String> urls = uploadGifToS3(file);
      String gifUrl = urls.get("gifUrl");
      String thumbnailUrl = urls.get("thumbnailUrl");

      // Get dimensions from the first frame
      BufferedImage firstFrame;
      try (InputStream gifStream = file.getInputStream()) {
        firstFrame = ImageIO.read(gifStream);
      }
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

      // Save the entity using the DAO
      // Note: GIF saving not yet implemented in DAO - placeholder
      throw new UnsupportedOperationException(
          "GIF content saving not yet implemented in DAO layer");

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

    // Generate S3 keys using new path structure: Gif/Full/{year}/{month}/{filename}
    // GIFs don't have EXIF data, so we use the current date
    LocalDate now = LocalDate.now();
    int year = now.getYear();
    int month = now.getMonthValue();
    String filename = file.getOriginalFilename();
    String gifS3Key = String.format("%s/%d/%02d/%s", PATH_GIF_FULL, year, month, filename);
    String thumbnailS3Key = String.format(
        "%s/%d/%02d/%s",
        PATH_GIF_FULL,
        year,
        month,
        Objects.requireNonNull(filename).replace(".gif", "-thumbnail.jpg"));

    // Upload GIF to S3
    PutObjectRequest putGifRequest = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(gifS3Key)
        .contentType(contentType)
        .contentLength((long) fileBytes.length)
        .build();

    s3Client.putObject(putGifRequest, RequestBody.fromBytes(fileBytes));

    // Generate thumbnail from first frame of GIF
    BufferedImage firstFrame = ImageIO.read(new ByteArrayInputStream(fileBytes));
    ByteArrayOutputStream thumbnailOutput = new ByteArrayOutputStream();
    ImageIO.write(firstFrame, "jpg", thumbnailOutput);
    byte[] thumbnailBytes = thumbnailOutput.toByteArray();

    // Upload thumbnail to S3
    PutObjectRequest putThumbnailRequest = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(thumbnailS3Key)
        .contentType("image/jpeg")
        .contentLength((long) thumbnailBytes.length)
        .build();

    s3Client.putObject(putThumbnailRequest, RequestBody.fromBytes(thumbnailBytes));

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
   * Check if a file is a JPG/JPEG file.
   *
   * @param file The file to check
   * @return true if the file is a JPG/JPEG, false otherwise
   */
  private boolean isJpgFile(MultipartFile file) {
    String contentType = file.getContentType();
    String filename = file.getOriginalFilename();

    return (contentType != null
        && (contentType.equals("image/jpeg") || contentType.equals("image/jpg")))
        || (filename != null
            && (filename.toLowerCase().endsWith(".jpg")
                || filename.toLowerCase().endsWith(".jpeg")));
  }

  // ============================================================================
  // NEW STREAMLINED IMAGE PROCESSING METHODS
  // ============================================================================

  /**
   * STREAMLINED: Process and save an image content.
   *
   * <p>
   * NOTE: This method only creates the ContentImageEntity. The caller is
   * responsible for
   * creating the CollectionContentEntity to link this image to a collection.
   *
   * <p>
   * Flow: 1. Extract metadata from original file 2. Upload original full-size
   * image to S3 3.
   * Convert JPG -> WebP (with compression) 4. Resize if needed 5. Upload
   * web-optimized image to S3
   * 6. Save metadata with both URLs to database 7. Return ImageContentEntity
   *
   * @param file  The image file to process
   * @param title The title of the image (optional, will use filename if null)
   * @return The saved image content entity
   */
  public ContentImageEntity processImageContent(MultipartFile file, String title)
      throws IOException {
    log.info("Processing image content");

    // STEP 1: Extract metadata from original file (before any conversion)
    log.info("Step 1: Extracting metadata from original file");
    Map<String, String> metadata = extractImageMetadata(file);

    // Parse image capture date for S3 path organization (year/month from EXIF)
    int[] dateComponents = parseImageDate(metadata.get("createDate"));
    int imageYear = dateComponents[0];
    int imageMonth = dateComponents[1];
    log.info("Image capture date: {}/{}", imageYear, String.format("%02d", imageMonth));

    // STEP 2: Upload original full-size image to S3
    log.info("Step 2: Uploading original full-size image to S3");
    String originalFilename = file.getOriginalFilename();
    String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
    String imageUrlOriginal = uploadImageToS3(
        file.getBytes(), originalFilename, contentType, PATH_IMAGE_FULL, imageYear, imageMonth);

    // STEP 3: Resize FIRST if needed (max 2500px on longest side)
    // We resize BEFORE converting to WebP to avoid having to decode WebP back to
    // BufferedImage
    log.info("Step 3: Resizing original image if needed");
    BufferedImage originalImage;
    try (InputStream imageStream = file.getInputStream()) {
      originalImage = ImageIO.read(imageStream);
    }
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
    String imageUrlWeb = uploadImageToS3(
        processedImageBytes,
        finalFilename,
        "image/webp",
        PATH_IMAGE_WEB,
        imageYear,
        imageMonth);

    // STEP 6: Create and save ImageContentEntity with metadata
    log.info("Step 6: Saving to database");

    // Generate file identifier for duplicate detection (format:
    // "YYYY-MM/filename.jpg")
    String fileIdentifier = String.format("%d-%02d/%s", imageYear, imageMonth, originalFilename);

    ContentImageEntity entity = ContentImageEntity.builder()
        .contentType(ContentType.IMAGE)
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
        .imageUrlOriginal(imageUrlOriginal)
        .focalLength(metadata.get("focalLength"))
        .locationId(
            metadata.get("location") != null
                ? locationDao.findOrCreate(metadata.get("location")).getId()
                : null)
        .imageUrlWeb(imageUrlWeb)
        .createDate(metadata.getOrDefault("createDate", LocalDate.now().toString()))
        .createdAt(parseExifDateToLocalDateTime(metadata.get("createDate")))
        .fileIdentifier(fileIdentifier)
        .build();

    // Handle camera - use helper method to create or find existing
    String cameraName = metadata.get("camera");
    String bodySerialNumber = metadata.get("bodySerialNumber");
    if (cameraName != null && !cameraName.trim().isEmpty()) {
      ContentCameraEntity camera = createCamera(cameraName, bodySerialNumber, null);
      entity.setCamera(camera);
    }

    // Handle lens - use helper method to create or find existing
    String lensName = metadata.get("lens");
    String lensSerialNumber = metadata.get("lensSerialNumber");
    if (lensName != null && !lensName.trim().isEmpty()) {
      ContentLensEntity lens = createLens(lensName, lensSerialNumber, null);
      entity.setLens(lens);
    }

    // STEP 7: Save and return
    ContentImageEntity savedEntity = contentDao.saveImage(entity);
    log.info("Successfully processed image content with ID: {}", savedEntity.getId());
    return savedEntity;
  }

  /**
   * Extract metadata from an image file using Drew Noakes metadata-extractor.
   * Uses the
   * ImageMetadata enum system for consistent, maintainable field extraction.
   *
   * @param file The image file to extract metadata from
   * @return Map of metadata key-value pairs
   * @throws IOException If there's an error reading the file
   */
  private Map<String, String> extractImageMetadata(MultipartFile file) throws IOException {
    Map<String, String> metadata = new HashMap<>();

    try (InputStream inputStream = file.getInputStream()) {
      Metadata imageMetadata = ImageMetadataReader.readMetadata(inputStream);

      // Extract EXIF metadata for all defined fields
      for (Directory directory : imageMetadata.getDirectories()) {
        for (Tag tag : directory.getTags()) {
          extractFromExifTag(tag, metadata);
        }
      }

      // Extract XMP metadata for all defined fields
      for (XmpDirectory xmpDirectory : imageMetadata.getDirectoriesOfType(XmpDirectory.class)) {
        extractFromXmpDirectory(xmpDirectory, metadata);
      }

      // Fallback: Get dimensions from BufferedImage if not found
      ensureDimensions(file, metadata);

      log.info("Extracted metadata: {} tags", metadata.size());
      log.info("Final rating value: {}", metadata.getOrDefault("rating", "NULL"));

    } catch (Exception e) {
      log.warn("Failed to extract full metadata: {}", e.getMessage());
      ensureDimensions(file, metadata);
    }

    return metadata;
  }

  /**
   * Extract metadata from a single EXIF tag using the ImageMetadata enum
   * configuration.
   *
   * @param tag      The EXIF tag to process
   * @param metadata The metadata map to populate
   */
  private void extractFromExifTag(Tag tag, Map<String, String> metadata) {
    String tagName = tag.getTagName();
    String description = tag.getDescription();

    if (description == null || description.isEmpty()) {
      return;
    }

    // Try each metadata field
    for (ImageMetadata.MetadataField field : ImageMetadata.MetadataField.values()) {
      if (field.getExifTags().matches(tagName)) {
        // Only set if not already extracted
        if (!metadata.containsKey(field.getFieldName())) {
          String extractedValue = field.getExtractor().extract(description);
          if (extractedValue != null) {
            metadata.put(field.getFieldName(), extractedValue);
          }
        }
      }
    }
  }

  /**
   * Extract metadata from XMP directory using the ImageMetadata enum
   * configuration.
   *
   * @param xmpDirectory The XMP directory to process
   * @param metadata     The metadata map to populate
   */
  private void extractFromXmpDirectory(XmpDirectory xmpDirectory, Map<String, String> metadata) {
    com.adobe.internal.xmp.XMPMeta xmpMeta = xmpDirectory.getXMPMeta();

    // Try each metadata field
    for (ImageMetadata.MetadataField field : ImageMetadata.MetadataField.values()) {
      ImageMetadata.XmpProperty xmpProperty = field.getXmpProperty();

      if (!xmpProperty.hasProperties()) {
        continue;
      }

      // Try each property name in the namespace
      for (String propertyName : xmpProperty.getPropertyNames()) {
        try {
          com.adobe.internal.xmp.properties.XMPProperty prop = xmpMeta.getProperty(xmpProperty.getNamespace(),
              propertyName);

          if (prop != null && prop.getValue() != null) {
            // Only set if not already extracted from EXIF
            if (!metadata.containsKey(field.getFieldName())) {
              String extractedValue = field.getExtractor().extract(prop.getValue());
              if (extractedValue != null) {
                metadata.put(field.getFieldName(), extractedValue);
                break; // Found value, stop trying other property names
              }
            }
          }
        } catch (com.adobe.internal.xmp.XMPException e) {
          // Property not found, continue to next
        }
      }
    }
  }

  /**
   * Ensure dimensions are present in metadata, reading from BufferedImage if
   * needed.
   *
   * @param file     The image file
   * @param metadata The metadata map to populate
   */
  private void ensureDimensions(MultipartFile file, Map<String, String> metadata) {
    if (!metadata.containsKey("imageWidth") || !metadata.containsKey("imageHeight")) {
      try (InputStream is = file.getInputStream()) {
        BufferedImage img = ImageIO.read(is);
        if (img != null) {
          metadata.put("imageWidth", String.valueOf(img.getWidth()));
          metadata.put("imageHeight", String.valueOf(img.getHeight()));
        }
      } catch (IOException e) {
        log.warn("Failed to read image dimensions from BufferedImage: {}", e.getMessage());
      }
    }
  }

  /**
   * Parse year and month from EXIF date string. EXIF date format is typically
   * "2024:05:15
   * 14:30:00".
   *
   * @param createDate The date string from EXIF metadata
   * @return int[] {year, month} or current date as fallback
   */
  private int[] parseImageDate(String createDate) {
    if (createDate == null || createDate.isEmpty()) {
      LocalDate now = LocalDate.now();
      return new int[] { now.getYear(), now.getMonthValue() };
    }
    try {
      // EXIF format: "2024:05:15 14:30:00"
      String[] parts = createDate.split("[: ]");
      int year = Integer.parseInt(parts[0]);
      int month = Integer.parseInt(parts[1]);
      return new int[] { year, month };
    } catch (Exception e) {
      log.warn("Failed to parse EXIF date '{}', using current date", createDate);
      LocalDate now = LocalDate.now();
      return new int[] { now.getYear(), now.getMonthValue() };
    }
  }

  /**
   * Parse EXIF date string to LocalDateTime. EXIF date format is "2026:01:26
   * 17:48:38" (YYYY:MM:DD
   * HH:MM:SS).
   *
   * @param createDate The date string from EXIF metadata
   * @return LocalDateTime parsed from EXIF date, or null if parsing fails
   */
  private LocalDateTime parseExifDateToLocalDateTime(String createDate) {
    if (createDate == null || createDate.trim().isEmpty()) {
      return null;
    }
    try {
      // EXIF format: "2026:01:26 17:48:38" -> convert to "2026-01-26T17:48:38"
      // Replace first two colons (in date part) with dashes, then space with T
      String normalized = createDate.replaceFirst(":", "-").replaceFirst(":", "-").replace(" ", "T");
      return LocalDateTime.parse(normalized);
    } catch (Exception e) {
      log.warn("Failed to parse EXIF date '{}' to LocalDateTime, will use upload time", createDate);
      return null;
    }
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

    return (contentType != null && contentType.equals("image/webp"))
        || (filename != null && filename.toLowerCase().endsWith(".webp"));
  }

  /**
   * Upload an image to S3 and return the CloudFront URL.
   *
   * <p>
   * Path structure: {basePath}/{year}/{month}/{filename} Example:
   * Image/Full/2025/08/photo.jpg
   *
   * @param imageBytes  The image bytes to upload
   * @param filename    The filename
   * @param contentType The content type of the image (e.g., "image/jpeg",
   *                    "image/webp")
   * @param basePath    The base path for S3 (e.g., PATH_IMAGE_FULL,
   *                    PATH_IMAGE_WEB)
   * @param year        The year from image capture date
   * @param month       The month from image capture date (1-12)
   * @return The CloudFront URL of the uploaded image
   */
  private String uploadImageToS3(
      byte[] imageBytes,
      String filename,
      String contentType,
      String basePath,
      int year,
      int month) {
    String s3Key = String.format("%s/%d/%02d/%s", basePath, year, month, filename);

    log.info("Uploading to S3: {}", s3Key);

    PutObjectRequest putRequest = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(s3Key)
        .contentType(contentType)
        .contentLength((long) imageBytes.length)
        .build();

    s3Client.putObject(putRequest, RequestBody.fromBytes(imageBytes));

    String cloudfrontUrl = "https://" + cloudfrontDomain + "/" + s3Key;
    log.info("Successfully uploaded: {}", cloudfrontUrl);

    return cloudfrontUrl;
  }

  /**
   * Resize a BufferedImage to fit within the maximum dimension. This method
   * resizes the ORIGINAL
   * image format (JPG/PNG) before WebP conversion, avoiding the need to decode
   * WebP back to
   * BufferedImage which causes native library crashes. If the image is already
   * within the size
   * limits, it returns the original unchanged.
   *
   * @param originalImage The original BufferedImage to resize
   * @param metadata      The image metadata containing dimensions (will be
   *                      updated)
   * @param maxDimension  The maximum allowed dimension (width or height)
   * @return Resized BufferedImage, or original if no resize needed
   */
  private BufferedImage resizeImage(
      BufferedImage originalImage, Map<String, String> metadata, int maxDimension) {
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
      log.info(
          "Image is within size limits ({}x{}), no resize needed", originalWidth, originalHeight);
      return originalImage;
    }

    log.info(
        "Resizing image from {}x{} to {}x{}", originalWidth, originalHeight, newWidth, newHeight);

    // Create resized image with proper color model
    BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = resizedImage.createGraphics();
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
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
   * Convert a BufferedImage to WebP format with compression. This method accepts
   * an already-resized
   * BufferedImage, avoiding the need to decode WebP.
   *
   * @param bufferedImage The BufferedImage to convert
   * @return byte array containing the WebP image data
   * @throws IOException If there's an error during conversion
   */
  private byte[] convertJpgToWebP(BufferedImage bufferedImage) throws IOException {
    log.info(
        "Converting BufferedImage to WebP: {}x{}",
        bufferedImage.getWidth(),
        bufferedImage.getHeight());

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
  // CAMERA/LENS CREATION HELPERS
  // =============================================================================

  /**
   * Create or find a camera entity. Generates a random UUID serial number if not
   * provided. Checks
   * by serial number first (if provided), then by name.
   *
   * @param cameraName       The camera name (required)
   * @param bodySerialNumber Optional serial number from EXIF metadata
   * @param newCameras       Optional set to track newly created cameras (for
   *                         response metadata)
   * @return The camera entity (existing or newly created)
   */
  public ContentCameraEntity createCamera(
      String cameraName, String bodySerialNumber, Set<ContentCameraEntity> newCameras) {
    if (cameraName == null || cameraName.trim().isEmpty()) {
      throw new IllegalArgumentException("cameraName is required");
    }
    cameraName = cameraName.trim();

    // Generate UUID serial number if not provided
    String serialNumber = bodySerialNumber;
    if (serialNumber == null || serialNumber.trim().isEmpty()) {
      serialNumber = UUID.randomUUID().toString();
      log.debug("Generated UUID serial number for camera: {}", cameraName);
    } else {
      serialNumber = serialNumber.trim();
    }

    // Check by serial number first (for deduplication)
    Optional<ContentCameraEntity> existingBySerial = contentCameraDao.findByBodySerialNumber(serialNumber);
    if (existingBySerial.isPresent()) {
      log.debug("Found existing camera by serial number: {}", serialNumber);
      return existingBySerial.get();
    }

    // Check by name (case-insensitive)
    Optional<ContentCameraEntity> existingByName = contentCameraDao.findByCameraNameIgnoreCase(cameraName);
    if (existingByName.isPresent()) {
      log.debug("Found existing camera by name: {}", cameraName);
      return existingByName.get();
    }

    // Create new camera with generated serial number
    log.info("Creating new camera: {} (serial: {})", cameraName, serialNumber);
    ContentCameraEntity newCamera = ContentCameraEntity.builder().cameraName(cameraName).bodySerialNumber(serialNumber)
        .build();
    ContentCameraEntity savedCamera = contentCameraDao.save(newCamera);
    if (newCameras != null) {
      newCameras.add(savedCamera);
    }
    return savedCamera;
  }

  /**
   * Create or find a lens entity. Generates a random UUID serial number if not
   * provided. Checks by
   * serial number first (if provided), then by name.
   *
   * @param lensName         The lens name (required)
   * @param lensSerialNumber Optional serial number from EXIF metadata
   * @param newLenses        Optional set to track newly created lenses (for
   *                         response metadata)
   * @return The lens entity (existing or newly created)
   */
  public ContentLensEntity createLens(
      String lensName, String lensSerialNumber, Set<ContentLensEntity> newLenses) {
    if (lensName == null || lensName.trim().isEmpty()) {
      throw new IllegalArgumentException("lensName is required");
    }
    lensName = lensName.trim();

    // Generate UUID serial number if not provided
    String serialNumber = lensSerialNumber;
    if (serialNumber == null || serialNumber.trim().isEmpty()) {
      serialNumber = UUID.randomUUID().toString();
      log.debug("Generated UUID serial number for lens: {}", lensName);
    } else {
      serialNumber = serialNumber.trim();
    }

    // Check by serial number first (for deduplication)
    Optional<ContentLensEntity> existingBySerial = contentLensDao.findByLensSerialNumber(serialNumber);
    if (existingBySerial.isPresent()) {
      log.debug("Found existing lens by serial number: {}", serialNumber);
      return existingBySerial.get();
    }

    // Check by name (case-insensitive)
    Optional<ContentLensEntity> existingByName = contentLensDao.findByLensNameIgnoreCase(lensName);
    if (existingByName.isPresent()) {
      log.debug("Found existing lens by name: {}", lensName);
      return existingByName.get();
    }

    // Create new lens with generated serial number
    log.info("Creating new lens: {} (serial: {})", lensName, serialNumber);
    ContentLensEntity newLens = ContentLensEntity.builder().lensName(lensName).lensSerialNumber(serialNumber).build();
    ContentLensEntity savedLens = contentLensDao.save(newLens);
    if (newLenses != null) {
      newLenses.add(savedLens);
    }
    return savedLens;
  }

  // =============================================================================
  // IMAGE UPDATE HELPERS (following the pattern from CollectionProcessingUtil)
  // =============================================================================

  /**
   * Apply partial updates from ImageUpdateRequest to an ImageContentEntity. Only
   * fields provided in
   * the update request will be updated. This uses the new prev/new/remove pattern
   * for entity
   * relationships.
   *
   * @param entity        The image entity to update
   * @param updateRequest The update request containing the fields to update
   */
  public void applyImageUpdates(
      ContentImageEntity entity, ContentImageUpdateRequest updateRequest) {
    // Update basic image metadata fields if provided
    if (updateRequest.getTitle() != null) {
      entity.setTitle(updateRequest.getTitle());
    }
    if (updateRequest.getRating() != null) {
      entity.setRating(updateRequest.getRating());
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
    // filmFormat must also be provided in the update request (or already exist on
    // the entity)
    if (updateRequest.getIsFilm() != null && updateRequest.getIsFilm()) {
      contentImageUpdateValidator.validateFilmFormatRequired(
          updateRequest.getIsFilm(), updateRequest.getFilmFormat(), entity.getFilmFormat());
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
      } else if (cameraUpdate.getNewValue() != null
          && !cameraUpdate.getNewValue().trim().isEmpty()) {
        String cameraName = cameraUpdate.getNewValue().trim();
        // Use helper method - no serial number provided, will generate UUID
        ContentCameraEntity camera = createCamera(cameraName, null, null);
        entity.setCamera(camera);
      } else if (cameraUpdate.getPrev() != null) {
        // Use existing camera by ID
        ContentCameraEntity camera = contentCameraDao
            .findById(cameraUpdate.getPrev())
            .orElseThrow(
                () -> new IllegalArgumentException(
                    "Camera not found with ID: " + cameraUpdate.getPrev()));
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
        String lensName = lensUpdate.getNewValue().trim();
        // Use helper method - no serial number provided, will generate UUID
        ContentLensEntity lens = createLens(lensName, null, null);
        entity.setLens(lens);
      } else if (lensUpdate.getPrev() != null) {
        // Use existing lens by ID
        ContentLensEntity lens = contentLensDao
            .findById(lensUpdate.getPrev())
            .orElseThrow(
                () -> new IllegalArgumentException(
                    "Lens not found with ID: " + lensUpdate.getPrev()));
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
        // Generate technical name from display name: "Kodak Portra 400" ->
        // "KODAK_PORTRA_400"
        String technicalName = displayName.toUpperCase().replaceAll("\\s+", "_");

        ContentFilmTypeEntity filmType = contentFilmTypeDao
            .findByFilmTypeNameIgnoreCase(technicalName)
            .orElseGet(
                () -> {
                  log.info(
                      "Creating new film type: {} (technical name: {})",
                      displayName,
                      technicalName);
                  ContentFilmTypeEntity newFilmType = new ContentFilmTypeEntity(
                      technicalName, displayName, newFilmTypeRequest.getDefaultIso());
                  return contentFilmTypeDao.save(newFilmType);
                });
        entity.setFilmType(filmType);
      } else if (filmTypeUpdate.getPrev() != null) {
        // Use existing film type by ID
        ContentFilmTypeEntity filmType = contentFilmTypeDao
            .findById(filmTypeUpdate.getPrev())
            .orElseThrow(
                () -> new IllegalArgumentException(
                    "Film type not found with ID: " + filmTypeUpdate.getPrev()));
        entity.setFilmType(filmType);
      }
    }

    // Handle location update using prev/new/remove pattern
    if (updateRequest.getLocation() != null) {
      LocationUpdate locationUpdate = updateRequest.getLocation();

      if (Boolean.TRUE.equals(locationUpdate.getRemove())) {
        // Remove location association
        entity.setLocationId(null);
        log.info("Removed location association from image {}", entity.getId());
      } else if (locationUpdate.getNewValue() != null
          && !locationUpdate.getNewValue().trim().isEmpty()) {
        // Create new location by name
        String locationName = locationUpdate.getNewValue().trim();
        LocationEntity location = locationDao.findOrCreate(locationName);
        entity.setLocationId(location.getId());
        log.info("Set location to: {} (ID: {})", locationName, location.getId());
      } else if (locationUpdate.getPrev() != null) {
        // Use existing location by ID
        LocationEntity location = locationDao
            .findById(locationUpdate.getPrev())
            .orElseThrow(
                () -> new IllegalArgumentException(
                    "Location not found with ID: " + locationUpdate.getPrev()));
        entity.setLocationId(location.getId());
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

    // Note: Tag and person relationship updates are handled separately in the
    // service layer
    // Collection visibility updates are also handled separately in
    // handleCollectionVisibilityUpdates
  }

  /**
   * Handle collection visibility and orderIndex updates for an image. This method
   * updates the
   * 'visible' flag and 'orderIndex' for the content entry in the current
   * collection. Note: For
   * cross-collection updates (updating the same image in multiple collections),
   * you would need to
   * add a repository method to find content by fileIdentifier. For now, this
   * handles visibility and
   * orderIndex for the current image/collection relationship.
   *
   * <p>
   * Typically used for single-image updates where we're adjusting the orderIndex
   * (drag-and-drop
   * reordering) or toggling visibility within a specific collection. The API call
   * is very
   * lightweight: ContentImageUpdateRequest with a single
   * CollectionUpdate.prev(ChildCollection)
   * containing orderIndex/visible.
   *
   * @param image             The image entity being updated
   * @param collectionUpdates List of collection updates containing visibility and
   *                          orderIndex
   *                          information
   */
  public void handleContentChildCollectionUpdates(
      ContentImageEntity image, List<ChildCollection> collectionUpdates) {
    if (collectionUpdates == null || collectionUpdates.isEmpty()) {
      return;
    }

    // Update visibility and orderIndex for the current image if its collection is
    // in the updates
    for (ChildCollection collectionUpdate : collectionUpdates) {
      if (collectionUpdate.getCollectionId() != null) {
        Long collectionId = collectionUpdate.getCollectionId();
        Integer orderIndex = collectionUpdate.getOrderIndex();
        Boolean visible = collectionUpdate.getVisible();

        // Find the join table entry for this image in this collection
        Optional<CollectionContentEntity> joinEntryOpt = collectionContentDao
            .findByCollectionIdAndContentId(collectionId, image.getId());

        if (joinEntryOpt.isEmpty()) {
          log.warn(
              "No join table entry found for content {} in collection {}. Skipping update.",
              image.getId(),
              collectionId);
          continue;
        }

        CollectionContentEntity joinEntry = joinEntryOpt.get();
        boolean updated = false;

        // Update Order Index if provided
        if (orderIndex != null) {
          collectionContentDao.updateOrderIndex(
              joinEntry.getId(), // Use join table entry ID, not collection ID
              orderIndex);
          log.info(
              "Updated orderIndex for image {} in collection {} to {}",
              image.getId(),
              collectionId,
              orderIndex);
          updated = true;
        }

        // Update visibility if provided
        if (visible != null) {
          collectionContentDao.updateVisible(
              joinEntry.getId(), // Use join table entry ID, not collection ID
              visible);
          log.info(
              "Updated visibility for image {} in collection {} to {}",
              image.getId(),
              collectionId,
              visible);
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
   * Update tags on an entity using the prev/new/remove pattern. This is a shared
   * utility method
   * used by both Collection and Content update operations.
   *
   * @param currentTags Current set of tags on the entity
   * @param tagUpdate   The tag update containing remove/prev/newValue operations
   * @param newTags     Optional set to track newly created tags (for response
   *                    metadata)
   * @return Updated set of tags
   */
  public Set<ContentTagEntity> updateTags(
      Set<ContentTagEntity> currentTags, TagUpdate tagUpdate, Set<ContentTagEntity> newTags) {
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
          .map(
              tagId -> contentTagDao
                  .findById(tagId)
                  .orElseThrow(
                      () -> new IllegalArgumentException("Tag not found: " + tagId)))
          .collect(Collectors.toSet());
      tags.addAll(existingTags);
    }

    // Create and add new tags by name (newValue) with optional tracking
    if (tagUpdate.getNewValue() != null && !tagUpdate.getNewValue().isEmpty()) {
      for (String tagName : tagUpdate.getNewValue()) {
        if (tagName != null && !tagName.trim().isEmpty()) {
          String trimmedName = tagName.trim();
          var existing = contentTagDao.findByTagNameIgnoreCase(trimmedName);
          if (existing.isPresent()) {
            tags.add(existing.get());
          } else {
            ContentTagEntity newTag = new ContentTagEntity(trimmedName);
            newTag = contentTagDao.save(newTag);
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
   * Update people on an entity using the prev/new/remove pattern. This is a
   * shared utility method
   * used by both Collection and Content update operations.
   *
   * @param currentPeople Current set of people on the entity
   * @param personUpdate  The person update containing remove/prev/newValue
   *                      operations
   * @param newPeople     Optional set to track newly created people (for response
   *                      metadata)
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
          .map(
              personId -> contentPersonDao
                  .findById(personId)
                  .orElseThrow(
                      () -> new IllegalArgumentException("Person not found: " + personId)))
          .collect(Collectors.toSet());
      people.addAll(existingPeople);
    }

    // Create and add new people by name (newValue) with optional tracking
    if (personUpdate.getNewValue() != null && !personUpdate.getNewValue().isEmpty()) {
      for (String personName : personUpdate.getNewValue()) {
        if (personName != null && !personName.trim().isEmpty()) {
          String trimmedName = personName.trim();
          var existing = contentPersonDao.findByPersonNameIgnoreCase(trimmedName);
          if (existing.isPresent()) {
            people.add(existing.get());
          } else {
            ContentPersonEntity newPerson = new ContentPersonEntity(trimmedName);
            newPerson = contentPersonDao.save(newPerson);
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
   * Load tags for a content entity from the database. Populates the entity's tags
   * set with
   * ContentTagEntity objects.
   *
   * @param entity The content entity (ContentImageEntity or ContentGifEntity)
   */
  private void loadContentTags(ContentEntity entity) {
    if (entity == null || entity.getId() == null) {
      return;
    }

    // Load tags from database using TagDao
    List<TagEntity> tagEntities = tagDao.findContentTags(entity.getId());

    // Convert TagEntity to ContentTagEntity and populate the entity's tags set
    Set<ContentTagEntity> contentTagEntities = tagEntities.stream()
        .map(
            tagEntity -> {
              ContentTagEntity contentTag = new ContentTagEntity();
              contentTag.setId(tagEntity.getId());
              contentTag.setTagName(tagEntity.getTagName());
              contentTag.setCreatedAt(tagEntity.getCreatedAt());
              return contentTag;
            })
        .collect(Collectors.toSet());

    // Set tags on the entity based on its type
    if (entity instanceof ContentImageEntity imageEntity) {
      imageEntity.setTags(contentTagEntities);
    } else if (entity instanceof ContentGifEntity gifEntity) {
      gifEntity.setTags(contentTagEntities);
    }
  }

  /**
   * Load people for a content entity from the database. Populates the entity's
   * people set with
   * ContentPersonEntity objects.
   *
   * @param entity The content entity (ContentImageEntity)
   */
  private void loadContentPeople(ContentEntity entity) {
    if (entity == null || entity.getId() == null) {
      return;
    }

    // Load people from database using ContentPersonDao
    List<ContentPersonEntity> personEntities = contentPersonDao.findContentPeople(entity.getId());

    // Convert to set and populate the entity's people set
    Set<ContentPersonEntity> contentPeopleEntities = new HashSet<>(personEntities);

    // Set people on the entity based on its type
    if (entity instanceof ContentImageEntity imageEntity) {
      imageEntity.setPeople(contentPeopleEntities);
    }
  }

  /**
   * Convert a set of ContentTagEntity to a sorted list of ContentTagModel.
   * Returns empty list if
   * tags is null or empty.
   *
   * @param tags Set of tag entities to convert
   * @return Sorted list of tag models (alphabetically by name)
   */
  public List<ContentTagModel> convertTagsToModels(Set<ContentTagEntity> tags) {
    if (tags == null || tags.isEmpty()) {
      return new ArrayList<>();
    }
    return tags.stream()
        .map(tag -> ContentTagModel.builder().id(tag.getId()).name(tag.getTagName()).build())
        .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
        .collect(Collectors.toList());
  }

  /**
   * Convert a set of ContentPersonEntity to a sorted list of ContentPersonModel.
   * Returns empty list
   * if people is null or empty.
   *
   * @param people Set of person entities to convert
   * @return Sorted list of person models (alphabetically by name)
   */
  public List<ContentPersonModel> convertPeopleToModels(Set<ContentPersonEntity> people) {
    if (people == null || people.isEmpty()) {
      return new ArrayList<>();
    }
    return people.stream()
        .map(
            person -> ContentPersonModel.builder()
                .id(person.getId())
                .name(person.getPersonName())
                .build())
        .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
        .collect(Collectors.toList());
  }

  // =============================================================================
  // LOCATION HANDLING HELPERS
  // =============================================================================

  /**
   * Find or create a location by name. If the location exists (case-insensitive),
   * returns the
   * existing one. Otherwise, creates a new location and returns it.
   *
   * @param locationName The location name to find or create
   * @return The location entity, or null if locationName is null/empty
   */
  public LocationEntity findOrCreateLocation(String locationName) {
    return locationDao.findOrCreate(locationName);
  }

  /**
   * Get a location by ID.
   *
   * @param locationId The location ID
   * @return The location entity, or null if not found
   */
  public LocationEntity getLocationById(Long locationId) {
    if (locationId == null) {
      return null;
    }
    return locationDao.findById(locationId).orElse(null);
  }

  /**
   * Convert a LocationEntity to a LocationModel for API responses.
   *
   * @param location The location entity to convert
   * @return The location model, or null if location is null
   */
  public LocationModel convertLocationToModel(LocationEntity location) {
    if (location == null) {
      return null;
    }
    return LocationModel.builder().id(location.getId()).name(location.getLocationName()).build();
  }

  /**
   * Get a location name by ID. Useful for converting locationId to display
   * string.
   *
   * @param locationId The location ID
   * @return The location name, or null if not found or ID is null
   */
  public String getLocationNameById(Long locationId) {
    if (locationId == null) {
      return null;
    }
    return locationDao.findById(locationId).map(LocationEntity::getLocationName).orElse(null);
  }
}
