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
import edens.zac.portfolio.backend.types.FilmFormat;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
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
  private final ReadCacheInvalidator readCacheInvalidator;
  private final ContentRepository contentRepository;
  private final EquipmentRepository equipmentRepository;
  private final LocationRepository locationRepository;
  private final ImageMetadataExtractor imageMetadataExtractor;
  private final ContentValidator contentValidator;
  private final String bucketName;
  private final String cloudfrontDomain;

  ImageProcessingService(
      S3Client s3Client,
      ReadCacheInvalidator readCacheInvalidator,
      ContentRepository contentRepository,
      EquipmentRepository equipmentRepository,
      LocationRepository locationRepository,
      ImageMetadataExtractor imageMetadataExtractor,
      ContentValidator contentValidator,
      @Value("${aws.portfolio.s3.bucket}") String bucketName,
      @Value("${cloudfront.domain}") String cloudfrontDomain) {
    this.s3Client = s3Client;
    this.readCacheInvalidator = readCacheInvalidator;
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
  private static final String PATH_GIF_WEB = "Gif/Web";
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
   * Prepare an image for upload: extract metadata, upload the original to S3, resize to 2500px on
   * the longest side, convert to WebP and upload that. This method does NO database calls and is
   * safe to run in parallel virtual threads.
   *
   * <p>The RAW upload is NOT done here. It is deferred to a background thread after the DB save;
   * {@code rawFilePath} is carried through {@link PreparedImageData} so {@code ContentService} can
   * schedule it.
   *
   * <p>The capture date used for dedupe falls back to {@code modifyDate} for film scans. The export
   * date is {@code now()}, because a {@link MultipartFile} carries no last-modified time and the
   * plugin does not send an export date -- so every multipart upload counts as a fresh export and
   * always takes the dedupe UPDATE branch. The from-disk path has a real file and uses its mtime
   * instead (see {@link #exportDateFromFile}).
   *
   * @param file The image file to process
   * @param rawFilePath Optional absolute path to the RAW source file on local disk
   * @return PreparedImageData with S3 URLs and metadata, ready for DB save
   * @throws IOException If there's an error processing the file
   */
  public PreparedImageData prepareImageForUpload(MultipartFile file, String rawFilePath)
      throws IOException {
    log.trace("Preparing image for upload: {}", file.getOriginalFilename());

    ImageMetadataExtractor.MetadataExtractionResult extraction =
        imageMetadataExtractor.extractImageMetadata(file);
    Map<String, String> metadata = extraction.metadata();

    int[] dateComponents =
        imageMetadataExtractor.parseImageDate(
            metadata.get("createDate"), metadata.get("modifyDate"));
    int imageYear = dateComponents[0];
    int imageMonth = dateComponents[1];

    String originalFilename = file.getOriginalFilename();
    String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
    final String imageUrlOriginal =
        uploadToS3(
            file.getBytes(), originalFilename, contentType, PATH_IMAGE_FULL, imageYear, imageMonth);

    BufferedImage originalImage;
    try (InputStream imageStream = file.getInputStream()) {
      originalImage = ImageIO.read(imageStream);
    }
    if (originalImage == null) {
      throw new IOException("Failed to read image: " + originalFilename);
    }
    BufferedImage resizedImage = resizeImage(originalImage, 2500);
    recordRenditionDimensions(resizedImage, metadata);

    byte[] processedImageBytes;
    String finalFilename;
    if (isJpgFile(file) || isWebPFile(file)) {
      processedImageBytes = convertToWebP(resizedImage);
      if (originalFilename == null) {
        throw new IllegalArgumentException("Original filename must not be null");
      }
      finalFilename = hashedWebFilename(originalFilename, processedImageBytes);
    } else {
      throw new IOException("Unsupported file format. Only JPG and WebP are supported.");
    }

    String imageUrlWeb =
        uploadToS3(
            processedImageBytes,
            finalFilename,
            "image/webp",
            PATH_IMAGE_WEB,
            imageYear,
            imageMonth);

    String createDateStr = metadata.get("createDate");
    String modifyDateStr = metadata.get("modifyDate");

    LocalDateTime captureDate =
        imageMetadataExtractor.parseExifDateToLocalDateTime(
            createDateStr != null ? createDateStr : modifyDateStr);

    LocalDateTime lastExportDate = LocalDateTime.now();

    log.info(
        "Prepared: {} ({}/{}), createDate='{}', modifyDate='{}', captureDate={}",
        originalFilename,
        imageYear,
        String.format("%02d", imageMonth),
        createDateStr,
        modifyDateStr,
        captureDate);
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
   * call (no background RAW phase needed when caller is not waiting). Same shape as {@link
   * #prepareImageForUpload}, except the original is streamed from disk with no heap copy and the
   * export date comes from the file's mtime.
   *
   * @param jpegPath Absolute path to the exported JPEG file on local disk
   * @param rawFilePath Optional absolute path to the RAW source file
   * @return PreparedImageData with S3 URLs and metadata
   * @throws IOException If there's an error reading or processing the files
   */
  public PreparedImageData prepareImageFromDisk(Path jpegPath, String rawFilePath)
      throws IOException {
    log.trace("Preparing image from disk: {}", jpegPath.getFileName());

    ImageMetadataExtractor.MetadataExtractionResult extraction =
        imageMetadataExtractor.extractImageMetadata(jpegPath);
    Map<String, String> metadata = extraction.metadata();

    int[] dateComponents =
        imageMetadataExtractor.parseImageDate(
            metadata.get("createDate"), metadata.get("modifyDate"));
    int imageYear = dateComponents[0];
    int imageMonth = dateComponents[1];

    String originalFilename = jpegPath.getFileName().toString();
    String contentType = detectMimeType(originalFilename);
    final String imageUrlOriginal =
        streamFileToS3(
            jpegPath, originalFilename, contentType, PATH_IMAGE_FULL, imageYear, imageMonth);

    BufferedImage originalImage = ImageIO.read(jpegPath.toFile());
    if (originalImage == null) {
      throw new IOException("Failed to read image: " + originalFilename);
    }
    BufferedImage resizedImage = resizeImage(originalImage, 2500);
    recordRenditionDimensions(resizedImage, metadata);

    byte[] processedImageBytes = convertToWebP(resizedImage);
    String webFilename = hashedWebFilename(originalFilename, processedImageBytes);
    String imageUrlWeb =
        uploadToS3(
            processedImageBytes, webFilename, "image/webp", PATH_IMAGE_WEB, imageYear, imageMonth);

    String createDateStr = metadata.get("createDate");
    String modifyDateStr = metadata.get("modifyDate");

    LocalDateTime captureDate =
        imageMetadataExtractor.parseExifDateToLocalDateTime(
            createDateStr != null ? createDateStr : modifyDateStr);
    LocalDateTime lastExportDate = exportDateFromFile(jpegPath);

    log.info(
        "Prepared from disk: {} ({}/{}), createDate='{}', modifyDate='{}', captureDate={}",
        originalFilename,
        imageYear,
        String.format("%02d", imageMonth),
        createDateStr,
        modifyDateStr,
        captureDate);
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
   * Export timestamp for dedupe: the exported JPEG's last-modified time.
   *
   * <p>Lightroom writes a new file on every export, so mtime is what separates a genuine re-export
   * (newer file, take the UPDATE branch) from re-sending the same file (same mtime, SKIP). Using
   * {@code now()} here instead made every re-send look newer, which is why SKIP only ever fired for
   * duplicates inside a single batch.
   *
   * <p>Falls back to {@code now()} when the filesystem cannot report the time, which keeps the
   * image eligible for update rather than skipping a real re-export.
   *
   * <p>Package-private for tests: the surrounding prepare step converts to WebP through a native
   * library that is not loadable on every dev architecture.
   */
  LocalDateTime exportDateFromFile(Path jpegPath) {
    try {
      return LocalDateTime.ofInstant(
          Files.getLastModifiedTime(jpegPath).toInstant(), ZoneId.systemDefault());
    } catch (IOException e) {
      log.warn(
          "Could not read last-modified time for {} -- treating as a fresh export: {}",
          jpegPath,
          e.getMessage());
      return LocalDateTime.now();
    }
  }

  /**
   * Save a prepared image with dedup logic. Checks for existing image by (filename, captureDate)
   * and either creates, updates, or skips.
   *
   * <p>SKIP requires BOTH export dates and the new one not being newer. A null stored export date
   * means "unknown/old" and always updates, which is what pre-V4 records look like.
   *
   * <p>On UPDATE the order is load-bearing. The DB row is saved first, so a failure leaves the old
   * S3 files still valid, and only then are the old S3 objects deleted -- and only when the URLs
   * actually changed. Re-exporting the same image produces the same content-hashed S3 key, so
   * deleting unconditionally would destroy the file just uploaded. Two fields are deliberately not
   * overwritten: {@code imageUrlRaw}, because RAW uploads land later on a background thread, and
   * the location, which is only replaced when the new export carries one so user-curated data is
   * never cleared. Tags and people are handled separately via {@code associateExtractedKeywords} in
   * {@code ContentService}.
   *
   * <p>On CREATE, {@code createdAt} comes from the EXIF createDate when present; {@code
   * ContentRepository.saveImage} falls back to upload time when it is absent, as with film scans.
   *
   * @param prepared The prepared image data from prepareImageForUpload
   * @param title Optional title override, falling back to a display title derived from the original
   *     filename -- this is NOT the S3 web key, which is content-hashed via {@code
   *     hashedWebFilename}
   * @return DedupeResult indicating the action taken and the entity
   */
  public DedupeResult savePreparedImageWithDedupe(PreparedImageData prepared, String title) {
    Map<String, String> metadata = prepared.metadata();
    String titleFallback =
        prepared.originalFilename().replaceAll("(?i)\\.(jpg|jpeg|webp)$", ".webp");

    if (prepared.originalFilename() != null && prepared.captureDate() != null) {
      Optional<ContentImageEntity> existingOpt =
          contentRepository.findByOriginalFilenameAndCaptureDate(
              prepared.originalFilename(), prepared.captureDate());

      if (existingOpt.isPresent()) {
        ContentImageEntity existing = existingOpt.get();

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

        log.info(
            "Updating existing image (id={}) for {}: newer export detected",
            existing.getId(),
            prepared.originalFilename());

        final String oldImageUrlWeb = existing.getImageUrlWeb();
        final String oldImageUrlOriginal = existing.getImageUrlOriginal();

        applyMetadataToEntity(existing, metadata, prepared);
        final ContentImageEntity savedEntity = contentRepository.saveImage(existing);

        if (metadata.get("location") != null) {
          Long locId = locationRepository.findOrCreate(metadata.get("location")).getId();
          locationRepository.saveContentLocations(savedEntity.getId(), List.of(locId));
        }

        if (!prepared.imageUrlWeb().equals(oldImageUrlWeb)) {
          deleteS3ObjectByUrl(oldImageUrlWeb);
        }
        if (!prepared.imageUrlOriginal().equals(oldImageUrlOriginal)) {
          deleteS3ObjectByUrl(oldImageUrlOriginal);
        }

        return new DedupeResult(savedEntity, DedupeAction.UPDATE);
      }
    }

    ContentImageEntity entity =
        ContentImageEntity.builder()
            .contentType(ContentType.IMAGE)
            .title(title != null ? title : metadata.getOrDefault("title", titleFallback))
            .createdAt(
                imageMetadataExtractor.parseExifDateToLocalDateTime(metadata.get("createDate")))
            .build();
    applyMetadataToEntity(entity, metadata, prepared);

    ContentImageEntity savedEntity = contentRepository.saveImage(entity);

    if (metadata.get("location") != null) {
      Long locId = locationRepository.findOrCreate(metadata.get("location")).getId();
      locationRepository.saveContentLocations(savedEntity.getId(), List.of(locId));
    }
    log.info("Created new image entity with ID: {}", savedEntity.getId());
    return new DedupeResult(savedEntity, DedupeAction.CREATE);
  }

  /**
   * Apply all EXIF/XMP metadata and prepared image data to an entity. Used by both create and
   * update paths so field mappings stay in sync.
   *
   * <p>Rating is only written when the re-export carries one, so a curated rating survives an
   * export that omits the tag -- the same rule the location follows. A present rating overwrites.
   *
   * <p>Dimensions default to {@code null}, never {@code 0}: a consumer cannot tell {@code 0} apart
   * from a real measurement. They are absent only when the header read in {@code
   * ImageMetadataExtractor} failed.
   *
   * <p>Camera resolution runs through {@code resolveFilmCameraDefaults} first: some film scanners
   * and medium-format backs report a generic capture-software name in the EXIF Model tag instead of
   * the physical body, so it is remapped to the real camera and given film defaults before the
   * camera entity is resolved. On a remap the EXIF body serial is dropped, because it belongs to
   * the scanner rather than the remapped body.
   */
  private void applyMetadataToEntity(
      ContentImageEntity entity, Map<String, String> metadata, PreparedImageData prepared) {
    entity.setImageUrlOriginal(prepared.imageUrlOriginal());
    entity.setImageUrlWeb(prepared.imageUrlWeb());
    entity.setCaptureDate(prepared.captureDate());
    entity.setLastExportDate(prepared.lastExportDate());
    entity.setOriginalFilename(prepared.originalFilename());
    entity.setImageWidth(
        imageMetadataExtractor.parseIntegerOrDefault(metadata.get("imageWidth"), null));
    entity.setImageHeight(
        imageMetadataExtractor.parseIntegerOrDefault(metadata.get("imageHeight"), null));
    entity.setIso(imageMetadataExtractor.parseIntegerOrDefault(metadata.get("iso"), null));
    if (metadata.get("rating") != null) {
      entity.setRating(imageMetadataExtractor.parseIntegerOrDefault(metadata.get("rating"), null));
    }
    entity.setFStop(metadata.get("fStop"));
    entity.setShutterSpeed(metadata.get("shutterSpeed"));
    entity.setFocalLength(metadata.get("focalLength"));
    entity.setAuthor(metadata.getOrDefault("author", ImageMetadataExtractor.DEFAULT.AUTHOR));
    entity.setBlackAndWhite(
        imageMetadataExtractor.parseBooleanOrDefault(metadata.get("blackAndWhite"), false));
    entity.setIsFilm(imageMetadataExtractor.parseBooleanOrDefault(metadata.get("isFilm"), false));

    String cameraName = metadata.get("camera");
    String bodySerialNumber = metadata.get("bodySerialNumber");
    FilmCameraDefaults filmDefaults = resolveFilmCameraDefaults(cameraName, entity);
    if (filmDefaults != null) {
      entity.setIsFilm(true);
      entity.setFilmFormat(filmDefaults.filmFormat());
      cameraName = filmDefaults.cameraName();
      bodySerialNumber = filmDefaults.remapped() ? null : bodySerialNumber;
    }
    if (cameraName != null && !cameraName.trim().isEmpty()) {
      entity.setCamera(createCamera(cameraName, bodySerialNumber, null));
    }
    String lensName = metadata.get("lens");
    String lensSerialNumber = metadata.get("lensSerialNumber");
    if (lensName != null && !lensName.trim().isEmpty()) {
      entity.setLens(createLens(lensName, lensSerialNumber, null));
    }
  }

  /** Resolved camera name and film format for a recognized film scanner/back. */
  private record FilmCameraDefaults(String cameraName, FilmFormat filmFormat, boolean remapped) {}

  /**
   * Hardcoded film-camera detection. Maps generic EXIF Model names reported by film scanners and
   * medium-format backs to the physical camera and its film format.
   *
   * <ul>
   *   <li>"EZ Controller" (Hasselblad/Flextight scan software) -> 120 film. A square (1:1) frame is
   *       a Hasselblad 500cm; a wider frame (closer to 645 / 5x7) is a Mamiya 645 Pro.
   *   <li>"OpticFilm 8300i" (Plustek 35mm scanner) -> 35mm film, camera name unchanged.
   * </ul>
   *
   * @param cameraName the raw EXIF camera name
   * @param entity the image entity (dimensions already applied) used for aspect-ratio checks
   * @return the film defaults, or null if the camera is not a recognized film source
   */
  private FilmCameraDefaults resolveFilmCameraDefaults(
      String cameraName, ContentImageEntity entity) {
    if (cameraName == null) {
      return null;
    }
    String name = cameraName.trim();
    if ("EZ Controller".equalsIgnoreCase(name)) {
      String body = isSquareAspectRatio(entity) ? "Hasselblad 500cm" : "Mamiya 645 Pro";
      return new FilmCameraDefaults(body, FilmFormat.MM_120, true);
    }
    if ("OpticFilm 8300i".equalsIgnoreCase(name)) {
      return new FilmCameraDefaults(name, FilmFormat.MM_35, false);
    }
    return null;
  }

  /**
   * Whether the image is effectively square (Hasselblad 6x6). A 645 or 5x7 frame has a ratio of
   * ~1.33-1.4, so a modest tolerance above 1.0 cleanly separates the two.
   */
  private boolean isSquareAspectRatio(ContentImageEntity entity) {
    Integer width = entity.getImageWidth();
    Integer height = entity.getImageHeight();
    if (width == null || height == null || width <= 0 || height <= 0) {
      return false;
    }
    double ratio = (double) Math.max(width, height) / Math.min(width, height);
    return ratio <= 1.15;
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
   * <p>Video takes two variants. FULL is the 2000px master: re-encoded only when the source exceeds
   * the cap, otherwise a lossless remux that strips audio, adds faststart and leaves a good export
   * untouched. WEB is the 1080px display copy, encoded separately only when the source is larger
   * than the web ceiling -- otherwise the small full file IS the web file, since it is never
   * upscaled. If dimension probing fails, both variants are re-encoded, which is safe because a
   * re-encode of an unknown-size file still caps it to the web ceilings. A GIF or image uploads
   * as-is with no web variant; the frontend falls back to {@code gifUrl}.
   *
   * <p>The thumbnail's first frame is extracted from the FULL bytes, so width and height reflect
   * the master and the poster matches what fullscreen shows.
   *
   * <p>Rating defaults to 4 so new GIF/MP4 content reads as feature media in the row grid --
   * horizontal gets a full row, vertical gets half. Admins can downgrade later.
   *
   * @param file The GIF/MP4 file to process
   * @param title Optional title for the content
   * @return The saved GIF content entity
   */
  public ContentGifEntity processGifContent(MultipartFile file, String title) {
    log.info("Processing GIF/MP4 content: {}", file.getOriginalFilename());

    try {
      contentValidator.validateGifFile(file);

      byte[] originalBytes = file.getBytes();
      String originalFilename = file.getOriginalFilename();
      String fallbackContentType =
          file.getContentType() != null ? file.getContentType() : "application/octet-stream";
      String baseName = stripVideoExtension(originalFilename);

      LocalDate now = LocalDate.now();
      int year = now.getYear();
      int month = now.getMonthValue();

      boolean isVideo = contentValidator.isMp4File(file);

      String gifUrl;
      String gifUrlWeb;
      byte[] fullBytes;

      if (isVideo) {
        int[] dims = probeVideoDimensions(originalBytes, originalFilename);
        VideoVariantPlanner.VideoVariantPlan plan =
            dims != null
                ? VideoVariantPlanner.compute(dims[0], dims[1])
                : new VideoVariantPlanner.VideoVariantPlan(true, true);

        fullBytes =
            plan.fullNeedsReencode()
                ? encodeVideoVariant(
                    originalBytes, originalFilename, VideoVariantPlanner.FULL_MAX_LONGEST_SIDE)
                : remuxVideo(originalBytes, originalFilename);
        gifUrl = uploadToS3(fullBytes, baseName + ".mp4", "video/mp4", PATH_GIF_FULL, year, month);

        if (plan.webIsSeparate()) {
          byte[] webBytes =
              encodeVideoVariant(
                  originalBytes, originalFilename, VideoVariantPlanner.WEB_MAX_LONGEST_SIDE);
          gifUrlWeb =
              uploadToS3(webBytes, baseName + "-web.mp4", "video/mp4", PATH_GIF_WEB, year, month);
        } else {
          gifUrlWeb = gifUrl;
        }
      } else {
        fullBytes = originalBytes;
        gifUrl =
            uploadToS3(
                originalBytes, originalFilename, fallbackContentType, PATH_GIF_FULL, year, month);
        gifUrlWeb = null;
      }

      BufferedImage firstFrame;
      if (isVideo) {
        firstFrame = extractFirstFrameViaFfmpeg(fullBytes, originalFilename);
      } else {
        try (InputStream is = new ByteArrayInputStream(fullBytes)) {
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
        String thumbFilename = baseName + "-thumbnail.webp";
        thumbnailUrl =
            uploadToS3(webpBytes, thumbFilename, "image/webp", PATH_GIF_THUMBNAIL, year, month);
      } else {
        log.warn("Could not extract first frame from: {}", originalFilename);
      }

      ContentGifEntity entity =
          ContentGifEntity.builder()
              .contentType(ContentType.GIF)
              .title(title != null && !title.isBlank() ? title : originalFilename)
              .gifUrl(gifUrl)
              .gifUrlWeb(gifUrlWeb)
              .thumbnailUrl(thumbnailUrl)
              .width(width)
              .height(height)
              .author(ImageMetadataExtractor.DEFAULT.AUTHOR)
              .createDate(now.toString())
              .rating(4)
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
   * Content-addressed web filename: {@code <name>.<12-hex-hash>.webp}. New bytes yield a new
   * key/URL so CloudFront and the Next optimizer refresh without invalidation; identical bytes
   * yield the identical key (idempotent re-export). Web rendition only — the full-size original and
   * GIF poster keep deterministic filename/year/month keys.
   */
  String hashedWebFilename(String originalFilename, byte[] webpBytes) {
    String base = originalFilename.replaceAll("(?i)\\.(jpg|jpeg|webp)$", "");
    return base + "." + contentHash(webpBytes) + ".webp";
  }

  /**
   * Short, URL-safe content hash (first 12 hex chars of SHA-256) of the given bytes.
   *
   * <p>SHA-256 is mandated to always be available, so the {@code NoSuchAlgorithmException} branch
   * is unreachable.
   */
  String contentHash(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      return HexFormat.of().formatHex(digest, 0, 6); // 6 bytes -> 12 hex chars
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm unavailable", e);
    }
  }

  /**
   * Delete an image and its variants from S3, then hand the same keys to {@link
   * ReadCacheInvalidator#invalidatePaths} so a re-upload landing on an S3 key that was just deleted
   * is served fresh rather than from CDN cache. The original and RAW keys are deterministic from
   * filename/year/month, so they do collide on re-upload. The web key does not: it is
   * content-hashed via {@code hashedWebFilename}, so a changed image gets a new key and only a
   * byte-identical re-export reuses the old one.
   *
   * @param image The ContentImageEntity containing S3 URLs to delete
   */
  public void deleteImageFromS3(ContentImageEntity image) {
    List<String> deletedKeys = new ArrayList<>();
    String webKey = deleteS3ObjectByUrl(image.getImageUrlWeb());
    if (webKey != null) deletedKeys.add(webKey);
    String originalKey = deleteS3ObjectByUrl(image.getImageUrlOriginal());
    if (originalKey != null) deletedKeys.add(originalKey);
    String rawKey = deleteS3ObjectByUrl(image.getImageUrlRaw());
    if (rawKey != null) deletedKeys.add(rawKey);
    readCacheInvalidator.invalidatePaths(deletedKeys);
  }

  /**
   * Delete S3 objects backing a GIF/MP4 entity: the full-resolution media plus the WebP first-frame
   * thumbnail. Mirrors {@link #deleteImageFromS3} — failures are logged, not thrown, and the keys
   * we attempted still go out as a single CloudFront invalidation.
   *
   * <p>The web variant may be null, or may equal {@code gifUrl} because small files reuse the full
   * path, so the duplicate is guarded to avoid a redundant delete and invalidation on one key.
   *
   * @param gif The ContentGifEntity containing S3 URLs to delete
   */
  public void deleteGifFromS3(ContentGifEntity gif) {
    List<String> deletedKeys = new ArrayList<>();
    String gifKey = deleteS3ObjectByUrl(gif.getGifUrl());
    if (gifKey != null) deletedKeys.add(gifKey);
    String webUrl = gif.getGifUrlWeb();
    if (webUrl != null && !webUrl.equals(gif.getGifUrl())) {
      String webKey = deleteS3ObjectByUrl(webUrl);
      if (webKey != null) deletedKeys.add(webKey);
    }
    String thumbKey = deleteS3ObjectByUrl(gif.getThumbnailUrl());
    if (thumbKey != null) deletedKeys.add(thumbKey);
    readCacheInvalidator.invalidatePaths(deletedKeys);
  }

  /**
   * Delete a single S3 object by its CloudFront URL. Logs but does not throw on failure.
   *
   * @return the S3 key that was targeted (whether or not the delete succeeded), or null if no
   *     attempt was made (url was null or did not match the configured CloudFront domain).
   */
  private String deleteS3ObjectByUrl(String url) {
    if (url == null) {
      return null;
    }
    String s3Key = extractS3KeyFromUrl(url);
    if (s3Key == null) {
      return null;
    }
    try {
      log.trace("Deleting from S3: {}", s3Key);
      s3Client.deleteObject(builder -> builder.bucket(bucketName).key(s3Key));
    } catch (Exception e) {
      log.error("Failed to delete S3 object {}: {}", url, e.getMessage());
    }
    return s3Key;
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
   * size limits, it returns the original unchanged. Pure transform — callers record the resulting
   * dimensions via {@link #recordRenditionDimensions}.
   *
   * @param originalImage The original BufferedImage to resize
   * @param maxDimension The maximum allowed dimension (width or height)
   * @return Resized BufferedImage, or original if no resize needed
   */
  private BufferedImage resizeImage(BufferedImage originalImage, int maxDimension) {
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

    return resizedImage;
  }

  /**
   * Overwrite metadata imageWidth/imageHeight with the served rendition's actual dimensions, so a
   * re-upload with a new aspect ratio persists correct dims instead of stale EXIF values.
   */
  void recordRenditionDimensions(BufferedImage rendition, Map<String, String> metadata) {
    metadata.put("imageWidth", String.valueOf(rendition.getWidth()));
    metadata.put("imageHeight", String.valueOf(rendition.getHeight()));
  }

  /**
   * Convert a BufferedImage to WebP format with compression.
   *
   * <p>The writer is disposed in a finally block. A write that throws still has to release the
   * writer's native resources.
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

    try {
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
      }
    } finally {
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

  /**
   * Probe the pixel dimensions of a video's primary stream via ffprobe.
   *
   * @return int[]{width, height}, or null if ffprobe fails or output is unparseable.
   */
  private int[] probeVideoDimensions(byte[] videoBytes, String filename) throws IOException {
    Path tempInput = Files.createTempFile("gif-probe-", "-" + safeTempName(filename));
    try {
      Files.write(tempInput, videoBytes);

      ProcessBuilder pb =
          new ProcessBuilder(
              "ffprobe",
              "-v",
              "error",
              "-select_streams",
              "v:0",
              "-show_entries",
              "stream=width,height",
              "-of",
              "csv=s=x:p=0",
              tempInput.toAbsolutePath().toString());
      pb.redirectErrorStream(true);
      Process process = pb.start();

      String out;
      try (InputStream is = process.getInputStream()) {
        out = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
      }
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        log.error("ffprobe exited with code {}: {}", exitCode, out);
        return null;
      }

      String[] parts = out.split("x");
      if (parts.length != 2) {
        log.error("ffprobe returned unexpected dimensions output: '{}'", out);
        return null;
      }
      return new int[] {Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("ffprobe was interrupted", e);
    } catch (NumberFormatException e) {
      log.error("ffprobe dimensions not numeric for {}: {}", filename, e.getMessage());
      return null;
    } finally {
      Files.deleteIfExists(tempInput);
    }
  }

  /**
   * Re-encode a video to fit within {@code maxLongestSide} (preserving aspect ratio, even
   * dimensions, never upscaling — the decrease-only scale filter only shrinks). Strips audio and
   * enables faststart. H.264 / yuv420p / CRF 23 for broad mobile + browser support.
   */
  private byte[] encodeVideoVariant(byte[] videoBytes, String filename, int maxLongestSide)
      throws IOException {
    String scale =
        String.format(
            "scale=w=%d:h=%d:force_original_aspect_ratio=decrease:force_divisible_by=2",
            maxLongestSide, maxLongestSide);
    return runFfmpegToMp4(
        videoBytes,
        filename,
        new String[] {
          "-vf",
          scale,
          "-c:v",
          "libx264",
          "-profile:v",
          "high",
          "-pix_fmt",
          "yuv420p",
          "-crf",
          "23",
          "-preset",
          "medium",
          "-an",
          "-movflags",
          "+faststart"
        });
  }

  /**
   * Lossless container rewrap for an already web-sized video: copy the video bitstream verbatim (no
   * quality loss, near-instant), drop audio, enable faststart.
   */
  private byte[] remuxVideo(byte[] videoBytes, String filename) throws IOException {
    return runFfmpegToMp4(
        videoBytes, filename, new String[] {"-c:v", "copy", "-an", "-movflags", "+faststart"});
  }

  /**
   * Run ffmpeg with the given output args, reading raw input from a temp file and returning the
   * encoded MP4 bytes. Shared glue for {@link #encodeVideoVariant} and {@link #remuxVideo}.
   */
  private byte[] runFfmpegToMp4(byte[] videoBytes, String filename, String[] outputArgs)
      throws IOException {
    Path tempInput = Files.createTempFile("gif-src-", "-" + safeTempName(filename));
    Path tempOutput = Files.createTempFile("gif-out-", ".mp4");
    try {
      Files.write(tempInput, videoBytes);

      List<String> command = new ArrayList<>();
      command.add("ffmpeg");
      command.add("-y");
      command.add("-i");
      command.add(tempInput.toAbsolutePath().toString());
      command.addAll(Arrays.asList(outputArgs));
      command.add(tempOutput.toAbsolutePath().toString());

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      Process process = pb.start();

      String ffmpegOutput;
      try (InputStream is = process.getInputStream()) {
        ffmpegOutput = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      }
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        log.error("ffmpeg exited with code {}: {}", exitCode, ffmpegOutput);
        throw new IOException("ffmpeg failed with exit code " + exitCode);
      }
      return Files.readAllBytes(tempOutput);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("ffmpeg was interrupted", e);
    } finally {
      Files.deleteIfExists(tempInput);
      Files.deleteIfExists(tempOutput);
    }
  }

  /** Filename safe for a temp suffix: strip path separators, fall back to a UUID. */
  private String safeTempName(String filename) {
    if (filename == null || filename.isBlank()) {
      return UUID.randomUUID() + ".mp4";
    }
    return filename.replaceAll("[/\\\\]", "_");
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

    String serialNumber = bodySerialNumber;
    if (serialNumber == null || serialNumber.trim().isEmpty()) {
      serialNumber = UUID.randomUUID().toString();
      log.debug("Generated UUID serial number for camera: {}", cameraName);
    } else {
      serialNumber = serialNumber.trim();
    }

    Optional<ContentCameraEntity> existingBySerial =
        equipmentRepository.findCameraByBodySerialNumber(serialNumber);
    if (existingBySerial.isPresent()) {
      log.debug("Found existing camera by serial number: {}", serialNumber);
      return existingBySerial.get();
    }

    Optional<ContentCameraEntity> existingByName =
        equipmentRepository.findCameraByNameIgnoreCase(cameraName);
    if (existingByName.isPresent()) {
      log.debug("Found existing camera by name: {}", cameraName);
      return existingByName.get();
    }

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

    String serialNumber = lensSerialNumber;
    if (serialNumber == null || serialNumber.trim().isEmpty()) {
      serialNumber = UUID.randomUUID().toString();
      log.debug("Generated UUID serial number for lens: {}", lensName);
    } else {
      serialNumber = serialNumber.trim();
    }

    Optional<ContentLensEntity> existingBySerial =
        equipmentRepository.findLensBySerialNumber(serialNumber);
    if (existingBySerial.isPresent()) {
      log.debug("Found existing lens by serial number: {}", serialNumber);
      return existingBySerial.get();
    }

    Optional<ContentLensEntity> existingByName =
        equipmentRepository.findLensByNameIgnoreCase(lensName);
    if (existingByName.isPresent()) {
      log.debug("Found existing lens by name: {}", lensName);
      return existingByName.get();
    }

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
