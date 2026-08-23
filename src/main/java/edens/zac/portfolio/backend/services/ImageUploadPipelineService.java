package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.DiskUploadRequest;
import edens.zac.portfolio.backend.model.ImageUploadResult;
import edens.zac.portfolio.backend.services.validator.ContentValidator;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
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

  // Prevents concurrent uploads from competing for heap during JPEG decode. A full-resolution
  // ImageIO.read of a 45MP JPEG costs 130-180 MB, so two upload paths decoding at once can
  // exhaust the EC2 heap. Every path that decodes must hold this: the multipart batch in
  // createImagesParallel, and the per-file prepare step in the disk and ingest loops, which run
  // on an unbounded virtual-thread executor and would otherwise stack without limit.
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
   *
   * <p>The result carries the new collectionId so callers -- the Lightroom plugin in particular --
   * can send follow-up batches into the same collection.
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

    return new ImageUploadResult(
        newCollectionId, result.successful(), result.failed(), result.skipped());
  }

  /**
   * Accept file paths and process images from local disk in background. Returns a JobStatus
   * immediately for the caller to return 202.
   *
   * <p>The collection is only checked for existence: any collection may receive uploaded images
   * (Rule B). Locations from the request are applied only when the collection has none.
   *
   * @param collectionId Target collection
   * @param request File paths and optional locationId
   * @return JobStatus with jobId for polling
   */
  public JobTrackingService.JobStatus processFilesFromDisk(
      Long collectionId, DiskUploadRequest request) {
    collectionRepository
        .findById(collectionId)
        .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + collectionId));

    if (request.locationIds() != null && !request.locationIds().isEmpty()) {
      contentService.setCollectionLocationsIfMissing(collectionId, request.locationIds());
    }

    var job = jobTrackingService.createJob(request.files().size());

    rawUploadExecutor.submit(() -> processFilesFromDiskBackground(collectionId, request, job));

    return job;
  }

  /**
   * Tag-first ingest: accept file paths with per-file name-based metadata and process them in
   * background, auto-deriving a date-based blog collection per capture day. No collectionId is
   * supplied -- the day's blog (get-or-create keyed on {@code (is_blog = true, collectionDate =
   * day)}) is the storage home. Returns a JobStatus immediately for the caller to return 202.
   *
   * @param request File paths plus optional per-file people/tags/locations/captureDate
   * @return JobStatus with jobId for polling
   */
  public JobTrackingService.JobStatus ingestFilesGroupedByDay(DiskUploadRequest request) {
    var job = jobTrackingService.createJob(request.files().size());

    rawUploadExecutor.submit(() -> ingestFilesGroupedByDayBackground(request, job));

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
   * <p>The collection is only checked for existence, outside the transaction: any collection may
   * receive uploaded images (Rule B).
   *
   * <p>An upload permit is acquired first so concurrent upload requests cannot OOM the heap; if
   * another upload is in progress this request blocks until it finishes. Phase 1 does S3 I/O and
   * CPU work only, no database calls, and RAW uploads are deferred to background threads after the
   * response is sent. Phase 2 saves each image in its own transaction, via the
   * {@code @Transactional} repository methods, so one failure cannot cascade and kill the whole
   * batch.
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

    collectionRepository
        .findById(collectionId)
        .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + collectionId));

    acquireUploadPermit();

    try {
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

      return saveProcessedImages(collectionId, allPrepared, allFailures);
    } finally {
      uploadSemaphore.release();
    }
  }

  // ---------------------------------------------------------------------------
  //  Private helpers
  // ---------------------------------------------------------------------------

  /** Block until an upload permit is free. Restores the interrupt flag before throwing. */
  private void acquireUploadPermit() {
    try {
      uploadSemaphore.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Upload interrupted while waiting for semaphore", e);
    }
  }

  /**
   * Prepare one file from disk while holding the upload permit. The permit covers only the decode
   * and S3 phase -- the heap-hungry part -- and is released before the caller does its database
   * work, so concurrent jobs interleave instead of one job holding the permit for its whole run.
   */
  private ImageProcessingService.PreparedImageData prepareFromDiskGuarded(
      String jpegPath, String rawPath) throws IOException {
    acquireUploadPermit();
    try {
      return imageProcessingService.prepareImageFromDisk(Path.of(jpegPath), rawPath);
    } finally {
      uploadSemaphore.release();
    }
  }

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

  /**
   * The from-disk ingest loop: prepare, dedupe-save and wire up each file in turn.
   *
   * <p>Known people are loaded once and used for two things: checking which plugin-provided people
   * need creating, and filtering people back out of the tag list. That filter is necessary because
   * Lightroom writes people into {@code dc:subject} as flat keywords, so without it every person
   * would also become a Tag. Newly created people join the filter set.
   *
   * <p>Per file, plugin-provided people and tags win over the XMP-extracted ones. A SKIP still
   * links the image into the target collection: a skipped image is unchanged, but re-sending an
   * already-known photo to a new collection must add it there rather than silently drop it.
   *
   * <p>Finally the {@code generalMetadata} cache is evicted, since new tags and people may have
   * been created during the upload.
   */
  private void processFilesFromDiskLoop(
      Long collectionId, DiskUploadRequest request, JobTrackingService.JobStatus job) {
    job.markProcessing();
    log.info(
        "Starting disk upload job {} for {} files in collection {}",
        job.jobId(),
        job.totalFiles(),
        collectionId);

    List<ContentPersonEntity> existingPeople = personRepository.findAllByOrderByPersonNameAsc();
    Set<String> existingNames =
        existingPeople.stream()
            .map(p -> p.getPersonName().toLowerCase())
            .collect(Collectors.toCollection(HashSet::new));

    ensurePluginPeopleExist(request, existingNames);

    Set<String> allKnownPeople =
        existingPeople.stream()
            .map(p -> p.getPersonName().toLowerCase())
            .collect(Collectors.toCollection(HashSet::new));
    request.files().stream()
        .filter(f -> f.people() != null)
        .flatMap(f -> f.people().stream())
        .forEach(name -> allKnownPeople.add(name.toLowerCase()));

    int orderIndex = contentService.nextOrderIndex(collectionId);

    for (var fileEntry : request.files()) {
      try {
        var prepared = prepareFromDiskGuarded(fileEntry.jpegPath(), fileEntry.rawPath());

        List<String> people =
            (fileEntry.people() != null && !fileEntry.people().isEmpty())
                ? fileEntry.people()
                : prepared.extractedPeople();

        List<String> rawTags =
            (fileEntry.tags() != null && !fileEntry.tags().isEmpty())
                ? fileEntry.tags()
                : prepared.extractedTags();
        List<String> tags =
            rawTags.stream().filter(tag -> !allKnownPeople.contains(tag.toLowerCase())).toList();

        ImageProcessingService.DedupeResult dedupeResult =
            imageProcessingService.savePreparedImageWithDedupe(prepared, null);

        job.processed().incrementAndGet();
        switch (dedupeResult.action()) {
          case CREATE -> {
            job.created().incrementAndGet();
            job.errors()
                .addAll(
                    wireImageAfterDedupe(
                        dedupeResult,
                        tags,
                        people,
                        prepared.rawFilePath(),
                        prepared.imageYear(),
                        prepared.imageMonth(),
                        collectionId,
                        orderIndex++));
            contentMutationUtil.associateLocationsByName(
                dedupeResult.entity().getId(), fileEntry.locations());
          }
          case UPDATE -> {
            job.updated().incrementAndGet();
            job.errors()
                .addAll(
                    wireImageAfterDedupe(
                        dedupeResult,
                        tags,
                        people,
                        prepared.rawFilePath(),
                        prepared.imageYear(),
                        prepared.imageMonth(),
                        collectionId,
                        orderIndex++));
            contentMutationUtil.associateLocationsByName(
                dedupeResult.entity().getId(), fileEntry.locations());
          }
          case SKIP -> {
            job.skipped().incrementAndGet();
            linkIfNotLinked(collectionId, dedupeResult.entity().getId(), orderIndex++);
          }
          default -> log.warn("Unexpected dedupe action: {}", dedupeResult.action());
        }
      } catch (Exception e) {
        log.error("Failed to process file {}: {}", fileEntry.jpegPath(), e.getMessage(), e);
        job.errors().add(fileEntry.jpegPath() + ": " + e.getMessage());
        job.processed().incrementAndGet();
      }
    }

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

  private void ingestFilesGroupedByDayBackground(
      DiskUploadRequest request, JobTrackingService.JobStatus job) {
    try {
      ingestFilesGroupedByDayLoop(request, job);
    } catch (Exception e) {
      log.error("Ingest job {} failed unexpectedly: {}", job.jobId(), e.getMessage(), e);
      job.errors().add("Job failed: " + e.getMessage());
      job.markCompleted();
    }
  }

  /**
   * The tag-first ingest loop. Same shape as {@link #processFilesFromDiskLoop} -- people loading,
   * the people-out-of-tags filter, plugin-over-XMP precedence and the link-on-SKIP rule are all
   * identical -- with one difference: there is no target collection, so each file lands in the BLOG
   * collection for its capture day.
   *
   * <p>Each image is prepared first, because that uploads to S3 and extracts EXIF including the
   * capture date, which is the fallback when the request omits {@code captureDate}. The per-day
   * collection is memoized within the job, and orderIndex is tracked per collection so multiple
   * files on the same day append in sequence.
   */
  private void ingestFilesGroupedByDayLoop(
      DiskUploadRequest request, JobTrackingService.JobStatus job) {
    job.markProcessing();
    log.info("Starting tag-first ingest job {} for {} files", job.jobId(), job.totalFiles());

    List<ContentPersonEntity> existingPeople = personRepository.findAllByOrderByPersonNameAsc();
    Set<String> existingNames =
        existingPeople.stream()
            .map(p -> p.getPersonName().toLowerCase())
            .collect(Collectors.toCollection(HashSet::new));

    ensurePluginPeopleExist(request, existingNames);

    Set<String> allKnownPeople =
        existingPeople.stream()
            .map(p -> p.getPersonName().toLowerCase())
            .collect(Collectors.toCollection(HashSet::new));
    request.files().stream()
        .filter(f -> f.people() != null)
        .flatMap(f -> f.people().stream())
        .forEach(name -> allKnownPeople.add(name.toLowerCase()));

    Map<LocalDate, Long> blogByDay = new HashMap<>();
    Map<Long, Integer> nextOrderByCollection = new HashMap<>();

    for (var fileEntry : request.files()) {
      try {
        var prepared = prepareFromDiskGuarded(fileEntry.jpegPath(), fileEntry.rawPath());

        LocalDate captureDay = resolveCaptureDay(fileEntry, prepared);
        if (captureDay == null) {
          log.warn(
              "No resolvable capture date for {} -- recording as failure", fileEntry.jpegPath());
          job.errors()
              .add(
                  fileEntry.jpegPath()
                      + ": no resolvable capture date (request captureDate absent and no EXIF date"
                      + " on file)");
          job.processed().incrementAndGet();
          continue;
        }

        Long collectionId = blogByDay.computeIfAbsent(captureDay, this::getOrCreateBlogForDay);

        List<String> people =
            (fileEntry.people() != null && !fileEntry.people().isEmpty())
                ? fileEntry.people()
                : prepared.extractedPeople();

        List<String> rawTags =
            (fileEntry.tags() != null && !fileEntry.tags().isEmpty())
                ? fileEntry.tags()
                : prepared.extractedTags();
        List<String> tags =
            rawTags.stream().filter(tag -> !allKnownPeople.contains(tag.toLowerCase())).toList();

        ImageProcessingService.DedupeResult dedupeResult =
            imageProcessingService.savePreparedImageWithDedupe(prepared, null);

        job.processed().incrementAndGet();
        switch (dedupeResult.action()) {
          case CREATE, UPDATE -> {
            if (dedupeResult.action() == ImageProcessingService.DedupeAction.CREATE) {
              job.created().incrementAndGet();
            } else {
              job.updated().incrementAndGet();
            }
            int orderIndex =
                nextOrderByCollection.computeIfAbsent(collectionId, contentService::nextOrderIndex);
            nextOrderByCollection.put(collectionId, orderIndex + 1);
            job.errors()
                .addAll(
                    wireImageAfterDedupe(
                        dedupeResult,
                        tags,
                        people,
                        prepared.rawFilePath(),
                        prepared.imageYear(),
                        prepared.imageMonth(),
                        collectionId,
                        orderIndex));
            contentMutationUtil.associateLocationsByName(
                dedupeResult.entity().getId(), fileEntry.locations());
          }
          case SKIP -> {
            job.skipped().incrementAndGet();
            int orderIndex =
                nextOrderByCollection.computeIfAbsent(collectionId, contentService::nextOrderIndex);
            nextOrderByCollection.put(collectionId, orderIndex + 1);
            linkIfNotLinked(collectionId, dedupeResult.entity().getId(), orderIndex);
          }
          default -> log.warn("Unexpected dedupe action: {}", dedupeResult.action());
        }
      } catch (Exception e) {
        log.error("Failed to ingest file {}: {}", fileEntry.jpegPath(), e.getMessage(), e);
        job.errors().add(fileEntry.jpegPath() + ": " + e.getMessage());
        job.processed().incrementAndGet();
      }
    }

    evictGeneralMetadataCache();

    job.markCompleted();
    log.info(
        "Ingest job {} complete: {} created, {} updated, {} skipped, {} errors across {} day(s)",
        job.jobId(),
        job.created().get(),
        job.updated().get(),
        job.skipped().get(),
        job.errors().size(),
        blogByDay.size());
  }

  /**
   * Resolve a file's capture day: prefer the request-provided {@code captureDate} ({@code
   * yyyy-MM-dd}); fall back to the EXIF capture date extracted while preparing the image. Returns
   * null when neither is resolvable (caller records the file as a job failure).
   */
  private LocalDate resolveCaptureDay(
      DiskUploadRequest.FileEntry fileEntry, ImageProcessingService.PreparedImageData prepared) {
    if (fileEntry.captureDate() != null && !fileEntry.captureDate().isBlank()) {
      try {
        return LocalDate.parse(fileEntry.captureDate().trim());
      } catch (DateTimeParseException e) {
        log.warn(
            "Unparseable captureDate '{}' for {} -- falling back to EXIF",
            fileEntry.captureDate(),
            fileEntry.jpegPath());
      }
    }
    return prepared.captureDate() != null ? prepared.captureDate().toLocalDate() : null;
  }

  /**
   * Get-or-create the blog collection for a capture day, keyed on {@code (is_blog=true,
   * collectionDate=day)}. If exactly one exists, reuse it; if multiple exist (should not happen),
   * use the oldest and log a warning; otherwise create a new blog whose title/slug derive from the
   * ISO date. Creation sets {@code isBlog=true} explicitly: the get-after-create lookup keys on
   * {@code is_blog}, so an implicit derivation would silently produce a duplicate day blog on every
   * batch.
   *
   * <p>The shared create path is privacy-first (every new collection lands UNLISTED). Ingested day
   * blogs are auto-published, so visibility is promoted to LISTED explicitly right after create --
   * without it the blog is invisible to {@link CollectionRepository#findListedBlogsOrdered} and to
   * the public {@code /all-blogs} listing, and the pipeline would silently never publish.
   */
  private Long getOrCreateBlogForDay(LocalDate day) {
    List<CollectionEntity> existing = collectionRepository.findBlogsByCollectionDate(day);
    if (!existing.isEmpty()) {
      if (existing.size() > 1) {
        log.warn(
            "Found {} BLOG collections for {} -- using oldest (id {})",
            existing.size(),
            day,
            existing.get(0).getId());
      }
      return existing.get(0).getId();
    }

    var createRequest =
        new CollectionRequests.Create(day.toString(), null, null, null, day, null, Boolean.TRUE);
    CollectionRequests.UpdateResponse created = collectionService.createCollection(createRequest);
    Long newId = created.collection().getId();
    collectionRepository.updateVisibility(newId, CollectionVisibility.LISTED);
    log.info("Created LISTED BLOG collection {} for capture day {}", newId, day);
    return newId;
  }

  /**
   * Ensure all plugin-provided people exist in DB before processing images. Deduplicates by slug
   * against the provided set, creating new people as needed.
   */
  private void ensurePluginPeopleExist(DiskUploadRequest request, Set<String> existingNames) {
    request.files().stream()
        .filter(f -> f.people() != null)
        .flatMap(f -> f.people().stream())
        .filter(name -> existingNames.add(name.toLowerCase()))
        .forEach(
            name -> {
              personRepository.save(new ContentPersonEntity(name));
              log.info("Created new person from plugin: {}", name);
            });
  }

  /**
   * Wire up an image after dedupe: associate keywords, schedule RAW upload if needed, and link to
   * collection (skipping if already linked for UPDATE actions). On CREATE the entity id is brand
   * new, so no link can exist yet and the lookup is skipped.
   *
   * <p>Keyword failures are returned rather than thrown. The image is already saved, and throwing
   * would skip the collection link below and orphan it; handing the failures back is what makes a
   * dropped person tag visible to the caller instead of silent (see {@code ContentMutationUtil} and
   * V53).
   *
   * @return one message per keyword association that failed; empty on full success
   */
  private List<String> wireImageAfterDedupe(
      ImageProcessingService.DedupeResult dedupeResult,
      List<String> tags,
      List<String> people,
      String rawFilePath,
      int year,
      int month,
      Long collectionId,
      int orderIndex) {
    List<String> keywordFailures =
        contentMutationUtil.associateExtractedKeywords(dedupeResult.entity().getId(), tags, people);
    scheduleRawUploadIfNeeded(dedupeResult, rawFilePath, year, month);
    if (dedupeResult.action() == ImageProcessingService.DedupeAction.UPDATE) {
      linkIfNotLinked(collectionId, dedupeResult.entity().getId(), orderIndex);
    } else {
      contentService.linkContentToCollection(
          collectionId, dedupeResult.entity().getId(), orderIndex);
    }
    return keywordFailures;
  }

  /**
   * Link an image to a collection unless that link already exists. Used by the UPDATE and SKIP
   * paths, where the image may already be a member from an earlier upload.
   */
  private void linkIfNotLinked(Long collectionId, Long contentId, int orderIndex) {
    Optional<CollectionContentEntity> existing =
        collectionRepository.findContentByCollectionIdAndContentId(collectionId, contentId);
    if (existing.isPresent()) {
      return;
    }
    contentService.linkContentToCollection(collectionId, contentId, orderIndex);
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
   * <p>Non-images and GIFs are skipped. The work is S3 upload, resize, WebP conversion and the
   * optional RAW upload -- no database calls.
   *
   * @param file The image file to process
   * @param rawFilePath Optional path to the RAW file
   * @return Prepared image data, or null if processing failed
   */
  private PreparedImage prepareImageAsync(MultipartFile file, String rawFilePath) {
    String filename = file.getOriginalFilename();
    try {
      log.trace("Preparing image: {}", filename);

      if (file.getContentType() == null
          || !file.getContentType().startsWith("image/")
          || file.getContentType().equals("image/gif")) {
        log.trace("Skipping non-image or GIF: {}", filename);
        return null;
      }

      ImageProcessingService.PreparedImageData prepared =
          imageProcessingService.prepareImageForUpload(file, rawFilePath);

      return new PreparedImage(prepared, filename);

    } catch (Exception e) {
      log.error("Failed to prepare image {}: {}", filename, e.getMessage(), e);
      return null;
    }
  }

  /**
   * Save prepared images to the database. Handles all DB work: camera/lens lookups, duplicate
   * detection, entity saves, and collection join entries.
   *
   * <p>Each image saves in its own transaction, via the {@code @Transactional} repository methods,
   * so one failure cannot cascade and kill the whole batch. A keyword failure is reported alongside
   * the image rather than replacing it, because the image itself succeeded.
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
        ImageProcessingService.DedupeResult dedupeResult =
            imageProcessingService.savePreparedImageWithDedupe(prepared.data(), null);

        if (dedupeResult.action() == ImageProcessingService.DedupeAction.SKIP) {
          skipped.add(
              new ImageUploadResult.SkippedFile(
                  prepared.filename(), "Duplicate with same or older export date"));
          orderIndex++;
          continue;
        }

        wireImageAfterDedupe(
                dedupeResult,
                prepared.data().extractedTags(),
                prepared.data().extractedPeople(),
                prepared.data().rawFilePath(),
                prepared.data().imageYear(),
                prepared.data().imageMonth(),
                collectionId,
                orderIndex)
            .forEach(
                message ->
                    failures.add(new ImageUploadResult.FileError(prepared.filename(), message)));

        ContentModel contentModel =
            contentModelConverter.convertRegularContentEntityToModel(dedupeResult.entity());
        createdImages.add(ContentService.castContentModel(contentModel, ContentModels.Image.class));

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
   * without failing the upload -- the staging link has its own try/catch so a metadata error cannot
   * block it.
   */
  private void postUploadProcessing(
      Long collectionId,
      CollectionRequests.Create createRequest,
      List<ContentModels.Image> uploadedImages) {

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

    try {
      linkToStagingCollection(collectionId);
    } catch (Exception e) {
      log.error("Failed to link collection {} to staging: {}", collectionId, e.getMessage(), e);
    }
  }

  private void deriveCollectionDate(
      CollectionEntity entity, List<ContentModels.Image> uploadedImages) {
    LocalDateTime earliest = null;
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
