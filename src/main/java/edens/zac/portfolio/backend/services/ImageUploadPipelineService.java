package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.DiskUploadRequest;
import edens.zac.portfolio.backend.model.ImageUploadResult;
import edens.zac.portfolio.backend.services.validator.ContentValidator;
import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service that owns all upload orchestration: parallel image uploads, disk-based processing, and
 * collection-with-images creation. Extracted from ContentService to separate upload pipeline
 * concerns from general content CRUD.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ImageUploadPipelineService {

  private final CollectionRepository collectionRepository;
  private final PersonRepository personRepository;
  private final ImageProcessingService imageProcessingService;
  private final ContentMutationUtil contentMutationUtil;
  private final ContentModelConverter contentModelConverter;
  private final ContentValidator contentValidator;
  private final CollectionService collectionService;
  private final JobTrackingService jobTrackingService;
  private final CacheManager cacheManager;
  private final ContentService contentService;
  private final TransactionTemplate transactionTemplate;

  /** Batch size for parallel image processing to avoid overwhelming resources */
  private static final int PARALLEL_BATCH_SIZE = 3;

  private static final String STAGING_COLLECTION_SLUG = "staging";

  // Virtual thread executor for parallel image processing (Java 21+)
  // Virtual threads are lightweight and don't consume OS threads while waiting on I/O
  private final ExecutorService imageProcessingExecutor =
      Executors.newVirtualThreadPerTaskExecutor();

  // Background executor for RAW file uploads -- runs after HTTP response is sent
  private final ExecutorService rawUploadExecutor = Executors.newVirtualThreadPerTaskExecutor();

  // Prevents concurrent upload requests from competing for heap during JPEG decode.
  // Each request uses PARALLEL_BATCH_SIZE threads for image processing; two concurrent
  // requests would double memory usage and risk OOM.
  private final Semaphore uploadSemaphore = new Semaphore(1);

  @PreDestroy
  void shutdown() {
    imageProcessingExecutor.shutdown();
    rawUploadExecutor.shutdown();
    try {
      if (!rawUploadExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
        log.warn("Background RAW uploads did not complete within 60s, forcing shutdown");
        rawUploadExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      rawUploadExecutor.shutdownNow();
    }
  }

  /**
   * Create a new collection and upload images to it in one operation. After images are uploaded,
   * auto-derives collectionDate from image EXIF if not provided, selects the highest-rated image as
   * cover, and links the new collection as a child of the "staging" collection.
   */
  public ImageUploadResult createCollectionWithImages(
      CollectionRequests.Create createRequest,
      List<MultipartFile> files,
      Map<String, String> rawFilePathMap) {
    CollectionRequests.UpdateResponse collectionResponse =
        collectionService.createCollection(createRequest);
    Long newCollectionId = collectionResponse.collection().getId();

    ImageUploadResult result = createImagesParallel(newCollectionId, files, rawFilePathMap);

    if (!result.successful().isEmpty()) {
      postUploadProcessing(newCollectionId, createRequest, result.successful());
    }

    // Return result with collectionId so callers (e.g. Lightroom plugin) can send follow-up batches
    return new ImageUploadResult(
        newCollectionId, result.successful(), result.failed(), result.skipped());
  }

  /**
   * Accept file paths and process images from local disk in background. Returns a JobStatus
   * immediately for the caller to return 202.
   *
   * @param collectionId Target collection
   * @param request File paths and optional locationId
   * @return JobStatus with jobId for polling
   */
  public JobTrackingService.JobStatus processFilesFromDisk(
      Long collectionId, DiskUploadRequest request) {
    // Verify collection exists before starting
    collectionRepository
        .findById(collectionId)
        .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + collectionId));

    // Optionally set collection location if provided and not already set
    if (request.locationId() != null) {
      contentService.setCollectionLocationIfMissing(collectionId, request.locationId());
    }

    var job = jobTrackingService.createJob(request.files().size());

    // Submit background processing on a virtual thread
    rawUploadExecutor.submit(() -> processFilesFromDiskBackground(collectionId, request, job));

    return job;
  }

  /**
   * OPTIMIZED: Create and upload images with parallel processing.
   *
   * <p>Architecture: 1. PARALLEL: S3 upload, resize, convert using virtual threads (NO database
   * calls) 2. SEQUENTIAL: Save all results to database in a single short transaction
   *
   * <p>Images are processed in batches of PARALLEL_BATCH_SIZE to avoid overwhelming S3/memory.
   * Virtual threads handle I/O concurrency without blocking OS threads.
   *
   * @param collectionId ID of the collection to add images to
   * @param files List of image files to upload
   * @param rawFilePathMap Map of rendered filename to RAW file path
   * @return List of successfully created images
   */
  public ImageUploadResult createImagesParallel(
      Long collectionId, List<MultipartFile> files, Map<String, String> rawFilePathMap) {
    log.info(
        "Creating {} images for collection {} with parallel processing (batch size: {})",
        files.size(),
        collectionId,
        PARALLEL_BATCH_SIZE);

    contentValidator.validateFiles(files);

    // Verify collection exists (outside transaction)
    collectionRepository
        .findById(collectionId)
        .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + collectionId));

    // Acquire semaphore to prevent concurrent upload requests from OOM-ing.
    // If another upload is in progress, this request blocks until it finishes.
    try {
      uploadSemaphore.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Upload interrupted while waiting for semaphore", e);
    }

    try {
      // PHASE 1: Prepare images in PARALLEL batches (S3 upload, resize, convert)
      // NO database calls happen here - only S3 I/O and CPU work
      // RAW uploads are deferred to background threads after the response is sent.
      List<PreparedImage> allPrepared = new ArrayList<>();
      List<ImageUploadResult.FileError> allFailures = new ArrayList<>();

      for (int i = 0; i < files.size(); i += PARALLEL_BATCH_SIZE) {
        int end = Math.min(i + PARALLEL_BATCH_SIZE, files.size());
        List<MultipartFile> batch = files.subList(i, end);
        log.debug("Processing batch {}-{} of {} files", i + 1, end, files.size());

        List<CompletableFuture<PreparedImage>> futures =
            batch.stream()
                .map(
                    file -> {
                      String rawPath =
                          rawFilePathMap.getOrDefault(file.getOriginalFilename(), null);
                      return CompletableFuture.supplyAsync(
                          () -> prepareImageAsync(file, rawPath), imageProcessingExecutor);
                    })
                .toList();

        // Wait for this batch to complete and track failures
        for (int j = 0; j < futures.size(); j++) {
          PreparedImage result = futures.get(j).join();
          if (result != null) {
            allPrepared.add(result);
          } else {
            String filename = batch.get(j).getOriginalFilename();
            allFailures.add(
                new ImageUploadResult.FileError(
                    filename != null ? filename : "unknown",
                    "Image preparation failed (S3 upload or processing error)"));
          }
        }

        log.debug(
            "Batch complete: {}/{} images prepared successfully so far",
            allPrepared.size(),
            files.size());
      }

      log.info(
          "All parallel processing complete: {}/{} images prepared, {} failed",
          allPrepared.size(),
          files.size(),
          allFailures.size());

      // PHASE 2: Save images to database individually
      // Each image saves in its own transaction (via @Transactional repository methods)
      // so that one failure doesn't cascade and kill the entire batch
      return saveProcessedImages(collectionId, allPrepared, allFailures);
    } finally {
      uploadSemaphore.release();
    }
  }

  // ---------------------------------------------------------------------------
  //  Private helpers
  // ---------------------------------------------------------------------------

  private void processFilesFromDiskBackground(
      Long collectionId, DiskUploadRequest request, JobTrackingService.JobStatus job) {
    try {
      processFilesFromDiskLoop(collectionId, request, job);
    } catch (Exception e) {
      log.error("Disk upload job {} failed unexpectedly: {}", job.jobId(), e.getMessage(), e);
      job.errors().add("Job failed: " + e.getMessage());
      job.markCompleted();
    }
  }

  private void processFilesFromDiskLoop(
      Long collectionId, DiskUploadRequest request, JobTrackingService.JobStatus job) {
    job.markProcessing();
    log.info(
        "Starting disk upload job {} for {} files in collection {}",
        job.jobId(),
        job.totalFiles(),
        collectionId);

    // Load all known people once -- used for both slug-existence checks and tag filtering
    List<ContentPersonEntity> existingPeople = personRepository.findAllByOrderByPersonNameAsc();
    Set<String> existingSlugs =
        existingPeople.stream().map(ContentPersonEntity::getSlug).collect(Collectors.toSet());

    // Ensure all plugin-provided people exist in DB before processing images
    ensurePluginPeopleExist(request, existingSlugs);

    // Build set of all known people names for filtering them out of tags.
    // Lightroom writes people to dc:subject as flat keywords, so without this they become Tags too.
    Set<String> allKnownPeople =
        existingPeople.stream()
            .map(p -> p.getPersonName().toLowerCase())
            .collect(Collectors.toCollection(HashSet::new));
    // Include any newly created people in the filter set
    request.files().stream()
        .filter(f -> f.people() != null)
        .flatMap(f -> f.people().stream())
        .forEach(name -> allKnownPeople.add(name.toLowerCase()));

    for (var fileEntry : request.files()) {
      try {
        var prepared =
            imageProcessingService.prepareImageFromDisk(
                Path.of(fileEntry.jpegPath()), fileEntry.rawPath());

        // People: prefer plugin-provided, fall back to XMP-extracted
        List<String> people =
            (fileEntry.people() != null && !fileEntry.people().isEmpty())
                ? fileEntry.people()
                : prepared.extractedPeople();

        // Filter tags against all known people names
        List<String> tags =
            prepared.extractedTags().stream()
                .filter(tag -> !allKnownPeople.contains(tag.toLowerCase()))
                .toList();

        // Save to DB with dedupe (reuses existing logic)
        ImageProcessingService.DedupeResult dedupeResult =
            imageProcessingService.savePreparedImageWithDedupe(prepared, null);

        // Update job counters based on dedupeResult action
        job.processed().incrementAndGet();
        switch (dedupeResult.action()) {
          case CREATE -> {
            job.created().incrementAndGet();
            wireImageAfterDedupe(
                dedupeResult,
                tags,
                people,
                prepared.rawFilePath(),
                prepared.imageYear(),
                prepared.imageMonth(),
                collectionId,
                contentService.nextOrderIndex(collectionId));
          }
          case UPDATE -> {
            job.updated().incrementAndGet();
            wireImageAfterDedupe(
                dedupeResult,
                tags,
                people,
                prepared.rawFilePath(),
                prepared.imageYear(),
                prepared.imageMonth(),
                collectionId,
                contentService.nextOrderIndex(collectionId));
          }
          case SKIP -> job.skipped().incrementAndGet();
          default -> log.warn("Unexpected dedupe action: {}", dedupeResult.action());
        }
      } catch (Exception e) {
        log.error("Failed to process file {}: {}", fileEntry.jpegPath(), e.getMessage(), e);
        job.errors().add(fileEntry.jpegPath() + ": " + e.getMessage());
        job.processed().incrementAndGet();
      }
    }

    // Evict generalMetadata cache -- new tags/people may have been created during upload
    evictGeneralMetadataCache();

    job.markCompleted();
    log.info(
        "Disk upload job {} complete: {} created, {} updated, {} skipped, {} errors",
        job.jobId(),
        job.created().get(),
        job.updated().get(),
        job.skipped().get(),
        job.errors().size());
  }

  /**
   * Ensure all plugin-provided people exist in DB before processing images. Deduplicates by slug
   * against the provided set, creating new people as needed.
   */
  private void ensurePluginPeopleExist(DiskUploadRequest request, Set<String> existingSlugs) {
    request.files().stream()
        .filter(f -> f.people() != null)
        .flatMap(f -> f.people().stream())
        .filter(name -> existingSlugs.add(SlugUtil.generateSlug(name)))
        .forEach(
            name -> {
              personRepository.save(new ContentPersonEntity(name));
              log.info("Created new person from plugin: {}", name);
            });
  }

  /**
   * Wire up an image after dedupe: associate keywords, schedule RAW upload if needed, and link to
   * collection (skipping if already linked for UPDATE actions).
   */
  private void wireImageAfterDedupe(
      ImageProcessingService.DedupeResult dedupeResult,
      List<String> tags,
      List<String> people,
      String rawFilePath,
      int year,
      int month,
      Long collectionId,
      int orderIndex) {
    contentMutationUtil.associateExtractedKeywords(dedupeResult.entity().getId(), tags, people);
    scheduleRawUploadIfNeeded(dedupeResult, rawFilePath, year, month);
    if (dedupeResult.action() == ImageProcessingService.DedupeAction.UPDATE) {
      Optional<CollectionContentEntity> existing =
          collectionRepository.findContentByCollectionIdAndContentId(
              collectionId, dedupeResult.entity().getId());
      if (existing.isPresent()) return;
    }
    contentService.linkContentToCollection(collectionId, dedupeResult.entity().getId(), orderIndex);
  }

  /**
   * Schedule a background RAW upload if a raw file path was provided. On CREATE, always schedules
   * (newly created images have null imageUrlRaw). On UPDATE, only schedules if no RAW already
   * exists.
   */
  private void scheduleRawUploadIfNeeded(
      ImageProcessingService.DedupeResult dedupeResult, String rawFilePath, int year, int month) {
    if (rawFilePath == null || rawFilePath.isBlank()) return;
    boolean isCreate = dedupeResult.action() == ImageProcessingService.DedupeAction.CREATE;
    if (!isCreate && dedupeResult.entity().getImageUrlRaw() != null) return;
    Long imageId = dedupeResult.entity().getId();
    rawUploadExecutor.submit(
        () -> imageProcessingService.uploadRawAndUpdateDb(imageId, rawFilePath, year, month));
  }

  /**
   * Prepare a single image asynchronously (S3 upload, resize, convert). This method runs in a
   * virtual thread and does NOT touch the database.
   *
   * @param file The image file to process
   * @param rawFilePath Optional path to the RAW file
   * @return Prepared image data, or null if processing failed
   */
  private PreparedImage prepareImageAsync(MultipartFile file, String rawFilePath) {
    String filename = file.getOriginalFilename();
    try {
      log.trace("Preparing image: {}", filename);

      // Skip non-images and GIFs
      if (file.getContentType() == null
          || !file.getContentType().startsWith("image/")
          || file.getContentType().equals("image/gif")) {
        log.trace("Skipping non-image or GIF: {}", filename);
        return null;
      }

      // S3 upload + resize + WebP conversion + optional RAW upload - NO database calls
      ImageProcessingService.PreparedImageData prepared =
          imageProcessingService.prepareImageForUpload(file, rawFilePath);

      return new PreparedImage(prepared, filename);

    } catch (Exception e) {
      log.error("Failed to prepare image {}: {}", filename, e.getMessage(), e);
      return null;
    }
  }

  /**
   * Save prepared images to database in a single transaction. Handles all DB work: camera/lens
   * lookups, duplicate detection, entity saves, and collection join entries.
   *
   * @param collectionId The collection to add images to
   * @param preparedImages List of prepared image data (S3 URLs + metadata)
   * @param previousFailures Failures from the preparation phase
   * @return ImageUploadResult with successful images and all failures
   */
  private ImageUploadResult saveProcessedImages(
      Long collectionId,
      List<PreparedImage> preparedImages,
      List<ImageUploadResult.FileError> previousFailures) {
    log.trace("Saving {} prepared images to database", preparedImages.size());

    List<ContentModels.Image> createdImages = new ArrayList<>();
    List<ImageUploadResult.FileError> failures = new ArrayList<>(previousFailures);
    List<ImageUploadResult.SkippedFile> skipped = new ArrayList<>();
    int orderIndex = contentService.nextOrderIndex(collectionId);

    for (PreparedImage prepared : preparedImages) {
      try {
        // Save to DB with dedupe: CREATE, UPDATE, or SKIP
        ImageProcessingService.DedupeResult dedupeResult =
            imageProcessingService.savePreparedImageWithDedupe(prepared.data(), null);

        if (dedupeResult.action() == ImageProcessingService.DedupeAction.SKIP) {
          skipped.add(
              new ImageUploadResult.SkippedFile(
                  prepared.filename(), "Duplicate with same or older export date"));
          orderIndex++;
          continue;
        }

        ContentImageEntity entity = dedupeResult.entity();

        // Auto-associate tags and people extracted from XMP keywords
        // On CREATE: initial association. On UPDATE: re-associate in case new tags/people were
        // added.
        if (dedupeResult.action() == ImageProcessingService.DedupeAction.CREATE
            || dedupeResult.action() == ImageProcessingService.DedupeAction.UPDATE) {
          contentMutationUtil.associateExtractedKeywords(
              entity.getId(), prepared.data().extractedTags(), prepared.data().extractedPeople());
        }

        // Schedule background RAW upload if a rawFilePath was provided.
        scheduleRawUploadIfNeeded(
            dedupeResult,
            prepared.data().rawFilePath(),
            prepared.data().imageYear(),
            prepared.data().imageMonth());

        // For UPDATE, check if already in this collection
        if (dedupeResult.action() == ImageProcessingService.DedupeAction.UPDATE) {
          Optional<CollectionContentEntity> existingJoin =
              collectionRepository.findContentByCollectionIdAndContentId(
                  collectionId, entity.getId());
          if (existingJoin.isPresent()) {
            // Already linked, just convert and add to results
            ContentModel contentModel =
                contentModelConverter.convertEntityToModel(existingJoin.get());
            if (contentModel instanceof ContentModels.Image imageModel) {
              createdImages.add(imageModel);
            }
            continue;
          }
        }

        // Create join table entry linking content to collection
        contentService.linkContentToCollection(collectionId, entity.getId(), orderIndex);

        // Convert to model
        ContentModel contentModel =
            contentModelConverter.convertEntityToModel(
                CollectionContentEntity.builder()
                    .collectionId(collectionId)
                    .contentId(entity.getId())
                    .orderIndex(orderIndex)
                    .visible(true)
                    .build());
        if (contentModel instanceof ContentModels.Image imageModel) {
          createdImages.add(imageModel);
        }

        orderIndex++;

      } catch (Exception e) {
        log.error("Failed to save image {}: {}", prepared.filename(), e.getMessage(), e);
        failures.add(new ImageUploadResult.FileError(prepared.filename(), e.getMessage()));
      }
    }

    log.info(
        "Upload complete for collection {}: {} succeeded, {} failed, {} skipped",
        collectionId,
        createdImages.size(),
        failures.size(),
        skipped.size());
    return new ImageUploadResult(createdImages, failures, skipped);
  }

  /**
   * Post-upload processing: derive collection date from images if not provided, set highest-rated
   * image as cover, and link to staging collection. Each step is independent and errors are logged
   * without failing the upload.
   */
  private void postUploadProcessing(
      Long collectionId,
      CollectionRequests.Create createRequest,
      List<ContentModels.Image> uploadedImages) {

    // Auto-derive collectionDate and set cover image
    try {
      transactionTemplate.executeWithoutResult(
          status -> {
            CollectionEntity entity =
                collectionRepository
                    .findById(collectionId)
                    .orElseThrow(
                        () ->
                            new ResourceNotFoundException("Collection not found: " + collectionId));

            if (createRequest.collectionDate() == null) {
              deriveCollectionDate(entity, uploadedImages);
            }
            selectCoverImage(entity, uploadedImages);
            collectionRepository.save(entity);
          });
    } catch (Exception e) {
      log.error(
          "Failed to update collection metadata for collection {}: {}",
          collectionId,
          e.getMessage(),
          e);
    }

    // Link to staging collection (separate try/catch so metadata errors don't block staging)
    try {
      linkToStagingCollection(collectionId);
    } catch (Exception e) {
      log.error("Failed to link collection {} to staging: {}", collectionId, e.getMessage(), e);
    }
  }

  private void deriveCollectionDate(
      CollectionEntity entity, List<ContentModels.Image> uploadedImages) {
    java.time.LocalDateTime earliest = null;
    for (ContentModels.Image img : uploadedImages) {
      if (img.captureDate() != null) {
        if (earliest == null || img.captureDate().isBefore(earliest)) {
          earliest = img.captureDate();
        }
      }
    }
    if (earliest != null) {
      entity.setCollectionDate(earliest.toLocalDate());
      log.info("Auto-derived collectionDate {} for collection {}", earliest, entity.getId());
    }
  }

  private void selectCoverImage(CollectionEntity entity, List<ContentModels.Image> uploadedImages) {
    ContentModels.Image best = null;
    for (ContentModels.Image img : uploadedImages) {
      if (best == null
          || (img.rating() != null && (best.rating() == null || img.rating() > best.rating()))) {
        best = img;
      }
    }
    if (best != null) {
      entity.setCoverImageId(best.id());
      log.info("Auto-set cover image {} for collection {}", best.id(), entity.getId());
    }
  }

  private void linkToStagingCollection(Long childCollectionId) {
    Optional<CollectionEntity> stagingOpt =
        collectionRepository.findBySlug(STAGING_COLLECTION_SLUG);
    if (stagingOpt.isEmpty()) {
      log.info("No '{}' collection found -- skipping auto-staging", STAGING_COLLECTION_SLUG);
      return;
    }
    collectionService.linkCollectionToParent(stagingOpt.get().getId(), childCollectionId);
    log.info("Linked collection {} to staging collection", childCollectionId);
  }

  /**
   * Manually evict the generalMetadata cache. Used by background methods where Spring's
   * proxy-based @CacheEvict cannot intercept (private/self-invoked methods).
   */
  private void evictGeneralMetadataCache() {
    var cache = cacheManager.getCache("generalMetadata");
    if (cache != null) {
      cache.clear();
      log.debug("Evicted generalMetadata cache after disk upload");
    }
  }

  /** Record to hold prepared image data before database save */
  private record PreparedImage(ImageProcessingService.PreparedImageData data, String filename) {}
}
