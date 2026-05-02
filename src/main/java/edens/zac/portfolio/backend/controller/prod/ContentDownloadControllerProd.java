package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.config.GalleryAccessCookies;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.services.ClientGalleryAuthService;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.ContentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * Authenticated download endpoints for client galleries. Streams WebP-only assets directly from S3
 * to the response so memory stays flat regardless of gallery size.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>{@code GET /api/read/content/images/{id}/download?format=web} -- single image WebP
 *   <li>{@code GET /api/read/collections/{slug}/download?format=web} -- ZIP of every WebP in the
 *       collection
 * </ul>
 *
 * <p>Both endpoints check the per-slug access cookie when the parent collection has a {@code
 * galleryPassword}. The cookie helpers live in {@link
 * edens.zac.portfolio.backend.config.GalleryAccessCookies} so cookie identity stays in lockstep
 * with the rate-limiter key.
 *
 * <p>{@code format=original} is rejected with {@code 400} for now -- only the WebP variant is
 * exposed to clients in v1.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/read")
public class ContentDownloadControllerProd {

  private final S3Client s3Client;
  private final CollectionService collectionService;
  private final ContentService contentService;
  private final ClientGalleryAuthService clientGalleryAuthService;

  @Value("${aws.portfolio.s3.bucket}")
  private String bucketName;

  @Value("${cloudfront.domain}")
  private String cloudfrontDomain;

  // ---------------------------------------------------------------------------
  //  Image download
  // ---------------------------------------------------------------------------

  @GetMapping("/content/images/{id}/download")
  public ResponseEntity<InputStreamResource> downloadImage(
      @PathVariable Long id,
      @RequestParam(defaultValue = "web") String format,
      HttpServletRequest request) {
    if (!"web".equalsIgnoreCase(format)) {
      log.debug("Rejecting image download (id={}) with unsupported format={}", id, format);
      return ResponseEntity.badRequest().build();
    }

    ContentImageEntity image = contentService.findImageById(id);

    // Auth gate: enforce only when a parent collection is password-protected.
    Optional<CollectionEntity> parentCollection = contentService.findCollectionForImage(id);
    if (parentCollection.isPresent() && parentCollection.get().getGalleryPassword() != null) {
      String parentSlug = parentCollection.get().getSlug();
      if (!GalleryAccessCookies.hasValidAccess(request, parentSlug, clientGalleryAuthService)) {
        log.warn("Unauthorized image download (id={}, slug={})", id, parentSlug);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
    }

    String s3Key = extractS3Key(image.getImageUrlWeb());
    if (s3Key == null) {
      log.error("Image {} has no resolvable S3 key (url={})", id, image.getImageUrlWeb());
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    GetObjectRequest s3Req = GetObjectRequest.builder().bucket(bucketName).key(s3Key).build();
    ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(s3Req);
    String filename = sanitizeFilename(image.getOriginalFilename(), id, ".webp");

    ResponseEntity.BodyBuilder builder =
        ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("image/webp"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
    Long contentLength = stream.response().contentLength();
    if (contentLength != null && contentLength > 0L) {
      builder = builder.contentLength(contentLength);
    }
    return builder.body(new InputStreamResource(stream));
  }

  // ---------------------------------------------------------------------------
  //  Collection ZIP download
  // ---------------------------------------------------------------------------

  @GetMapping("/collections/{slug}/download")
  public void downloadCollection(
      @PathVariable String slug,
      @RequestParam(defaultValue = "web") String format,
      HttpServletRequest request,
      HttpServletResponse response)
      throws IOException {
    if (!"web".equalsIgnoreCase(format)) {
      log.debug("Rejecting collection download (slug={}) with unsupported format={}", slug, format);
      response.sendError(
          HttpStatus.BAD_REQUEST.value(), "Unsupported format. Only 'web' is supported in v1.");
      return;
    }

    CollectionEntity collection = collectionService.findEntityBySlug(slug);

    if (collection.getGalleryPassword() != null
        && !GalleryAccessCookies.hasValidAccess(request, slug, clientGalleryAuthService)) {
      log.warn("Unauthorized collection ZIP download (slug={})", slug);
      response.sendError(HttpStatus.UNAUTHORIZED.value());
      return;
    }

    List<ContentImageEntity> images = contentService.findImagesForCollection(collection.getId());

    String zipName = sanitizeFilename(collection.getSlug(), collection.getId(), ".zip");
    response.setContentType("application/zip");
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipName + "\"");

    try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
      int seq = 0;
      for (ContentImageEntity image : images) {
        String s3Key = extractS3Key(image.getImageUrlWeb());
        if (s3Key == null) {
          log.warn(
              "Skipping image {} in ZIP (no resolvable S3 key, url={})",
              image.getId(),
              image.getImageUrlWeb());
          continue;
        }
        String entryName = sanitizeFilename(image.getOriginalFilename(), image.getId(), ".webp");
        // Defensive against duplicate names within a ZIP (S3 keys are unique, original_filenames
        // are not). Prefix with a sequence number so ZipOutputStream never throws on duplicates.
        GetObjectRequest s3Req = GetObjectRequest.builder().bucket(bucketName).key(s3Key).build();
        try (ResponseInputStream<GetObjectResponse> in = s3Client.getObject(s3Req)) {
          ZipEntry entry = new ZipEntry(String.format("%03d_%s", seq++, entryName));
          zos.putNextEntry(entry);
          in.transferTo(zos);
          zos.closeEntry();
        } catch (SdkException e) {
          // Per-image failure must not corrupt the rest of the ZIP. Write a placeholder so the
          // recipient sees something is missing without us tearing down the whole download.
          log.warn(
              "Failed to fetch S3 object for ZIP entry (imageId={}, key={}): {}",
              image.getId(),
              s3Key,
              e.getMessage());
          ZipEntry errorEntry = new ZipEntry(String.format("%03d_%s.error.txt", seq++, entryName));
          zos.putNextEntry(errorEntry);
          zos.write(
              ("Could not include this image: " + e.getClass().getSimpleName())
                  .getBytes(StandardCharsets.UTF_8));
          zos.closeEntry();
        }
      }
      zos.finish();
    }
    log.info("Streamed ZIP download (slug={}, count={})", slug, images.size());
  }

  // ---------------------------------------------------------------------------
  //  Helpers
  // ---------------------------------------------------------------------------

  /**
   * Translate a CloudFront URL stored on the entity (e.g. {@code
   * https://{cloudfront-domain}/Image/Web/2025/01/foo.webp}) back to the underlying S3 key. Returns
   * {@code null} when the URL is empty or doesn't match the configured CloudFront domain.
   */
  private String extractS3Key(String cloudfrontUrl) {
    if (cloudfrontUrl == null || cloudfrontUrl.isEmpty()) {
      return null;
    }
    String prefix = "https://" + cloudfrontDomain + "/";
    if (cloudfrontUrl.startsWith(prefix)) {
      return cloudfrontUrl.substring(prefix.length());
    }
    log.warn("Cloudfront URL doesn't match configured domain: {}", cloudfrontUrl);
    return null;
  }

  /**
   * Sanitize a filename for use in {@code Content-Disposition} or as a ZIP entry. Strips path
   * traversal and control characters, normalises the extension, falls back to a uuid if the input
   * is unusable.
   */
  private String sanitizeFilename(String original, Object idForFallback, String extension) {
    String base = original;
    if (base != null) {
      // Drop any path component to prevent traversal (`/`, `\`).
      int slashIdx = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
      if (slashIdx >= 0) {
        base = base.substring(slashIdx + 1);
      }
      base = base.replaceAll("[\\p{Cntrl}\"\\\\]", "");
      // Strip any existing image extension, we'll reapply the canonical one.
      base = base.replaceAll("(?i)\\.(jpg|jpeg|webp|png|tif|tiff)$", "");
      base = base.trim();
    }
    if (base == null || base.isEmpty()) {
      base =
          (idForFallback != null ? idForFallback.toString() : "download")
              + "-"
              + UUID.randomUUID().toString().substring(0, 8);
    }
    return base + extension;
  }
}
