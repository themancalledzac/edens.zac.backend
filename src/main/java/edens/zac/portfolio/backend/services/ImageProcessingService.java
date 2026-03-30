package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.EquipmentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.entity.ContentCameraEntity;
import edens.zac.portfolio.backend.entity.ContentGifEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.ContentLensEntity;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Handles image and GIF processing: S3 upload/delete, resize, WebP conversion, GIF/MP4 first-frame
 * extraction, and dedup-aware save.
 */
@Component
@Slf4j
public class ImageProcessingService {

  private final S3Client s3Client;
  private final ContentRepository contentRepository;
  private final EquipmentRepository equipmentRepository;
  private final LocationRepository locationRepository;
  private final ImageMetadataExtractor imageMetadataExtractor;
  private final ContentValidator contentValidator;
  private final String bucketName;
  private final String cloudfrontDomain;

  ImageProcessingService(
      S3Client s3Client,
      ContentRepository contentRepository,
      EquipmentRepository equipmentRepository,
      LocationRepository locationRepository,
      ImageMetadataExtractor imageMetadataExtractor,
      ContentValidator contentValidator,
      @Value("${aws.portfolio.s3.bucket}") String bucketName,
      @Value("${cloudfront.domain}") String cloudfrontDomain) {
    this.s3Client = s3Client;
    this.contentRepository = contentRepository;
    this.equipmentRepository = equipmentRepository;
    this.locationRepository = locationRepository;
    this.imageMetadataExtractor = imageMetadataExtractor;
    this.contentValidator = contentValidator;
    this.bucketName = bucketName;
    this.cloudfrontDomain = cloudfrontDomain;
  }

  // S3 path constants for content type hierarchy:
  // {ContentType}/{Quality}/{Year}/{Month}/{filename}
  private static final String PATH_IMAGE_FULL = "Image/Full";
  private static final String PATH_IMAGE_WEB = "Image/Web";
  private static final String PATH_GIF_FULL = "Gif/Full";
  private static final String PATH_GIF_THUMBNAIL = "Gif/Thumbnail";
  private static final String PATH_IMAGE_RAW = "Image/Raw";

  // ============================================================================
  // PUBLIC RECORDS
  // ============================================================================

  /**
   * Data holder for image preparation results. Contains all data needed to save to DB, but does NOT
   * hold any DB connections or entity references. Used by the parallel processing phase.
   */
  public record PreparedImageData(
      String originalFilename,
      String imageUrlOriginal,
      String imageUrlWeb,
      String imageUrlRaw,
      String rawFilePath,
      Map<String, String> metadata,
      List<String> extractedTags,
      List<String> extractedPeople,
      int imageYear,
      int imageMonth,
      LocalDateTime captureDate,
      LocalDateTime lastExportDate) {}

  /**
   * Result of dedupe-aware save. Indicates whether the image was created, updated, or skipped.
   *
   * @param entity The saved entity (null if skipped)
   * @param action CREATE, UPDATE, or SKIP
   */
  public record DedupeResult(ContentImageEntity entity, DedupeAction action) {}

  /** Action taken during dedup-aware save. */
  public enum DedupeAction {
    CREATE,
    UPDATE,
    SKIP
  }

  // ============================================================================
  // IMAGE UPLOAD PIPELINE
  // ============================================================================

  /**
   * Prepare an image for upload without RAW file processing. Delegates to the full method with null
   * rawFilePath.
   */
  public PreparedImageData prepareImageForUpload(MultipartFile file) throws IOException {
    return prepareImageForUpload(file, null);
  }

  /**
   * Prepare an image for upload: extract metadata, upload to S3, resize, convert to WebP.
   * Optionally uploads the original RAW source file to S3. This method does NO database calls and
   * is safe to run in parallel virtual threads.
   *
   * @param file The image file to process
   * @param rawFilePath Optional absolute path to the RAW source file on local disk
   * @return PreparedImageData with S3 URLs and metadata, ready for DB save
   * @throws IOException If there's an error processing the file
   */
  public PreparedImageData prepareImageForUpload(MultipartFile file, String rawFilePath)
      throws IOException {
    log.trace("Preparing image for upload: {}", file.getOriginalFilename());

    // Extract metadata from original file (no DB calls)
    ImageMetadataExtractor.MetadataExtractionResult extraction =
        imageMetadataExtractor.extractImageMetadata(file);
    Map<String, String> metadata = extraction.metadata();

    // Parse image capture date for S3 path organization
    int[] dateComponents =
        imageMetadataExtractor.parseImageDate(
            metadata.get("createDate"), metadata.get("modifyDate"));
    int imageYear = dateComponents[0];
    int imageMonth = dateComponents[1];

    // Upload original full-size image to S3
    String originalFilename = file.getOriginalFilename();
    String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
    final String imageUrlOriginal =
        uploadToS3(
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
      processedImageBytes = convertToWebP(resizedImage);
      if (originalFilename == null) {
        throw new IllegalArgumentException("Original filename must not be null");
      }
      finalFilename = originalFilename.replaceAll("(?i)\\.(jpg|jpeg|webp)$", ".webp");
    } else {
      throw new IOException("Unsupported file format. Only JPG and WebP are supported.");
    }

    // Upload web-optimized image to S3
    String imageUrlWeb =
        uploadToS3(
            processedImageBytes,
            finalFilename,
            "image/webp",
            PATH_IMAGE_WEB,
            imageYear,
            imageMonth);

    // RAW upload is deferred to a background thread after DB save — not done here.
    // rawFilePath is carried through PreparedImageData so ContentService can schedule it.

    // Parse capture date for deduplication
    LocalDateTime captureDate =
        imageMetadataExtractor.parseExifDateToLocalDateTime(metadata.get("createDate"));

    // Use file last-modified as export date (approximation for dedupe)
    LocalDateTime lastExportDate = LocalDateTime.now();

    log.info(
        "Prepared: {} ({}/{})", originalFilename, imageYear, String.format("%02d", imageMonth));
    return new PreparedImageData(
        originalFilename,
        imageUrlOriginal,
        imageUrlWeb,
        null,
        rawFilePath,
        metadata,
        extraction.extractedTags(),
        extraction.extractedPeople(),
        imageYear,
        imageMonth,
        captureDate,
        lastExportDate);
  }

  /**
   * Prepare an image for upload by reading JPEG from disk. Processes both JPEG and RAW in the same
   * call (no background RAW phase needed when caller is not waiting).
   *
   * @param jpegPath Absolute path to the exported JPEG file on local disk
   * @param rawFilePath Optional absolute path to the RAW source file
   * @return PreparedImageData with S3 URLs and metadata
   * @throws IOException If there's an error reading or processing the files
   */
  public PreparedImageData prepareImageFromDisk(Path jpegPath, String rawFilePath)
      throws IOException {
    log.trace("Preparing image from disk: {}", jpegPath.getFileName());

    // Extract metadata from JPEG on disk
    ImageMetadataExtractor.MetadataExtractionResult extraction =
        imageMetadataExtractor.extractImageMetadata(jpegPath);
    Map<String, String> metadata = extraction.metadata();

    // Parse image capture date for S3 path organization
    int[] dateComponents =
        imageMetadataExtractor.parseImageDate(
            metadata.get("createDate"), metadata.get("modifyDate"));
    int imageYear = dateComponents[0];
    int imageMonth = dateComponents[1];

    // Upload original full-size JPEG to S3 (stream from disk, zero heap copy)
    String originalFilename = jpegPath.getFileName().toString();
    String contentType = detectMimeType(originalFilename);
    final String imageUrlOriginal =
        streamFileToS3(
            jpegPath, originalFilename, contentType, PATH_IMAGE_FULL, imageYear, imageMonth);

    // Read image for resize + WebP conversion
    BufferedImage originalImage = ImageIO.read(jpegPath.toFile());
    if (originalImage == null) {
      throw new IOException("Failed to read image: " + originalFilename);
    }
    BufferedImage resizedImage = resizeImage(originalImage, metadata, 2500);

    // Convert to WebP
    byte[] processedImageBytes = convertToWebP(resizedImage);
    String webFilename = originalFilename.replaceAll("(?i)\\.(jpg|jpeg|webp)$", ".webp");
    String imageUrlWeb =
        uploadToS3(
            processedImageBytes, webFilename, "image/webp", PATH_IMAGE_WEB, imageYear, imageMonth);

    LocalDateTime captureDate =
        imageMetadataExtractor.parseExifDateToLocalDateTime(metadata.get("createDate"));
    LocalDateTime lastExportDate = LocalDateTime.now();

    log.info(
        "Prepared from disk: {} ({}/{})",
        originalFilename,
        imageYear,
        String.format("%02d", imageMonth));
    return new PreparedImageData(
        originalFilename,
        imageUrlOriginal,
        imageUrlWeb,
        null,
        rawFilePath,
        metadata,
        extraction.extractedTags(),
        extraction.extractedPeople(),
        imageYear,
        imageMonth,
        captureDate,
        lastExportDate);
  }

  /**
   * Save a prepared image with dedup logic. Checks for existing image by (filename, captureDate)
   * and either creates, updates, or skips.
   *
   * @param prepared The prepared image data from prepareImageForUpload
   * @param title Optional title override
   * @return DedupeResult indicating the action taken and the entity
   */
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

        // Compare export dates: skip only if we have BOTH dates and new is not newer
        // Null existing export date = "unknown/old" = always update (e.g. pre-V4 records)
        boolean existingIsNewerOrEqual =
            existing.getLastExportDate() != null
                && prepared.lastExportDate() != null
                && !prepared.lastExportDate().isAfter(existing.getLastExportDate());
        if (existingIsNewerOrEqual) {
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
        final String oldImageUrlWeb = existing.getImageUrlWeb();
        final String oldImageUrlOriginal = existing.getImageUrlOriginal();

        existing.setImageUrlOriginal(prepared.imageUrlOriginal());
        existing.setImageUrlWeb(prepared.imageUrlWeb());
        // Don't null out imageUrlRaw — RAW uploads are deferred to background threads.
        // The background thread will overwrite the same S3 key and update the DB URL.
        existing.setLastExportDate(prepared.lastExportDate());
        existing.setCaptureDate(prepared.captureDate());
        existing.setImageWidth(
            imageMetadataExtractor.parseIntegerOrDefault(metadata.get("imageWidth"), 0));
        existing.setImageHeight(
            imageMetadataExtractor.parseIntegerOrDefault(metadata.get("imageHeight"), 0));

        // Update metadata that may change between exports
        existing.setRating(
            imageMetadataExtractor.parseIntegerOrDefault(metadata.get("rating"), null));
        existing.setIsFilm(
            imageMetadataExtractor.parseBooleanOrDefault(metadata.get("isFilm"), false));
        // Only update location if the new export has one — never clear user-curated location data.
        // Location is often set manually via the UI when EXIF lacks GPS data.
        if (metadata.get("location") != null) {
          existing.setLocationId(locationRepository.findOrCreate(metadata.get("location")).getId());
        }

        // Tags and people are handled via associateExtractedKeywords in ContentService

        // Save DB first -- if this fails, old S3 files remain valid
        final ContentImageEntity savedEntity = contentRepository.saveImage(existing);

        // Only delete old S3 files if the URLs actually changed (different key).
        // Re-exporting the same image produces the same S3 key — deleting would
        // destroy the file we just uploaded.
        if (!prepared.imageUrlWeb().equals(oldImageUrlWeb)) {
          deleteS3ObjectByUrl(oldImageUrlWeb);
        }
        if (!prepared.imageUrlOriginal().equals(oldImageUrlOriginal)) {
          deleteS3ObjectByUrl(oldImageUrlOriginal);
        }

        return new DedupeResult(savedEntity, DedupeAction.UPDATE);
      }
    }

    // No duplicate found: create new entity
    ContentImageEntity entity =
        ContentImageEntity.builder()
            .contentType(ContentType.IMAGE)
            .title(title != null ? title : metadata.getOrDefault("title", webFilename))
            .imageWidth(imageMetadataExtractor.parseIntegerOrDefault(metadata.get("imageWidth"), 0))
            .imageHeight(
                imageMetadataExtractor.parseIntegerOrDefault(metadata.get("imageHeight"), 0))
            .iso(imageMetadataExtractor.parseIntegerOrDefault(metadata.get("iso"), null))
            .author(metadata.getOrDefault("author", ImageMetadataExtractor.DEFAULT.AUTHOR))
            .rating(imageMetadataExtractor.parseIntegerOrDefault(metadata.get("rating"), null))
            .fStop(metadata.get("fStop"))
            .blackAndWhite(
                imageMetadataExtractor.parseBooleanOrDefault(metadata.get("blackAndWhite"), false))
            .isFilm(imageMetadataExtractor.parseBooleanOrDefault(metadata.get("isFilm"), false))
            .shutterSpeed(metadata.get("shutterSpeed"))
            .imageUrlOriginal(prepared.imageUrlOriginal())
            .imageUrlRaw(prepared.imageUrlRaw())
            .focalLength(metadata.get("focalLength"))
            .locationId(
                metadata.get("location") != null
                    ? locationRepository.findOrCreate(metadata.get("location")).getId()
                    : null)
            .imageUrlWeb(prepared.imageUrlWeb())
            .captureDate(prepared.captureDate())
            .lastExportDate(prepared.lastExportDate())
            .originalFilename(prepared.originalFilename())
            .createdAt(
                imageMetadataExtractor.parseExifDateToLocalDateTime(metadata.get("createDate")))
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
    log.info("Created new image entity with ID: {}", savedEntity.getId());
    return new DedupeResult(savedEntity, DedupeAction.CREATE);
  }

  /**
   * Upload a RAW file to S3 and update the database record with the URL. Designed to run in a
   * background thread after the HTTP response has been sent.
   *
   * @param imageId The database ID of the image to update
   * @param rawFilePath Absolute path to the RAW file on local disk
   * @param imageYear Year for S3 path organization
   * @param imageMonth Month for S3 path organization
   */
  public void uploadRawAndUpdateDb(
      Long imageId, String rawFilePath, int imageYear, int imageMonth) {
    try {
      Path rawPath = Path.of(rawFilePath);
      if (!Files.exists(rawPath)) {
        log.warn("RAW file not found, skipping background upload: {}", rawFilePath);
        return;
      }
      String rawFilename = rawPath.getFileName().toString();
      String rawMimeType = detectMimeType(rawFilename);
      String imageUrlRaw =
          streamFileToS3(rawPath, rawFilename, rawMimeType, PATH_IMAGE_RAW, imageYear, imageMonth);
      contentRepository.updateImageRawUrl(imageId, imageUrlRaw);
      log.info("RAW uploaded: {} (image {})", rawFilename, imageId);
    } catch (Exception e) {
      log.error("Background RAW upload failed for image {}: {}", imageId, e.getMessage(), e);
    }
  }

  // ============================================================================
  // GIF/MP4 PROCESSING
  // ============================================================================

  /**
   * Process a GIF or MP4 file: validate, upload to S3, extract first frame as WebP thumbnail.
   *
   * @param file The GIF/MP4 file to process
   * @param title Optional title for the content
   * @return The saved GIF content entity
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
          uploadToS3(fileBytes, originalFilename, contentType, PATH_GIF_FULL, year, month);

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
        byte[] webpBytes = convertToWebP(firstFrame);
        String thumbFilename = stripVideoExtension(originalFilename) + "-thumbnail.webp";
        thumbnailUrl =
            uploadToS3(webpBytes, thumbFilename, "image/webp", PATH_GIF_THUMBNAIL, year, month);
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
              .author(ImageMetadataExtractor.DEFAULT.AUTHOR)
              .createDate(now.toString())
              .build();

      return contentRepository.saveGif(entity);

    } catch (IOException e) {
      log.error("Error processing GIF/MP4 content: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to process GIF/MP4 content", e);
    }
  }

  // ============================================================================
  // S3 OPERATIONS
  // ============================================================================

  /**
   * Upload bytes to S3 and return the CloudFront URL.
   *
   * <p>Path structure: {basePath}/{year}/{month}/{filename}
   *
   * @param imageBytes The bytes to upload
   * @param filename The filename
   * @param contentType The content type (e.g., "image/jpeg", "image/webp")
   * @param basePath The base path for S3 (e.g., PATH_IMAGE_FULL)
   * @param year The year from image capture date
   * @param month The month from image capture date (1-12)
   * @return The CloudFront URL of the uploaded file
   */
  private String uploadToS3(
      byte[] imageBytes,
      String filename,
      String contentType,
      String basePath,
      int year,
      int month) {
    String s3Key = String.format("%s/%d/%02d/%s", basePath, year, month, filename);

    log.trace("Uploading to S3: {}", s3Key);

    PutObjectRequest putRequest =
        PutObjectRequest.builder()
            .bucket(bucketName)
            .key(s3Key)
            .contentType(contentType)
            .contentLength((long) imageBytes.length)
            .build();

    s3Client.putObject(putRequest, RequestBody.fromBytes(imageBytes));

    String cloudfrontUrl = "https://" + cloudfrontDomain + "/" + s3Key;

    return cloudfrontUrl;
  }

  /** Stream a file directly from disk to S3 without loading into heap. */
  private String streamFileToS3(
      Path filePath, String filename, String contentType, String basePath, int year, int month)
      throws IOException {
    String s3Key = String.format("%s/%d/%02d/%s", basePath, year, month, filename);
    long fileSize = Files.size(filePath);

    log.trace("Streaming to S3: {} ({} MB)", s3Key, fileSize / (1024 * 1024));

    PutObjectRequest putRequest =
        PutObjectRequest.builder()
            .bucket(bucketName)
            .key(s3Key)
            .contentType(contentType)
            .contentLength(fileSize)
            .build();

    s3Client.putObject(putRequest, RequestBody.fromFile(filePath));

    String cloudfrontUrl = "https://" + cloudfrontDomain + "/" + s3Key;

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
    deleteS3ObjectByUrl(image.getImageUrlRaw());
  }

  /** Delete a single S3 object by its CloudFront URL. Logs but does not throw on failure. */
  private void deleteS3ObjectByUrl(String url) {
    if (url == null) {
      return;
    }
    try {
      String s3Key = extractS3KeyFromUrl(url);
      if (s3Key != null) {
        log.trace("Deleting from S3: {}", s3Key);
        s3Client.deleteObject(builder -> builder.bucket(bucketName).key(s3Key));
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

    String prefix = "https://" + cloudfrontDomain + "/";
    if (url.startsWith(prefix)) {
      return url.substring(prefix.length());
    }

    log.warn("URL does not match expected CloudFront format: {}", url);
    return null;
  }

  // ============================================================================
  // IMAGE TRANSFORMATION
  // ============================================================================

  /**
   * Resize a BufferedImage to fit within the maximum dimension. If the image is already within the
   * size limits, it returns the original unchanged.
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

    int newWidth;
    int newHeight;
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
      log.trace(
          "Image is within size limits ({}x{}), no resize needed", originalWidth, originalHeight);
      return originalImage;
    }

    log.trace(
        "Resizing image from {}x{} to {}x{}", originalWidth, originalHeight, newWidth, newHeight);

    BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = resizedImage.createGraphics();
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
    g.dispose();

    metadata.put("imageWidth", String.valueOf(newWidth));
    metadata.put("imageHeight", String.valueOf(newHeight));

    return resizedImage;
  }

  /**
   * Convert a BufferedImage to WebP format with compression.
   *
   * @param bufferedImage The BufferedImage to convert
   * @return byte array containing the WebP image data
   * @throws IOException If there's an error during conversion
   */
  private byte[] convertToWebP(BufferedImage bufferedImage) throws IOException {
    log.trace("Converting to WebP: {}x{}", bufferedImage.getWidth(), bufferedImage.getHeight());

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
    if (!writers.hasNext()) {
      throw new IOException("No WebP writer found. Make sure webp-imageio is on the classpath.");
    }

    ImageWriter writer = writers.next();
    log.trace("Using WebP writer: {}", writer.getClass().getName());

    ImageWriteParam writeParam = writer.getDefaultWriteParam();

    if (writeParam.canWriteCompressed()) {
      writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      String[] compressionTypes = writeParam.getCompressionTypes();
      if (compressionTypes != null && compressionTypes.length > 0) {
        writeParam.setCompressionType(compressionTypes[0]);
      }
      writeParam.setCompressionQuality(0.85f);
      log.trace("Set WebP compression quality to 85%");
    } else {
      log.warn("WebP writer does not support compression settings");
    }

    try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputStream)) {
      writer.setOutput(ios);
      writer.write(null, new IIOImage(bufferedImage, null, null), writeParam);
      writer.dispose();
    }

    byte[] webpBytes = outputStream.toByteArray();
    log.trace("WebP conversion complete: {} bytes", webpBytes.length);

    return webpBytes;
  }

  // ============================================================================
  // FILE TYPE HELPERS
  // ============================================================================

  private boolean isJpgFile(MultipartFile file) {
    String contentType = file.getContentType();
    String filename = file.getOriginalFilename();

    return (contentType != null
            && (contentType.equals("image/jpeg") || contentType.equals("image/jpg")))
        || (filename != null
            && (filename.toLowerCase().endsWith(".jpg")
                || filename.toLowerCase().endsWith(".jpeg")));
  }

  private boolean isWebPFile(MultipartFile file) {
    String contentType = file.getContentType();
    String filename = file.getOriginalFilename();

    return (contentType != null && contentType.equals("image/webp"))
        || (filename != null && filename.toLowerCase().endsWith(".webp"));
  }

  /**
   * Detect MIME type from file extension for RAW and common image formats.
   *
   * @param filename The filename with extension
   * @return The MIME type string
   */
  private String detectMimeType(String filename) {
    if (filename == null) {
      return "application/octet-stream";
    }
    String lower = filename.toLowerCase();
    if (lower.endsWith(".nef")) return "image/x-nikon-nef";
    if (lower.endsWith(".cr2")) return "image/x-canon-cr2";
    if (lower.endsWith(".cr3")) return "image/x-canon-cr3";
    if (lower.endsWith(".arw")) return "image/x-sony-arw";
    if (lower.endsWith(".dng")) return "image/x-adobe-dng";
    if (lower.endsWith(".raf")) return "image/x-fuji-raf";
    if (lower.endsWith(".orf")) return "image/x-olympus-orf";
    if (lower.endsWith(".rw2")) return "image/x-panasonic-rw2";
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
    if (lower.endsWith(".tiff") || lower.endsWith(".tif")) return "image/tiff";
    if (lower.endsWith(".png")) return "image/png";
    if (lower.endsWith(".webp")) return "image/webp";
    return "application/octet-stream";
  }

  // ============================================================================
  // GIF/MP4 HELPERS
  // ============================================================================

  /**
   * Extract the first frame of an MP4/MOV video using ffmpeg.
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

  // ============================================================================
  // CAMERA/LENS CREATION HELPERS
  // ============================================================================

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
}
