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
import java.io.IOException;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    // Auth gate: EVERY password-protected parent must authorize this request. An image can sit in
    // several collections at once, so resolving one arbitrary parent let an unprotected wrapper
    // waive a protected gallery's password. Fail closed -- a cookie for one gallery does not
    // unlock an image that also lives in another.
    for (CollectionEntity parentCollection : contentService.findProtectedCollectionsForImage(id)) {
      if (!isDownloadAuthorized(request, parentCollection)) {
        log.warn("Unauthorized image download (id={}, slug={})", id, parentCollection.getSlug());
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

  /**
   * Redirect to a presigned URL for a collection download: a ZIP of the collection, or -- when
   * exactly one image resolves -- that image on its own, so a one-image "Download Selected" is not
   * wrapped in a single-entry archive.
   *
   * <p>The auth gate runs twice. First the slug in the URL, then every password-protected
   * collection that also holds an image this download would return. Checking only the slug let a
   * public wrapper waive a protected gallery's password, and with {@code ?imageIds=<one>} the
   * response is not even a ZIP but a 302 to a presigned full-resolution original. This fails closed
   * on EVERY protected parent of every image served, exactly like the per-image endpoint above.
   *
   * <p>A subset ZIP gets a {@code -selection-<count>} suffix so a client who downloads "all" and
   * then a selection does not end up with two identically named files. The count is an int, so the
   * suffix is always safe to build.
   *
   * @return {@code 302} to the presigned URL, {@code 401} when any gating collection is not
   *     authorized, or {@code 404} when nothing resolves
   * @throws IOException when building or uploading the ZIP fails; GlobalExceptionHandler maps it to
   *     a 500
   */
  @GetMapping("/collections/{slug}/download")
  public ResponseEntity<Void> downloadCollection(
      @PathVariable String slug,
      @RequestParam(defaultValue = "web") String format,
      @RequestParam(required = false) List<Long> imageIds,
      HttpServletRequest request)
      throws IOException {

    CollectionEntity collection = collectionService.findEntityBySlug(slug);

    if (collection.getGalleryPassword() != null && !isDownloadAuthorized(request, collection)) {
      log.warn("Unauthorized collection download (slug={})", slug);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    for (CollectionEntity gatingCollection :
        contentService.findProtectedCollectionsForCollectionDownload(
            collection.getId(), imageIds)) {
      if (!isDownloadAuthorized(request, gatingCollection)) {
        log.warn(
            "Unauthorized collection download (slug={}, gatedBy={})",
            slug,
            gatingCollection.getSlug());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
    }

    List<DownloadResolution> entries =
        contentService.resolveCollectionDownloadEntries(collection.getId(), format, imageIds);

    if (entries.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    boolean isSubset = imageIds != null && !imageIds.isEmpty();
    URI url;
    if (entries.size() == 1) {
      DownloadResolution only = entries.get(0);
      url = downloadUrlService.presignObject(only.s3Key(), only.contentType(), only.filename());
    } else {
      String zipName =
          contentService.collectionZipFilename(collection.getSlug(), collection.getId());
      if (isSubset) {
        zipName = zipName.replaceFirst("\\.zip$", "-selection-" + imageIds.size() + ".zip");
      }
      url = downloadUrlService.zipToS3AndPresign(entries, zipName);
    }

    log.info("Redirected download (slug={}, format={}, count={})", slug, format, entries.size());
    return ResponseEntity.status(HttpStatus.FOUND).location(url).build();
  }

  // ---------------------------------------------------------------------------
  //  Auth helpers
  // ---------------------------------------------------------------------------

  /**
   * Session+grant download authorization, falling back to the existing cookie gate.
   *
   * <p>The cookie fallback goes through the four-arg {@link GalleryAccessCookies#hasValidAccess}
   * overload — the same one the public READ gate uses ({@code
   * CollectionService.isGalleryAccessAuthorized}). The three-arg overload it replaced accepted only
   * the per-slug cookie, so a viewer who unlocked a PARENT gallery could VIEW a propagated
   * CLIENT_GALLERY child (read gate: password-fingerprint cookie accepted) but got 401 downloading
   * from it (download gate: per-slug cookie only) — while the frontend rendered an enabled download
   * button. Read and download now grant on identical evidence.
   *
   * <p>The four-arg overload short-circuits to {@code true} on a null/blank password, which cannot
   * widen this gate: every caller supplies a collection already known to be protected. The two
   * loops take theirs from {@code findProtectedCollectionsForImage} / {@code
   * findProtectedCollectionsForCollectionDownload}, which filter on {@code galleryPassword !=
   * null}; the slug-level check tests the same field inline. A non-null-but-empty password is
   * unreachable — {@code GalleryAccessRequest.password} is {@code @Size(min = 4)}, and the only
   * other writer copies an already-validated parent password onto a child. Beyond the short-circuit
   * the overload is a strict superset of the three-arg one: it tries the identical per-slug cookie
   * first, and the extra grant it adds requires an HMAC-signed fingerprint cookie matching THIS
   * collection's current password.
   */
  private boolean isDownloadAuthorized(HttpServletRequest request, CollectionEntity collection) {
    Long userId = currentUserId();
    if (userId != null && collectionAccessService.isClient(userId, collection.getId())) {
      return true;
    }
    return GalleryAccessCookies.hasValidAccess(
        request, collection.getSlug(), collection.getGalleryPassword(), clientGalleryAuthService);
  }

  /** The authenticated principal's user id, or null when the request is anonymous. */
  private static Long currentUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) ? p.userId() : null;
  }
}
