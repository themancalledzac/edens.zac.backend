package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.config.GalleryAccessCookies;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.DownloadResolution;
import edens.zac.portfolio.backend.services.ClientGalleryAuthService;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.ContentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
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
 * Authenticated download endpoints for client galleries. Streams assets directly from S3 to the
 * response so memory stays flat regardless of gallery size.
 *
 * <p>This controller stays thin: format/field selection, fallback rules, and filename/MIME
 * decisions all live in {@link ContentService}. The controller is responsible only for HTTP
 * concerns -- parsing parameters, the gallery-access cookie auth gate, and S3 streaming.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>{@code GET /api/read/content/images/{id}/download?format=web} -- single image WebP
 *   <li>{@code GET /api/read/content/images/{id}/download?format=original} -- single image JPEG
 *   <li>{@code GET /api/read/collections/{slug}/download?format=web} -- ZIP of every WebP
 *   <li>{@code GET /api/read/collections/{slug}/download?format=original} -- ZIP of full-res JPEGs
 *       (per-image fallback to WebP when no original is stored)
 * </ul>
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

  // ---------------------------------------------------------------------------
  //  Image download
  // ---------------------------------------------------------------------------

  @GetMapping("/content/images/{id}/download")
  public ResponseEntity<InputStreamResource> downloadImage(
      @PathVariable Long id,
      @RequestParam(defaultValue = "web") String format,
      HttpServletRequest request) {

    // Auth gate: enforce only when a parent collection is password-protected.
    Optional<CollectionEntity> parentCollection = contentService.findCollectionForImage(id);
    if (parentCollection.isPresent() && parentCollection.get().getGalleryPassword() != null) {
      String parentSlug = parentCollection.get().getSlug();
      if (!GalleryAccessCookies.hasValidAccess(request, parentSlug, clientGalleryAuthService)) {
        log.warn("Unauthorized image download (id={}, slug={})", id, parentSlug);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
    }

    DownloadResolution resolution = contentService.resolveImageDownload(id, format);
    GetObjectRequest s3Req =
        GetObjectRequest.builder().bucket(bucketName).key(resolution.s3Key()).build();
    ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(s3Req);

    ResponseEntity.BodyBuilder builder =
        ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(resolution.contentType()))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + resolution.filename() + "\"");
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

    CollectionEntity collection = collectionService.findEntityBySlug(slug);

    if (collection.getGalleryPassword() != null
        && !GalleryAccessCookies.hasValidAccess(request, slug, clientGalleryAuthService)) {
      log.warn("Unauthorized collection ZIP download (slug={})", slug);
      response.sendError(HttpStatus.UNAUTHORIZED.value());
      return;
    }

    List<DownloadResolution> entries =
        contentService.resolveCollectionDownloadEntries(collection.getId(), format);

    String zipName = contentService.collectionZipFilename(collection.getSlug(), collection.getId());
    response.setContentType("application/zip");
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipName + "\"");

    try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
      int seq = 0;
      for (DownloadResolution entry : entries) {
        // Defensive against duplicate names within a ZIP (S3 keys are unique, original_filenames
        // are not). Prefix with a sequence number so ZipOutputStream never throws on duplicates.
        GetObjectRequest s3Req =
            GetObjectRequest.builder().bucket(bucketName).key(entry.s3Key()).build();
        try (ResponseInputStream<GetObjectResponse> in = s3Client.getObject(s3Req)) {
          ZipEntry zipEntry = new ZipEntry(String.format("%03d_%s", seq++, entry.filename()));
          zos.putNextEntry(zipEntry);
          in.transferTo(zos);
          zos.closeEntry();
        } catch (SdkException e) {
          // Per-image failure must not corrupt the rest of the ZIP. Write a placeholder so the
          // recipient sees something is missing without us tearing down the whole download.
          log.warn(
              "Failed to fetch S3 object for ZIP entry (key={}): {}",
              entry.s3Key(),
              e.getMessage());
          ZipEntry errorEntry =
              new ZipEntry(String.format("%03d_%s.error.txt", seq++, entry.filename()));
          zos.putNextEntry(errorEntry);
          zos.write(
              ("Could not include this image: " + e.getClass().getSimpleName())
                  .getBytes(StandardCharsets.UTF_8));
          zos.closeEntry();
        }
      }
      zos.finish();
    }
    log.info("Streamed ZIP download (slug={}, format={}, count={})", slug, format, entries.size());
  }
}
