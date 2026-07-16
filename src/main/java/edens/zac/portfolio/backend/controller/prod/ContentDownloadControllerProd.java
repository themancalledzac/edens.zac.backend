package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.config.GalleryAccessCookies;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.DownloadResolution;
import edens.zac.portfolio.backend.services.ClientGalleryAuthService;
import edens.zac.portfolio.backend.services.CollectionAccessService;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.ContentService;
import edens.zac.portfolio.backend.services.DownloadUrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated download endpoints for client galleries. Instead of streaming bytes through the
 * response, these authorize the request and then <strong>302-redirect to a short-lived S3 presigned
 * URL</strong>, so the browser pulls the actual file straight from S3.
 *
 * <p>This is deliberate: the frontend runs on AWS Amplify Web Compute, whose Next.js BFF caps any
 * proxied HTTP response at 5.72 MB. A full-resolution image or a multi-image ZIP exceeds that and
 * is killed at the CloudFront/compute layer (the client-reported {@code 413 Content Too Large}).
 * The redirect body is a few hundred bytes — it passes the cap — and the presigned URL the browser
 * then follows hits S3 directly, which has no such limit. See {@link DownloadUrlService}.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>{@code GET /api/read/content/images/{id}/download?format=web|original} — single image
 *   <li>{@code GET /api/read/collections/{slug}/download?format=web|original} — ZIP of the
 *       collection
 *   <li>{@code GET /api/read/collections/{slug}/download?...&imageIds=1,2,3} — ZIP of the selected
 *       subset (a single selected image redirects straight to that image, no ZIP)
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/read")
public class ContentDownloadControllerProd {

  private final CollectionService collectionService;
  private final ContentService contentService;
  private final ClientGalleryAuthService clientGalleryAuthService;
  private final CollectionAccessService collectionAccessService;
  private final DownloadUrlService downloadUrlService;

  // ---------------------------------------------------------------------------
  //  Image download
  // ---------------------------------------------------------------------------

  @GetMapping("/content/images/{id}/download")
  public ResponseEntity<Void> downloadImage(
      @PathVariable Long id,
      @RequestParam(defaultValue = "web") String format,
      HttpServletRequest request) {

    // Auth gate: enforce only when a parent collection is password-protected.
    Optional<CollectionEntity> parentCollection = contentService.findCollectionForImage(id);
    if (parentCollection.isPresent() && parentCollection.get().getGalleryPassword() != null) {
      if (!isDownloadAuthorized(request, parentCollection.get())) {
        log.warn(
            "Unauthorized image download (id={}, slug={})", id, parentCollection.get().getSlug());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
    }

    DownloadResolution resolution = contentService.resolveImageDownload(id, format);
    URI url =
        downloadUrlService.presignObject(
            resolution.s3Key(), resolution.contentType(), resolution.filename());
    return ResponseEntity.status(HttpStatus.FOUND).location(url).build();
  }

  // ---------------------------------------------------------------------------
  //  Collection ZIP download
  // ---------------------------------------------------------------------------

  @GetMapping("/collections/{slug}/download")
  public void downloadCollection(
      @PathVariable String slug,
      @RequestParam(defaultValue = "web") String format,
      @RequestParam(required = false) List<Long> imageIds,
      HttpServletRequest request,
      HttpServletResponse response)
      throws IOException {

    CollectionEntity collection = collectionService.findEntityBySlug(slug);

    if (collection.getGalleryPassword() != null && !isDownloadAuthorized(request, collection)) {
      log.warn("Unauthorized collection download (slug={})", slug);
      response.sendError(HttpStatus.UNAUTHORIZED.value());
      return;
    }

    List<DownloadResolution> entries =
        contentService.resolveCollectionDownloadEntries(collection.getId(), format, imageIds);

    if (entries.isEmpty()) {
      response.sendError(HttpStatus.NOT_FOUND.value());
      return;
    }

    boolean isSubset = imageIds != null && !imageIds.isEmpty();
    URI url;
    if (entries.size() == 1) {
      // A single resolved image (including a one-image "Download Selected") skips the ZIP and
      // redirects straight to that image — the recipient gets the file, not a one-entry archive.
      DownloadResolution only = entries.get(0);
      url = downloadUrlService.presignObject(only.s3Key(), only.contentType(), only.filename());
    } else {
      String zipName =
          contentService.collectionZipFilename(collection.getSlug(), collection.getId());
      if (isSubset) {
        // Distinguish a selected-subset ZIP from the whole-collection one so a client who downloads
        // "all" and then a subset doesn't get two identically named files. Count is an int -- safe.
        zipName = zipName.replaceFirst("\\.zip$", "-selection-" + imageIds.size() + ".zip");
      }
      url = downloadUrlService.zipToS3AndPresign(entries, zipName);
    }

    response.setStatus(HttpStatus.FOUND.value());
    response.setHeader(HttpHeaders.LOCATION, url.toString());
    log.info("Redirected download (slug={}, format={}, count={})", slug, format, entries.size());
  }

  // ---------------------------------------------------------------------------
  //  Auth helpers
  // ---------------------------------------------------------------------------

  /**
   * Session+grant download authorization, falling back to the existing cookie gate. A non-null
   * {@code galleryPassword} is already confirmed by callers before invoking this helper.
   */
  private boolean isDownloadAuthorized(HttpServletRequest request, CollectionEntity collection) {
    Long userId = currentUserId();
    if (userId != null && collectionAccessService.isClient(userId, collection.getId())) {
      return true;
    }
    return GalleryAccessCookies.hasValidAccess(
        request, collection.getSlug(), clientGalleryAuthService);
  }

  /** The authenticated principal's user id, or null when the request is anonymous. */
  private static Long currentUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) ? p.userId() : null;
  }
}
