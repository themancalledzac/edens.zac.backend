package edens.zac.portfolio.backend.services;

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.properties.XMPProperty;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.xmp.XmpDirectory;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.EquipmentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentCameraEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentGifEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.ContentLensEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.entity.ContentTextEntity;
import edens.zac.portfolio.backend.entity.LocationEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.services.validator.ContentImageUpdateValidator;
import edens.zac.portfolio.backend.services.validator.ContentValidator;
import edens.zac.portfolio.backend.types.ContentType;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 * Utility class for processing content. Handles conversion between entities and models, content
 * validation, and specialized processing for different content types.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContentProcessingUtil {

  // Dependencies for S3 upload and repositories
  private final S3Client s3Client;
  private final ContentRepository contentRepository;
  private final CollectionRepository collectionRepository;
  private final EquipmentRepository equipmentRepository;
  private final TagRepository tagRepository;
  private final PersonRepository personRepository;
  private final LocationRepository locationRepository;
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
  private static final String PATH_GIF_THUMBNAIL = "Gif/Thumbnail";

  // Lightroom hierarchical subject XMP namespace
  private static final String NS_LIGHTROOM = "http://ns.adobe.com/lightroom/1.0/";

  // Keywords to filter out (already handled by BLACK_AND_WHITE / IS_FILM metadata fields)
  private static final Set<String> FILTERED_KEYWORDS =
      Set.of("monochrome", "blackandwhite", "black-and-white", "film");

  // Default values for image metadata
  public static final class DEFAULT {
    public static final String AUTHOR = "Zechariah Edens";
  }

  /** Tags and people extracted from image XMP keywords. */
  public record ExtractedKeywords(List<String> tags, List<String> people) {
    public static final ExtractedKeywords EMPTY = new ExtractedKeywords(List.of(), List.of());
  }

  /** Result of metadata extraction: technical fields plus keyword-based tags/people. */
  private record MetadataExtractionResult(
      Map<String, String> metadata, List<String> extractedTags, List<String> extractedPeople) {}

  /**
   * Convert a ContentEntity to its corresponding ContentModel based on type. This version does not
   * have collection-specific metadata (orderIndex, caption, visible). Use the overloaded version
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
      case IMAGE -> convertImageToModel((ContentImageEntity) entity, null, null);
      case TEXT -> convertTextToModel((ContentTextEntity) entity, null, null);
      case GIF -> convertGifToModel((ContentGifEntity) entity, null, null);
      case COLLECTION -> {
        log.warn(
            "COLLECTION content type not supported in convertRegularContentEntityToModel, entity id={}",
            entity.getId());
        yield null;
      }
    };
  }

  /**
   * Convert a ContentEntity to its corresponding ContentModel with join table metadata. This
   * version populates collection-specific fields (orderIndex, visible) from the join table.
   *
   * @param entity The join table entry (CollectionContentEntity) containing the content and
   *     metadata
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
    Optional<ContentEntity> baseContentOpt =
        contentRepository.findAllByIds(List.of(contentId)).stream().findFirst();
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
        Optional<ContentImageEntity> imageOpt = contentRepository.findImageById(contentId);
        content = imageOpt.orElse(null);
      }
      case TEXT -> {
        Optional<ContentTextEntity> textOpt = contentRepository.findTextById(contentId);
        content = textOpt.orElse(null);
      }
      case GIF -> {
        Optional<ContentGifEntity> gifOpt = contentRepository.findGifById(contentId);
        content = gifOpt.orElse(null);
      }
      case COLLECTION -> {
        Optional<ContentCollectionEntity> collectionOpt =
            contentRepository.findCollectionContentById(contentId);
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
   * Convert a bulk-loaded ContentEntity to its corresponding ContentModel with join table metadata.
   * This method is optimized for bulk-loaded entities that are already properly initialized (not
   * proxies). However, it defensively resolves proxies if needed, especially for COLLECTION types
   * which may still be proxies.
   *
   * @param content The bulk-loaded content entity (should be properly typed, but may still be a
   *     proxy)
   * @param joinEntry The join table entry containing collection-specific metadata (orderIndex,
   *     visible)
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

    Integer orderIndex = joinEntry.getOrderIndex();
    Boolean visible = joinEntry.getVisible();

    // For COLLECTION type, cast and convert
    if (content.getContentType() == ContentType.COLLECTION) {
      if (content instanceof ContentCollectionEntity contentCollectionEntity) {
        return convertCollectionToModel(contentCollectionEntity, joinEntry);
      } else {
        log.error(
            "Content type is COLLECTION but entity is not ContentCollectionEntity: {}",
            content.getClass());
        return null;
      }
    }

    return switch (content.getContentType()) {
      case IMAGE -> convertImageToModel((ContentImageEntity) content, orderIndex, visible);
      case TEXT -> convertTextToModel((ContentTextEntity) content, orderIndex, visible);
      case GIF -> convertGifToModel((ContentGifEntity) content, orderIndex, visible);
      case COLLECTION -> null; // handled above
    };
  }

  public static Records.Camera cameraEntityToCameraModel(ContentCameraEntity entity) {
    return new Records.Camera(entity.getId(), entity.getCameraName());
  }

  public static Records.Lens lensEntityToLensModel(ContentLensEntity entity) {
    return new Records.Lens(entity.getId(), entity.getLensName());
  }

  /**
   * Convert a ContentImageEntity to a ContentModels.Image. Public method for use by other utilities
   * (e.g., CollectionProcessingUtil). orderIndex and visible are null when the image is not fetched
   * in the context of a specific collection.
   *
   * @param entity The image content entity to convert
   * @return The corresponding image content model
   */
  public ContentModels.Image convertImageEntityToModel(ContentImageEntity entity) {
    return convertImageToModel(entity, null, null);
  }

  /**
   * Batch-convert a list of ContentImageEntity to models using pre-fetched related data. Eliminates
   * N+1 queries by batch-loading tags, people, and locations for all images in 3 queries total.
   *
   * @param entities The image entities to convert
   * @return List of converted image models
   */
  public List<ContentModels.Image> batchConvertImageEntitiesToModels(
      List<ContentImageEntity> entities) {
    if (entities == null || entities.isEmpty()) {
      return new ArrayList<>();
    }

    List<Long> contentIds = entities.stream().map(ContentImageEntity::getId).toList();

    List<Long> locationIds =
        entities.stream()
            .map(ContentImageEntity::getLocationId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    Map<Long, List<TagEntity>> tagsByContentId = tagRepository.findTagsByContentIds(contentIds);
    Map<Long, List<ContentPersonEntity>> peopleByContentId =
        personRepository.findPeopleByContentIds(contentIds);
    Map<Long, LocationEntity> locationsById = locationRepository.findByIds(locationIds);

    return entities.stream()
        .map(
            entity ->
                buildImageModelWithBatchData(
                    entity, null, null, tagsByContentId, peopleByContentId, locationsById))
        .toList();
  }

  /**
   * Build an image model using pre-fetched batch data instead of per-image queries.
   *
   * @param entity The image entity to convert
   * @param orderIndex Position within the parent collection (null outside collection context)
   * @param visible Visibility in the parent collection (null outside collection context)
   * @param tagsByContentId Pre-loaded tags grouped by content ID
   * @param peopleByContentId Pre-loaded people grouped by content ID
   * @param locationsById Pre-loaded locations keyed by location ID
   * @return The corresponding image content model
   */
  ContentModels.Image buildImageModelWithBatchData(
      ContentImageEntity entity,
      Integer orderIndex,
      Boolean visible,
      Map<Long, List<TagEntity>> tagsByContentId,
      Map<Long, List<ContentPersonEntity>> peopleByContentId,
      Map<Long, LocationEntity> locationsById) {
    if (entity == null) {
      return null;
    }

    Records.Location location = null;
    if (entity.getLocationId() != null) {
      LocationEntity locationEntity = locationsById.get(entity.getLocationId());
      if (locationEntity != null) {
        location =
            new Records.Location(
                locationEntity.getId(), locationEntity.getLocationName(), locationEntity.getSlug());
      }
    }

    Set<TagEntity> tags = new HashSet<>(tagsByContentId.getOrDefault(entity.getId(), List.of()));
    Set<ContentPersonEntity> people =
        new HashSet<>(peopleByContentId.getOrDefault(entity.getId(), List.of()));

    return new ContentModels.Image(
        entity.getId(),
        entity.getContentType(),
        entity.getTitle(),
        null,
        entity.getImageUrlWeb(),
        orderIndex,
        visible,
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getImageWidth(),
        entity.getImageHeight(),
        entity.getIso(),
        entity.getAuthor(),
        entity.getRating(),
        entity.getFStop(),
        entity.getLens() != null ? lensEntityToLensModel(entity.getLens()) : null,
        entity.getBlackAndWhite(),
        entity.getIsFilm(),
        entity.getFilmType() != null ? entity.getFilmType().getDisplayName() : null,
        entity.getFilmFormat(),
        entity.getShutterSpeed(),
        entity.getCamera() != null ? cameraEntityToCameraModel(entity.getCamera()) : null,
        entity.getFocalLength(),
        location,
        entity.getCaptureDate(),
        convertTagsToModels(tags),
        convertPeopleToModels(people),
        new ArrayList<>());
  }

  /**
   * Convert a ContentImageEntity to a ContentModels.Image record.
   *
   * @param entity The image content entity to convert
   * @param orderIndex Position within the parent collection (null outside collection context)
   * @param visible Visibility in the parent collection (null outside collection context)
   * @return The corresponding image content model
   */
  private ContentModels.Image convertImageToModel(
      ContentImageEntity entity, Integer orderIndex, Boolean visible) {
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

    Records.Location location = null;
    if (entity.getLocationId() != null) {
      LocationEntity locationEntity =
          locationRepository.findById(entity.getLocationId()).orElse(null);
      if (locationEntity != null) {
        location =
            new Records.Location(
                locationEntity.getId(), locationEntity.getLocationName(), locationEntity.getSlug());
      }
    }

    return new ContentModels.Image(
        entity.getId(),
        entity.getContentType(),
        entity.getTitle(),
        null,
        entity.getImageUrlWeb(),
        orderIndex,
        visible,
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getImageWidth(),
        entity.getImageHeight(),
        entity.getIso(),
        entity.getAuthor(),
        entity.getRating(),
        entity.getFStop(),
        entity.getLens() != null ? lensEntityToLensModel(entity.getLens()) : null,
        entity.getBlackAndWhite(),
        entity.getIsFilm(),
        entity.getFilmType() != null ? entity.getFilmType().getDisplayName() : null,
        entity.getFilmFormat(),
        entity.getShutterSpeed(),
        entity.getCamera() != null ? cameraEntityToCameraModel(entity.getCamera()) : null,
        entity.getFocalLength(),
        location,
        entity.getCaptureDate(),
        convertTagsToModels(entity.getTags()),
        convertPeopleToModels(entity.getPeople()),
        new ArrayList<>());
  }

  /**
   * Convert a ContentTextEntity to a ContentModels.Text record.
   *
   * @param entity The text content entity to convert
   * @param orderIndex Position within the parent collection (null outside collection context)
   * @param visible Visibility in the parent collection (null outside collection context)
   * @return The corresponding text content model
   */
  private ContentModels.Text convertTextToModel(
      ContentTextEntity entity, Integer orderIndex, Boolean visible) {
    if (entity == null) {
      return null;
    }

    return new ContentModels.Text(
        entity.getId(),
        entity.getContentType(),
        null,
        null,
        null,
        orderIndex,
        visible,
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getTextContent(),
        entity.getFormatType());
  }

  /**
   * Convert a ContentGifEntity to a ContentModels.Gif record.
   *
   * @param entity The gif content entity to convert
   * @param orderIndex Position within the parent collection (null outside collection context)
   * @param visible Visibility in the parent collection (null outside collection context)
   * @return The corresponding gif content model
   */
  private ContentModels.Gif convertGifToModel(
      ContentGifEntity entity, Integer orderIndex, Boolean visible) {
    if (entity == null) {
      return null;
    }

    // Load tags from database if not already loaded
    if (entity.getTags() == null || entity.getTags().isEmpty()) {
      loadContentTags(entity);
    }

    return new ContentModels.Gif(
        entity.getId(),
        entity.getContentType(),
        entity.getTitle(),
        null,
        null,
        orderIndex,
        visible,
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getGifUrl(),
        entity.getThumbnailUrl(),
        entity.getWidth(),
        entity.getHeight(),
        entity.getAuthor(),
        entity.getCreateDate(),
        convertTagsToModels(entity.getTags()));
  }

  /**
   * Convert a ContentCollectionEntity to a ContentModels.Collection. Extracts data from the
   * referenced collection and populates join table metadata (orderIndex, visible) from joinEntry.
   *
   * @param contentEntity The ContentCollectionEntity referencing another collection
   * @param joinEntry The join table entry with collection-specific metadata
   * @return The converted collection content model
   */
  private ContentModels.Collection convertCollectionToModel(
      ContentCollectionEntity contentEntity, CollectionContentEntity joinEntry) {
    if (contentEntity == null) {
      return null;
    }

    CollectionEntity referencedCollection = contentEntity.getReferencedCollection();
    if (referencedCollection == null) {
      log.error("ContentCollectionEntity {} has null referencedCollection", contentEntity.getId());
      return null;
    }

    // Load full collection data if only the ID is present (from bulk query)
    Long referencedCollectionId = referencedCollection.getId();
    if (referencedCollectionId != null && referencedCollection.getTitle() == null) {
      log.debug(
          "Loading full collection data for referencedCollectionId: {}", referencedCollectionId);
      referencedCollection =
          collectionRepository.findById(referencedCollectionId).orElse(referencedCollection);
    }

    ContentModels.Image coverImage = null;
    if (referencedCollection.getCoverImageId() != null) {
      ContentImageEntity coverImageEntity =
          contentRepository.findImageById(referencedCollection.getCoverImageId()).orElse(null);
      if (coverImageEntity != null) {
        coverImage = convertImageEntityToModel(coverImageEntity);
      }
    }

    return new ContentModels.Collection(
        contentEntity.getId(),
        contentEntity.getContentType(),
        referencedCollection.getTitle(),
        referencedCollection.getDescription(),
        null,
        joinEntry != null ? joinEntry.getOrderIndex() : null,
        joinEntry != null ? joinEntry.getVisible() : null,
        contentEntity.getCreatedAt(),
        contentEntity.getUpdatedAt(),
        referencedCollection.getId(),
        referencedCollection.getSlug(),
        referencedCollection.getType(),
        coverImage);
  }

  /**
   * Process and save a GIF/MP4 upload. Uploads the file to S3, extracts a first-frame WebP
   * thumbnail, and saves the entity to the database.
   *
   * @param file The GIF or MP4 file
   * @param title Optional title override (defaults to filename)
   * @return The saved ContentGifEntity
   */
  public ContentGifEntity processGifContent(MultipartFile file, String title) {
    log.info("Processing GIF/MP4 content: {}", file.getOriginalFilename());

    try {
      contentValidator.validateGifFile(file);

      byte[] fileBytes = file.getBytes();
      String originalFilename = file.getOriginalFilename();
      String contentType =
          file.getContentType() != null ? file.getContentType() : "application/octet-stream";

      LocalDate now = LocalDate.now();
      int year = now.getYear();
      int month = now.getMonthValue();

      // Upload raw file to S3
      String gifUrl =
          uploadImageToS3(fileBytes, originalFilename, contentType, PATH_GIF_FULL, year, month);

      // Extract first frame for WebP thumbnail
      BufferedImage firstFrame;
      if (contentValidator.isMp4File(file)) {
        firstFrame = extractFirstFrameViaFfmpeg(fileBytes, originalFilename);
      } else {
        try (InputStream is = new ByteArrayInputStream(fileBytes)) {
          firstFrame = ImageIO.read(is);
        }
      }

      String thumbnailUrl = null;
      Integer width = null;
      Integer height = null;

      if (firstFrame != null) {
        width = firstFrame.getWidth();
        height = firstFrame.getHeight();
        byte[] webpBytes = convertJpgToWebP(firstFrame);
        String thumbFilename = stripVideoExtension(originalFilename) + "-thumbnail.webp";
        thumbnailUrl =
            uploadImageToS3(
                webpBytes, thumbFilename, "image/webp", PATH_GIF_THUMBNAIL, year, month);
      } else {
        log.warn("Could not extract first frame from: {}", originalFilename);
      }

      // Build and save entity
      ContentGifEntity entity =
          ContentGifEntity.builder()
              .contentType(ContentType.GIF)
              .title(title != null && !title.isBlank() ? title : originalFilename)
              .gifUrl(gifUrl)
              .thumbnailUrl(thumbnailUrl)
              .width(width)
              .height(height)
              .author(DEFAULT.AUTHOR)
              .createDate(now.toString())
              .build();

      return contentRepository.saveGif(entity);

    } catch (IOException e) {
      log.error("Error processing GIF/MP4 content: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to process GIF/MP4 content", e);
    }
  }

  /**
   * Extract the first frame of an MP4/MOV video using ffmpeg. Writes the video bytes to a temp
   * file, runs ffmpeg to extract frame 1 as PNG, reads the result into a BufferedImage.
   *
   * @param videoBytes The raw video file bytes
   * @param filename The original filename (for logging)
   * @return BufferedImage of the first frame, or null if extraction fails
   */
  private BufferedImage extractFirstFrameViaFfmpeg(byte[] videoBytes, String filename)
      throws IOException {
    Path tempInput = Files.createTempFile("gif-upload-", "-" + filename);
    Path tempOutput = Files.createTempFile("gif-frame-", ".png");
    try {
      Files.write(tempInput, videoBytes);

      ProcessBuilder pb =
          new ProcessBuilder(
              "ffmpeg",
              "-y",
              "-i",
              tempInput.toAbsolutePath().toString(),
              "-vframes",
              "1",
              "-f",
              "image2",
              tempOutput.toAbsolutePath().toString());
      pb.redirectErrorStream(true);
      Process process = pb.start();

      String ffmpegOutput;
      try (InputStream is = process.getInputStream()) {
        ffmpegOutput = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      }

      int exitCode = process.waitFor();
      if (exitCode != 0) {
        log.error("ffmpeg exited with code {}: {}", exitCode, ffmpegOutput);
        return null;
      }

      return ImageIO.read(tempOutput.toFile());

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("ffmpeg was interrupted", e);
    } finally {
      Files.deleteIfExists(tempInput);
      Files.deleteIfExists(tempOutput);
    }
  }

  private String stripVideoExtension(String filename) {
    if (filename == null) {
      return "gif-upload-" + UUID.randomUUID();
    }
    return filename.replaceAll("(?i)\\.(mp4|mov|gif)$", "");
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
  // PARALLEL-SAFE IMAGE PROCESSING (no DB calls)
  // ============================================================================

  /**
   * Data holder for image preparation results. Contains all data needed to save to DB, but does NOT
   * hold any DB connections or entity references. Used by the parallel processing phase.
   */
  public record PreparedImageData(
      String originalFilename,
      String imageUrlOriginal,
      String imageUrlWeb,
      Map<String, String> metadata,
      List<String> extractedTags,
      List<String> extractedPeople,
      int imageYear,
      int imageMonth,
      LocalDateTime captureDate,
      LocalDateTime lastExportDate) {}

  /**
   * Prepare an image for upload: extract metadata, upload to S3, resize, convert to WebP. This
   * method does NO database calls and is safe to run in parallel virtual threads.
   *
   * @param file The image file to process
   * @return PreparedImageData with S3 URLs and metadata, ready for DB save
   * @throws IOException If there's an error processing the file
   */
  public PreparedImageData prepareImageForUpload(MultipartFile file) throws IOException {
    log.info("Preparing image for upload: {}", file.getOriginalFilename());

    // Extract metadata from original file (no DB calls)
    MetadataExtractionResult extraction = extractImageMetadata(file);
    Map<String, String> metadata = extraction.metadata();

    // Parse image capture date for S3 path organization
    int[] dateComponents = parseImageDate(metadata.get("createDate"), metadata.get("modifyDate"));
    int imageYear = dateComponents[0];
    int imageMonth = dateComponents[1];
    log.info("Image capture date: {}/{}", imageYear, String.format("%02d", imageMonth));

    // Upload original full-size image to S3
    String originalFilename = file.getOriginalFilename();
    String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
    String imageUrlOriginal =
        uploadImageToS3(
            file.getBytes(), originalFilename, contentType, PATH_IMAGE_FULL, imageYear, imageMonth);

    // Resize if needed (max 2500px on longest side)
    BufferedImage originalImage;
    try (InputStream imageStream = file.getInputStream()) {
      originalImage = ImageIO.read(imageStream);
    }
    if (originalImage == null) {
      throw new IOException("Failed to read image: " + originalFilename);
    }
    BufferedImage resizedImage = resizeImage(originalImage, metadata, 2500);

    // Convert to WebP
    byte[] processedImageBytes;
    String finalFilename;
    if (isJpgFile(file) || isWebPFile(file)) {
      processedImageBytes = convertJpgToWebP(resizedImage);
      if (originalFilename == null) {
        throw new IllegalArgumentException("Original filename must not be null");
      }
      finalFilename = originalFilename.replaceAll("(?i)\\.(jpg|jpeg|webp)$", ".webp");
    } else {
      throw new IOException("Unsupported file format. Only JPG and WebP are supported.");
    }

    // Upload web-optimized image to S3
    String imageUrlWeb =
        uploadImageToS3(
            processedImageBytes,
            finalFilename,
            "image/webp",
            PATH_IMAGE_WEB,
            imageYear,
            imageMonth);

    // Parse capture date for deduplication
    LocalDateTime captureDate = parseExifDateToLocalDateTime(metadata.get("createDate"));

    // Use file last-modified as export date (approximation for dedupe)
    LocalDateTime lastExportDate = LocalDateTime.now();

    log.info("Image prepared successfully: {}", originalFilename);
    return new PreparedImageData(
        originalFilename,
        imageUrlOriginal,
        imageUrlWeb,
        metadata,
        extraction.extractedTags(),
        extraction.extractedPeople(),
        imageYear,
        imageMonth,
        captureDate,
        lastExportDate);
  }

  /**
   * Result of dedupe-aware save. Indicates whether the image was created, updated, or skipped.
   *
   * @param entity The saved entity (null if skipped)
   * @param action CREATE, UPDATE, or SKIP
   */
  public record DedupeResult(ContentImageEntity entity, DedupeAction action) {}

  public enum DedupeAction {
    CREATE,
    UPDATE,
    SKIP
  }

  public DedupeResult savePreparedImageWithDedupe(PreparedImageData prepared, String title) {
    Map<String, String> metadata = prepared.metadata();
    String webFilename = prepared.originalFilename().replaceAll("(?i)\\.(jpg|jpeg|webp)$", ".webp");

    // Check for existing image by filename + capture date
    if (prepared.originalFilename() != null && prepared.captureDate() != null) {
      Optional<ContentImageEntity> existingOpt =
          contentRepository.findByOriginalFilenameAndCaptureDate(
              prepared.originalFilename(), prepared.captureDate());

      if (existingOpt.isPresent()) {
        ContentImageEntity existing = existingOpt.get();

        // Compare export dates: if existing has no export date, or same/older, skip
        if (existing.getLastExportDate() == null
            || (prepared.lastExportDate() != null
                && !prepared.lastExportDate().isAfter(existing.getLastExportDate()))) {
          log.info(
              "Skipping duplicate image (id={}) for {}: same or older export",
              existing.getId(),
              prepared.originalFilename());
          return new DedupeResult(existing, DedupeAction.SKIP);
        }

        // Newer export: update entity in DB first, then delete old S3 files
        log.info(
            "Updating existing image (id={}) for {}: newer export detected",
            existing.getId(),
            prepared.originalFilename());

        // Capture old URLs before overwriting
        String oldImageUrlWeb = existing.getImageUrlWeb();
        String oldImageUrlOriginal = existing.getImageUrlOriginal();

        existing.setImageUrlOriginal(prepared.imageUrlOriginal());
        existing.setImageUrlWeb(prepared.imageUrlWeb());
        existing.setLastExportDate(prepared.lastExportDate());
        existing.setImageWidth(parseIntegerOrDefault(metadata.get("imageWidth"), 0));
        existing.setImageHeight(parseIntegerOrDefault(metadata.get("imageHeight"), 0));

        // Save DB first -- if this fails, old S3 files remain valid
        ContentImageEntity savedEntity = contentRepository.saveImage(existing);

        // Now safe to delete old S3 files (DB already points to new URLs)
        deleteS3ObjectByUrl(oldImageUrlWeb);
        deleteS3ObjectByUrl(oldImageUrlOriginal);

        return new DedupeResult(savedEntity, DedupeAction.UPDATE);
      }
    }

    // No duplicate found: create new entity
    ContentImageEntity entity =
        ContentImageEntity.builder()
            .contentType(ContentType.IMAGE)
            .title(title != null ? title : metadata.getOrDefault("title", webFilename))
            .imageWidth(parseIntegerOrDefault(metadata.get("imageWidth"), 0))
            .imageHeight(parseIntegerOrDefault(metadata.get("imageHeight"), 0))
            .iso(parseIntegerOrDefault(metadata.get("iso"), null))
            .author(metadata.getOrDefault("author", DEFAULT.AUTHOR))
            .rating(parseIntegerOrDefault(metadata.get("rating"), null))
            .fStop(metadata.get("fStop"))
            .blackAndWhite(parseBooleanOrDefault(metadata.get("blackAndWhite"), false))
            .isFilm(parseBooleanOrDefault(metadata.get("isFilm"), false))
            .shutterSpeed(metadata.get("shutterSpeed"))
            .imageUrlOriginal(prepared.imageUrlOriginal())
            .focalLength(metadata.get("focalLength"))
            .locationId(
                metadata.get("location") != null
                    ? locationRepository.findOrCreate(metadata.get("location")).getId()
                    : null)
            .imageUrlWeb(prepared.imageUrlWeb())
            .captureDate(prepared.captureDate())
            .lastExportDate(prepared.lastExportDate())
            .originalFilename(prepared.originalFilename())
            .createdAt(parseExifDateToLocalDateTime(metadata.get("createDate")))
            .build();

    // Handle camera
    String cameraName = metadata.get("camera");
    String bodySerialNumber = metadata.get("bodySerialNumber");
    if (cameraName != null && !cameraName.trim().isEmpty()) {
      entity.setCamera(createCamera(cameraName, bodySerialNumber, null));
    }

    // Handle lens
    String lensName = metadata.get("lens");
    String lensSerialNumber = metadata.get("lensSerialNumber");
    if (lensName != null && !lensName.trim().isEmpty()) {
      entity.setLens(createLens(lensName, lensSerialNumber, null));
    }

    ContentImageEntity savedEntity = contentRepository.saveImage(entity);
    log.info("Successfully created new image content with ID: {}", savedEntity.getId());
    return new DedupeResult(savedEntity, DedupeAction.CREATE);
  }

  // ============================================================================
  // ORIGINAL IMAGE PROCESSING (sequential, used by createImages)
  // ============================================================================

  /**
   * STREAMLINED: Process and save an image content.
   *
   * <p>NOTE: This method only creates the ContentImageEntity. The caller is responsible for
   * creating the CollectionContentEntity to link this image to a collection.
   *
   * <p>Flow: 1. Extract metadata from original file 2. Upload original full-size image to S3 3.
   * Convert JPG -> WebP (with compression) 4. Resize if needed 5. Upload web-optimized image to S3
   * 6. Save metadata with both URLs to database 7. Return ImageContentEntity
   *
   * @param file The image file to process
   * @param title The title of the image (optional, will use filename if null)
   * @return The saved image content entity
   */
  public ContentImageEntity processImageContent(MultipartFile file, String title)
      throws IOException {
    log.info("Processing image content: {}", file.getOriginalFilename());
    PreparedImageData prepared = prepareImageForUpload(file);
    DedupeResult result = savePreparedImageWithDedupe(prepared, title);

    // Auto-associate tags and people extracted from XMP keywords (only on new images)
    if (result.action() == DedupeAction.CREATE) {
      associateExtractedKeywords(
          result.entity().getId(), prepared.extractedTags(), prepared.extractedPeople());
    }

    log.info("Successfully processed image content with ID: {}", result.entity().getId());
    return result.entity();
  }

  /**
   * Extract metadata from an image file using Drew Noakes metadata-extractor. Uses the
   * ImageMetadata enum system for consistent, maintainable field extraction. Also extracts tags and
   * people from XMP keyword arrays (lr:hierarchicalSubject / dc:subject).
   *
   * @param file The image file to extract metadata from
   * @return MetadataExtractionResult with technical metadata map plus extracted tags/people
   * @throws IOException If there's an error reading the file
   */
  private MetadataExtractionResult extractImageMetadata(MultipartFile file) throws IOException {
    Map<String, String> metadata = new HashMap<>();
    ExtractedKeywords keywords = ExtractedKeywords.EMPTY;

    try (InputStream inputStream = file.getInputStream()) {
      Metadata imageMetadata = ImageMetadataReader.readMetadata(inputStream);

      // Extract EXIF metadata for all defined fields
      for (Directory directory : imageMetadata.getDirectories()) {
        for (Tag tag : directory.getTags()) {
          extractFromExifTag(tag, metadata);
        }
      }

      // Extract XMP metadata for all defined fields + keywords/people
      for (XmpDirectory xmpDirectory : imageMetadata.getDirectoriesOfType(XmpDirectory.class)) {
        extractFromXmpDirectory(xmpDirectory, metadata);

        // Extract tags and people from XMP keyword arrays (stop after first non-empty result)
        if (keywords.tags().isEmpty() && keywords.people().isEmpty()) {
          try {
            XMPMeta xmpMeta = xmpDirectory.getXMPMeta();
            if (xmpMeta != null) {
              keywords = extractTagsAndPeopleFromXmp(xmpMeta);
            }
          } catch (Exception e) {
            log.warn(
                "Failed to extract keywords from XMP for {}: {}",
                file.getOriginalFilename(),
                e.getMessage());
          }
        }
      }

      if (!metadata.containsKey("createDate")) {
        log.warn("No capture date found in EXIF or XMP for file: {}", file.getOriginalFilename());
      }

      // Fallback: Get dimensions from BufferedImage if not found
      ensureDimensions(file, metadata);

      log.info("Extracted metadata: {} fields", metadata.size());
      log.info("Final rating value: {}", metadata.getOrDefault("rating", "NULL"));
      if (!keywords.tags().isEmpty() || !keywords.people().isEmpty()) {
        log.info(
            "Extracted {} tags and {} people from XMP keywords",
            keywords.tags().size(),
            keywords.people().size());
      }

    } catch (Exception e) {
      log.error(
          "Failed to extract full metadata for {}: {}",
          file.getOriginalFilename(),
          e.getMessage(),
          e);
      ensureDimensions(file, metadata);
    }

    return new MetadataExtractionResult(metadata, keywords.tags(), keywords.people());
  }

  /**
   * Extract metadata from a single EXIF tag using the ImageMetadata enum configuration.
   *
   * @param tag The EXIF tag to process
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
   * Extract metadata from XMP directory using the ImageMetadata enum configuration.
   *
   * @param xmpDirectory The XMP directory to process
   * @param metadata The metadata map to populate
   */
  private void extractFromXmpDirectory(XmpDirectory xmpDirectory, Map<String, String> metadata) {
    XMPMeta xmpMeta = xmpDirectory.getXMPMeta();

    // Try each metadata field
    for (ImageMetadata.MetadataField field : ImageMetadata.MetadataField.values()) {
      ImageMetadata.XmpProperty xmpProperty = field.getXmpProperty();

      if (!xmpProperty.hasProperties()) {
        continue;
      }

      // Try each (namespace, propertyName) pair in priority order
      for (ImageMetadata.XmpProperty.NamespaceProp entry : xmpProperty.getEntries()) {
        try {
          XMPProperty prop = xmpMeta.getProperty(entry.namespace(), entry.propertyName());

          if (prop != null && prop.getValue() != null) {
            // Only set if not already extracted from EXIF
            if (!metadata.containsKey(field.getFieldName())) {
              String extractedValue = field.getExtractor().extract(prop.getValue());
              if (extractedValue != null) {
                metadata.put(field.getFieldName(), extractedValue);
                break; // Found value, stop trying further fallbacks
              }
            }
          }
        } catch (XMPException e) {
          log.debug(
              "XMP extraction failed for {}/{} (code {}): {}",
              entry.namespace(),
              entry.propertyName(),
              e.getErrorCode(),
              e.getMessage());
        }
      }
    }
  }

  /**
   * Extract all items from an XMP array property (bag or sequence).
   *
   * @param xmpMeta The XMP metadata object
   * @param namespace The XMP namespace URI
   * @param propertyName The array property name
   * @return List of string values, empty list if property is absent or on error
   */
  private List<String> extractXmpArrayItems(
      XMPMeta xmpMeta, String namespace, String propertyName) {
    List<String> items = new ArrayList<>();
    try {
      int count = xmpMeta.countArrayItems(namespace, propertyName);
      for (int i = 1; i <= count; i++) {
        XMPProperty item = xmpMeta.getArrayItem(namespace, propertyName, i);
        if (item != null && item.getValue() != null && !item.getValue().isBlank()) {
          items.add(item.getValue().trim());
        }
      }
    } catch (XMPException e) {
      log.debug(
          "XMP array extraction failed for {}/{}: {}", namespace, propertyName, e.getMessage());
    }
    return items;
  }

  /**
   * Extract tags and people from XMP keyword arrays. Uses lr:hierarchicalSubject to distinguish
   * people (under "People" parent) from tags. Falls back to dc:subject (flat keywords, all become
   * tags) if hierarchical subjects are not present.
   *
   * @param xmpMeta The XMP metadata object
   * @return ExtractedKeywords with separated tag and people name lists
   */
  private ExtractedKeywords extractTagsAndPeopleFromXmp(XMPMeta xmpMeta) {
    // Try hierarchical subjects first (Lightroom writes these with category parents)
    List<String> hierarchicalSubjects =
        extractXmpArrayItems(xmpMeta, NS_LIGHTROOM, "hierarchicalSubject");

    if (!hierarchicalSubjects.isEmpty()) {
      List<String> tags = new ArrayList<>();
      List<String> people = new ArrayList<>();

      for (String subject : hierarchicalSubjects) {
        if (subject.toLowerCase().startsWith("people|")) {
          // "People|Jane Doe" → person "Jane Doe"
          String personName = subject.substring("people|".length()).trim();
          if (!personName.isEmpty()) {
            people.add(personName);
          }
        } else {
          // "Weather|sunset" → tag "sunset" (leaf segment)
          String leaf =
              subject.contains("|")
                  ? subject.substring(subject.lastIndexOf('|') + 1).trim()
                  : subject.trim();
          if (!leaf.isEmpty() && !FILTERED_KEYWORDS.contains(leaf.toLowerCase())) {
            tags.add(leaf);
          }
        }
      }

      return new ExtractedKeywords(tags, people);
    }

    // Fallback: flat dc:subject — all become tags, no people distinction
    List<String> dcSubjects =
        extractXmpArrayItems(xmpMeta, com.adobe.internal.xmp.XMPConst.NS_DC, "subject");

    List<String> tags =
        dcSubjects.stream()
            .filter(s -> !s.isBlank())
            .map(String::trim)
            .filter(s -> !FILTERED_KEYWORDS.contains(s.toLowerCase()))
            .toList();

    return new ExtractedKeywords(tags, List.of());
  }

  /**
   * Ensure dimensions are present in metadata, reading from BufferedImage if needed.
   *
   * @param file The image file
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
   * Parse year and month from EXIF date string. EXIF date format is typically "2024:05:15
   * 14:30:00".
   *
   * @param createDate The capture date string from EXIF/XMP metadata
   * @param modifyDate The modify date string (Lightroom export date), used as fallback
   * @return int[] {year, month} or current date as last resort
   */
  int[] parseImageDate(String createDate, String modifyDate) {
    if (createDate != null && !createDate.isEmpty()) {
      try {
        // EXIF format: "2024:05:15 14:30:00" or ISO-8601: "2024-05-15T14:30:00"
        String[] parts = createDate.split("[: T-]");
        return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
      } catch (Exception e) {
        log.warn("Failed to parse capture date '{}', trying modify date", createDate);
      }
    }
    if (modifyDate != null && !modifyDate.isEmpty()) {
      try {
        String[] parts = modifyDate.split("[: T-]");
        log.info("Using modify date for S3 path: {}/{}", parts[0], parts[1]);
        return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
      } catch (Exception e) {
        log.warn("Failed to parse modify date '{}', using current date", modifyDate);
      }
    }
    log.warn("No valid date for S3 path, using current date");
    LocalDate now = LocalDate.now();
    return new int[] {now.getYear(), now.getMonthValue()};
  }

  LocalDateTime parseExifDateToLocalDateTime(String createDate) {
    if (createDate == null || createDate.trim().isEmpty()) {
      return null;
    }
    try {
      // EXIF format: "2026:01:26 17:48:38" -> convert to "2026-01-26T17:48:38"
      // Replace first two colons (in date part) with dashes, then space with T
      String normalized =
          createDate.replaceFirst(":", "-").replaceFirst(":", "-").replace(" ", "T");
      return LocalDateTime.parse(normalized);
    } catch (Exception e) {
      log.warn("Failed to parse EXIF date '{}' to LocalDateTime, date will be null", createDate);
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
   * <p>Path structure: {basePath}/{year}/{month}/{filename} Example: Image/Full/2025/08/photo.jpg
   *
   * @param imageBytes The image bytes to upload
   * @param filename The filename
   * @param contentType The content type of the image (e.g., "image/jpeg", "image/webp")
   * @param basePath The base path for S3 (e.g., PATH_IMAGE_FULL, PATH_IMAGE_WEB)
   * @param year The year from image capture date
   * @param month The month from image capture date (1-12)
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

    PutObjectRequest putRequest =
        PutObjectRequest.builder()
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
   * Delete an image and its variants from S3.
   *
   * @param image The ContentImageEntity containing S3 URLs to delete
   */
  public void deleteImageFromS3(ContentImageEntity image) {
    deleteS3ObjectByUrl(image.getImageUrlWeb());
    deleteS3ObjectByUrl(image.getImageUrlOriginal());
  }

  /** Delete a single S3 object by its CloudFront URL. Logs but does not throw on failure. */
  private void deleteS3ObjectByUrl(String url) {
    if (url == null) {
      return;
    }
    try {
      String s3Key = extractS3KeyFromUrl(url);
      if (s3Key != null) {
        log.info("Deleting from S3: {}", s3Key);
        s3Client.deleteObject(builder -> builder.bucket(bucketName).key(s3Key));
        log.info("Successfully deleted from S3: {}", s3Key);
      }
    } catch (Exception e) {
      log.error("Failed to delete S3 object {}: {}", url, e.getMessage());
    }
  }

  /**
   * Extract S3 key from CloudFront URL.
   *
   * @param url The CloudFront URL (e.g., "https://cloudfront.domain/Image/Web/2024/01/file.webp")
   * @return The S3 key (e.g., "Image/Web/2024/01/file.webp") or null if invalid
   */
  private String extractS3KeyFromUrl(String url) {
    if (url == null || url.isEmpty()) {
      return null;
    }

    // Remove the CloudFront domain prefix
    String prefix = "https://" + cloudfrontDomain + "/";
    if (url.startsWith(prefix)) {
      return url.substring(prefix.length());
    }

    log.warn("URL does not match expected CloudFront format: {}", url);
    return null;
  }

  /**
   * Resize a BufferedImage to fit within the maximum dimension. This method resizes the ORIGINAL
   * image format (JPG/PNG) before WebP conversion, avoiding the need to decode WebP back to
   * BufferedImage which causes native library crashes. If the image is already within the size
   * limits, it returns the original unchanged.
   *
   * @param originalImage The original BufferedImage to resize
   * @param metadata The image metadata containing dimensions (will be updated)
   * @param maxDimension The maximum allowed dimension (width or height)
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
   * Convert a BufferedImage to WebP format with compression. This method accepts an already-resized
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
   * @param value The string value to parse
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
   * @param value The string value to parse
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
   * Create or find a camera entity. Generates a random UUID serial number if not provided. Checks
   * by serial number first (if provided), then by name.
   *
   * @param cameraName The camera name (required)
   * @param bodySerialNumber Optional serial number from EXIF metadata
   * @param newCameras Optional set to track newly created cameras (for response metadata)
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
    Optional<ContentCameraEntity> existingBySerial =
        equipmentRepository.findCameraByBodySerialNumber(serialNumber);
    if (existingBySerial.isPresent()) {
      log.debug("Found existing camera by serial number: {}", serialNumber);
      return existingBySerial.get();
    }

    // Check by name (case-insensitive)
    Optional<ContentCameraEntity> existingByName =
        equipmentRepository.findCameraByNameIgnoreCase(cameraName);
    if (existingByName.isPresent()) {
      log.debug("Found existing camera by name: {}", cameraName);
      return existingByName.get();
    }

    // Create new camera with generated serial number
    log.info("Creating new camera: {} (serial: {})", cameraName, serialNumber);
    ContentCameraEntity newCamera =
        ContentCameraEntity.builder().cameraName(cameraName).bodySerialNumber(serialNumber).build();
    ContentCameraEntity savedCamera = equipmentRepository.saveCamera(newCamera);
    if (newCameras != null) {
      newCameras.add(savedCamera);
    }
    return savedCamera;
  }

  /**
   * Create or find a lens entity. Generates a random UUID serial number if not provided. Checks by
   * serial number first (if provided), then by name.
   *
   * @param lensName The lens name (required)
   * @param lensSerialNumber Optional serial number from EXIF metadata
   * @param newLenses Optional set to track newly created lenses (for response metadata)
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
    Optional<ContentLensEntity> existingBySerial =
        equipmentRepository.findLensBySerialNumber(serialNumber);
    if (existingBySerial.isPresent()) {
      log.debug("Found existing lens by serial number: {}", serialNumber);
      return existingBySerial.get();
    }

    // Check by name (case-insensitive)
    Optional<ContentLensEntity> existingByName =
        equipmentRepository.findLensByNameIgnoreCase(lensName);
    if (existingByName.isPresent()) {
      log.debug("Found existing lens by name: {}", lensName);
      return existingByName.get();
    }

    // Create new lens with generated serial number
    log.info("Creating new lens: {} (serial: {})", lensName, serialNumber);
    ContentLensEntity newLens =
        ContentLensEntity.builder().lensName(lensName).lensSerialNumber(serialNumber).build();
    ContentLensEntity savedLens = equipmentRepository.saveLens(newLens);
    if (newLenses != null) {
      newLenses.add(savedLens);
    }
    return savedLens;
  }

  // =============================================================================
  // IMAGE UPDATE HELPERS (following the pattern from CollectionProcessingUtil)
  // =============================================================================

  /**
   * Handle collection visibility and orderIndex updates for an image. This method updates the
   * 'visible' flag and 'orderIndex' for the content entry in the current collection. Note: For
   * cross-collection updates (updating the same image in multiple collections), you would need to
   * add a repository method to find content by fileIdentifier. For now, this handles visibility and
   * orderIndex for the current image/collection relationship.
   *
   * <p>Typically used for single-image updates where we're adjusting the orderIndex (drag-and-drop
   * reordering) or toggling visibility within a specific collection. The API call is very
   * lightweight: ContentImageUpdateRequest with a single
   * CollectionUpdate.prev(Records.ChildCollection) containing orderIndex/visible.
   *
   * @param image The image entity being updated
   * @param collectionUpdates List of collection updates containing visibility and orderIndex
   *     information
   */
  public void handleContentChildCollectionUpdates(
      ContentImageEntity image, List<Records.ChildCollection> collectionUpdates) {
    if (collectionUpdates == null || collectionUpdates.isEmpty()) {
      return;
    }

    // Update visibility and orderIndex for the current image if its collection is
    // in the updates
    for (Records.ChildCollection collectionUpdate : collectionUpdates) {
      if (collectionUpdate.collectionId() != null) {
        Long collectionId = collectionUpdate.collectionId();
        Integer orderIndex = collectionUpdate.orderIndex();
        Boolean visible = collectionUpdate.visible();

        // Find the join table entry for this image in this collection
        Optional<CollectionContentEntity> joinEntryOpt =
            collectionRepository.findContentByCollectionIdAndContentId(collectionId, image.getId());

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
          collectionRepository.updateContentOrderIndex(
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
          collectionRepository.updateContentVisible(
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
   * Associate tags and people extracted from image XMP metadata with a saved image. Creates new
   * tag/person entities if they don't already exist (case-insensitive dedup). Failures are logged
   * but do not propagate — the image save is not affected.
   *
   * @param imageId The saved image entity ID
   * @param tagNames Tag names extracted from XMP keywords
   * @param peopleNames Person names extracted from XMP keywords
   */
  public void associateExtractedKeywords(
      Long imageId, List<String> tagNames, List<String> peopleNames) {
    if ((tagNames == null || tagNames.isEmpty())
        && (peopleNames == null || peopleNames.isEmpty())) {
      return;
    }

    try {
      // Associate tags (deduplicate names to avoid redundant DB lookups)
      if (tagNames != null && !tagNames.isEmpty()) {
        Set<Long> tagIds = new LinkedHashSet<>();
        Set<String> seenTags = new HashSet<>();
        for (String tagName : tagNames) {
          if (!seenTags.add(tagName.toLowerCase())) {
            continue;
          }
          var existing = tagRepository.findByTagNameIgnoreCase(tagName);
          if (existing.isPresent()) {
            tagIds.add(existing.get().getId());
          } else {
            TagEntity newTag = tagRepository.save(new TagEntity(tagName));
            tagIds.add(newTag.getId());
            log.info("Created new tag from XMP keyword: {}", tagName);
          }
        }
        tagRepository.saveContentTags(imageId, new ArrayList<>(tagIds));
        log.info("Associated {} tags with image {}", tagIds.size(), imageId);
      }

      // Associate people (deduplicate names to avoid redundant DB lookups)
      if (peopleNames != null && !peopleNames.isEmpty()) {
        Set<Long> personIds = new LinkedHashSet<>();
        Set<String> seenPeople = new HashSet<>();
        for (String personName : peopleNames) {
          if (!seenPeople.add(personName.toLowerCase())) {
            continue;
          }
          var existing = personRepository.findByPersonNameIgnoreCase(personName);
          if (existing.isPresent()) {
            personIds.add(existing.get().getId());
          } else {
            ContentPersonEntity newPerson =
                personRepository.save(new ContentPersonEntity(personName));
            personIds.add(newPerson.getId());
            log.info("Created new person from XMP keyword: {}", personName);
          }
        }
        contentRepository.saveImagePeople(imageId, new ArrayList<>(personIds));
        log.info("Associated {} people with image {}", personIds.size(), imageId);
      }
    } catch (Exception e) {
      log.warn(
          "Failed to associate extracted keywords with image {}: {}", imageId, e.getMessage(), e);
    }
  }

  /**
   * Update tags on an entity using the prev/new/remove pattern. This is a shared utility method
   * used by both Collection and Content update operations.
   *
   * @param currentTags Current set of tags on the entity
   * @param tagUpdate The tag update containing remove/prev/newValue operations
   * @param newTags Optional set to track newly created tags (for response metadata)
   * @return Updated set of tags
   */
  public Set<TagEntity> updateTags(
      Set<TagEntity> currentTags, CollectionRequests.TagUpdate tagUpdate, Set<TagEntity> newTags) {
    if (tagUpdate == null) {
      return currentTags;
    }

    Set<TagEntity> tags = new HashSet<>(currentTags);

    // Remove tags if specified
    if (tagUpdate.remove() != null && !tagUpdate.remove().isEmpty()) {
      tags.removeIf(tag -> tagUpdate.remove().contains(tag.getId()));
    }

    // Add existing tags by ID (prev)
    if (tagUpdate.prev() != null && !tagUpdate.prev().isEmpty()) {
      Set<TagEntity> existingTags =
          tagUpdate.prev().stream()
              .map(
                  tagId ->
                      tagRepository
                          .findById(tagId)
                          .orElseThrow(
                              () -> new IllegalArgumentException("Tag not found: " + tagId)))
              .collect(Collectors.toSet());
      tags.addAll(existingTags);
    }

    // Create and add new tags by name (newValue) with optional tracking
    if (tagUpdate.newValue() != null && !tagUpdate.newValue().isEmpty()) {
      for (String tagName : tagUpdate.newValue()) {
        if (tagName != null && !tagName.trim().isEmpty()) {
          String trimmedName = tagName.trim();
          var existing = tagRepository.findByTagNameIgnoreCase(trimmedName);
          if (existing.isPresent()) {
            tags.add(existing.get());
          } else {
            TagEntity newTag = new TagEntity(trimmedName);
            newTag = tagRepository.save(newTag);
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
   * Update people on an entity using the prev/new/remove pattern. This is a shared utility method
   * used by both Collection and Content update operations.
   *
   * @param currentPeople Current set of people on the entity
   * @param personUpdate The person update containing remove/prev/newValue operations
   * @param newPeople Optional set to track newly created people (for response metadata)
   * @return Updated set of people
   */
  public Set<ContentPersonEntity> updatePeople(
      Set<ContentPersonEntity> currentPeople,
      CollectionRequests.PersonUpdate personUpdate,
      Set<ContentPersonEntity> newPeople) {
    if (personUpdate == null) {
      return currentPeople;
    }

    Set<ContentPersonEntity> people = new HashSet<>(currentPeople);

    // Remove people if specified
    if (personUpdate.remove() != null && !personUpdate.remove().isEmpty()) {
      people.removeIf(person -> personUpdate.remove().contains(person.getId()));
    }

    // Add existing people by ID (prev)
    if (personUpdate.prev() != null && !personUpdate.prev().isEmpty()) {
      Set<ContentPersonEntity> existingPeople =
          personUpdate.prev().stream()
              .map(
                  personId ->
                      personRepository
                          .findById(personId)
                          .orElseThrow(
                              () -> new IllegalArgumentException("Person not found: " + personId)))
              .collect(Collectors.toSet());
      people.addAll(existingPeople);
    }

    // Create and add new people by name (newValue) with optional tracking
    if (personUpdate.newValue() != null && !personUpdate.newValue().isEmpty()) {
      for (String personName : personUpdate.newValue()) {
        if (personName != null && !personName.trim().isEmpty()) {
          String trimmedName = personName.trim();
          var existing = personRepository.findByPersonNameIgnoreCase(trimmedName);
          if (existing.isPresent()) {
            people.add(existing.get());
          } else {
            ContentPersonEntity newPerson = new ContentPersonEntity(trimmedName);
            newPerson = personRepository.save(newPerson);
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
   * Load tags for a content entity from the database. Populates the entity's tags set with
   * TagEntity objects.
   *
   * @param entity The content entity (ContentImageEntity or ContentGifEntity)
   */
  private void loadContentTags(ContentEntity entity) {
    if (entity == null || entity.getId() == null) {
      return;
    }

    // Load tags from database
    List<TagEntity> tagEntities = tagRepository.findContentTags(entity.getId());
    Set<TagEntity> tagSet = new HashSet<>(tagEntities);

    if (entity instanceof ContentImageEntity imageEntity) {
      imageEntity.setTags(tagSet);
    } else if (entity instanceof ContentGifEntity gifEntity) {
      gifEntity.setTags(tagSet);
    }
  }

  /**
   * Load people for a content entity from the database. Populates the entity's people set with
   * ContentPersonEntity objects.
   *
   * @param entity The content entity (ContentImageEntity)
   */
  private void loadContentPeople(ContentEntity entity) {
    if (entity == null || entity.getId() == null) {
      return;
    }

    // Load people from database using ContentPersonDao
    List<ContentPersonEntity> personEntities = personRepository.findContentPeople(entity.getId());

    // Convert to set and populate the entity's people set
    Set<ContentPersonEntity> contentPeopleEntities = new HashSet<>(personEntities);

    // Set people on the entity based on its type
    if (entity instanceof ContentImageEntity imageEntity) {
      imageEntity.setPeople(contentPeopleEntities);
    }
  }

  /**
   * Convert a set of TagEntity to a sorted list of ContentTagModel. Returns empty list if tags is
   * null or empty.
   *
   * @param tags Set of tag entities to convert
   * @return Sorted list of tag models (alphabetically by name)
   */
  public List<Records.Tag> convertTagsToModels(Set<TagEntity> tags) {
    if (tags == null || tags.isEmpty()) {
      return new ArrayList<>();
    }
    return tags.stream()
        .map(tag -> new Records.Tag(tag.getId(), tag.getTagName(), tag.getSlug()))
        .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
        .collect(Collectors.toList());
  }

  /**
   * Convert a set of ContentPersonEntity to a sorted list of Records.Person. Returns empty list if
   * people is null or empty.
   *
   * @param people Set of person entities to convert
   * @return Sorted list of person models (alphabetically by name)
   */
  public List<Records.Person> convertPeopleToModels(Set<ContentPersonEntity> people) {
    if (people == null || people.isEmpty()) {
      return new ArrayList<>();
    }
    return people.stream()
        .map(person -> new Records.Person(person.getId(), person.getPersonName(), person.getSlug()))
        .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
        .collect(Collectors.toList());
  }
}
