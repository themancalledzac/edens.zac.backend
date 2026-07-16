package edens.zac.portfolio.backend.controller.prod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.DownloadResolution;
import edens.zac.portfolio.backend.services.ClientGalleryAuthService;
import edens.zac.portfolio.backend.services.CollectionAccessService;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.ContentService;
import edens.zac.portfolio.backend.services.DownloadUrlService;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller-level tests. The controller is intentionally thin: it authorizes, then redirects (302)
 * to a presigned S3 URL produced by {@link DownloadUrlService} — the bytes never flow through the
 * response (that would hit the Amplify 5.72 MB proxy cap). So these tests stub the service methods
 * and focus on auth gating, the single-vs-ZIP branch, and exception-to-HTTP-status mapping.
 * Presign/ZIP mechanics live in {@code DownloadUrlServiceTest}; resolution in {@code
 * ContentServiceDownloadTest}.
 */
@ExtendWith(MockitoExtension.class)
class ContentDownloadControllerProdTest {

  private static final URI PRESIGNED = URI.create("https://bucket.s3.amazonaws.com/obj?sig=abc");
  private static final URI ZIP_PRESIGNED =
      URI.create("https://bucket.s3.amazonaws.com/zip?sig=xyz");

  private MockMvc mockMvc;

  @Mock private CollectionService collectionService;
  @Mock private ContentService contentService;
  @Mock private ClientGalleryAuthService clientGalleryAuthService;
  @Mock private CollectionAccessService collectionAccessService;
  @Mock private DownloadUrlService downloadUrlService;

  @InjectMocks private ContentDownloadControllerProd controller;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static DownloadResolution webResolution(String filename) {
    return new DownloadResolution("Image/Web/2025/01/" + filename, ".webp", "image/webp", filename);
  }

  private static DownloadResolution jpegResolution(String filename) {
    return new DownloadResolution(
        "Image/Original/2025/01/" + filename, ".jpg", "image/jpeg", filename);
  }

  private static CollectionEntity protectedGallery() {
    return CollectionEntity.builder()
        .id(1L)
        .type(CollectionType.CLIENT_GALLERY)
        .title("Smith Wedding")
        .slug("smith-wedding")
        .visibility(CollectionVisibility.UNLISTED)
        .galleryPassword("sunshine")
        .build();
  }

  private static CollectionEntity openCollection() {
    return CollectionEntity.builder()
        .id(2L)
        .type(CollectionType.PORTFOLIO)
        .title("Open Portfolio")
        .slug("open-portfolio")
        .visibility(CollectionVisibility.LISTED)
        .galleryPassword(null)
        .build();
  }

  // ---------------------------------------------------------------------------
  //  Image download
  // ---------------------------------------------------------------------------

  @Nested
  class ImageDownload {

    @Test
    void protectedCollection_validCookie_redirectsToPresignedUrl() throws Exception {
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.of(protectedGallery()));
      when(clientGalleryAuthService.validateAccessToken("smith-wedding", "tok-123"))
          .thenReturn(true);
      when(contentService.resolveImageDownload(10L, "web"))
          .thenReturn(webResolution("smith-001.webp"));
      when(downloadUrlService.presignObject(
              "Image/Web/2025/01/smith-001.webp", "image/webp", "smith-001.webp"))
          .thenReturn(PRESIGNED);

      mockMvc
          .perform(
              get("/api/read/content/images/10/download")
                  .cookie(new Cookie("gallery_access_smith-wedding", "tok-123")))
          .andExpect(status().isFound())
          .andExpect(header().string("Location", PRESIGNED.toString()));
    }

    @Test
    void protectedCollection_noCookie_returns401() throws Exception {
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.of(protectedGallery()));

      mockMvc
          .perform(get("/api/read/content/images/10/download"))
          .andExpect(status().isUnauthorized());

      verify(contentService, never()).resolveImageDownload(any(), any());
      verify(downloadUrlService, never()).presignObject(any(), any(), any());
    }

    @Test
    void unprotectedCollection_noCookie_redirects() throws Exception {
      when(contentService.findCollectionForImage(11L)).thenReturn(Optional.of(openCollection()));
      when(contentService.resolveImageDownload(11L, "web"))
          .thenReturn(webResolution("open-001.webp"));
      when(downloadUrlService.presignObject(any(), any(), any())).thenReturn(PRESIGNED);

      mockMvc
          .perform(get("/api/read/content/images/11/download"))
          .andExpect(status().isFound())
          .andExpect(header().string("Location", PRESIGNED.toString()));

      verify(clientGalleryAuthService, never()).validateAccessToken(any(), any());
    }

    @Test
    void formatOriginal_redirectsWithJpegResolution() throws Exception {
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.of(protectedGallery()));
      when(clientGalleryAuthService.validateAccessToken("smith-wedding", "tok-123"))
          .thenReturn(true);
      when(contentService.resolveImageDownload(10L, "original"))
          .thenReturn(jpegResolution("smith-001.jpg"));
      when(downloadUrlService.presignObject(
              "Image/Original/2025/01/smith-001.jpg", "image/jpeg", "smith-001.jpg"))
          .thenReturn(PRESIGNED);

      mockMvc
          .perform(
              get("/api/read/content/images/10/download")
                  .param("format", "original")
                  .cookie(new Cookie("gallery_access_smith-wedding", "tok-123")))
          .andExpect(status().isFound())
          .andExpect(header().string("Location", PRESIGNED.toString()));
    }

    @Test
    void serviceThrowsNotFound_returns404() throws Exception {
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.empty());
      when(contentService.resolveImageDownload(10L, "original"))
          .thenThrow(new ResourceNotFoundException("No original available for image 10"));

      mockMvc
          .perform(get("/api/read/content/images/10/download").param("format", "original"))
          .andExpect(status().isNotFound());

      verify(downloadUrlService, never()).presignObject(any(), any(), any());
    }

    @Test
    void formatUnsupported_returns400() throws Exception {
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.empty());
      when(contentService.resolveImageDownload(10L, "raw"))
          .thenThrow(new IllegalArgumentException("Unsupported download format: raw"));

      mockMvc
          .perform(get("/api/read/content/images/10/download").param("format", "raw"))
          .andExpect(status().isBadRequest());

      verify(downloadUrlService, never()).presignObject(any(), any(), any());
    }
  }

  // ---------------------------------------------------------------------------
  //  Collection download
  // ---------------------------------------------------------------------------

  @Nested
  class CollectionDownload {

    @Test
    void multipleImages_buildsZipAndRedirects() throws Exception {
      when(collectionService.findEntityBySlug("smith-wedding")).thenReturn(protectedGallery());
      when(clientGalleryAuthService.validateAccessToken("smith-wedding", "tok-123"))
          .thenReturn(true);
      when(contentService.resolveCollectionDownloadEntries(1L, "web", null))
          .thenReturn(List.of(webResolution("first.webp"), webResolution("second.webp")));
      when(contentService.collectionZipFilename("smith-wedding", 1L))
          .thenReturn("smith-wedding-1.zip");
      when(downloadUrlService.zipToS3AndPresign(any(), eq("smith-wedding-1.zip")))
          .thenReturn(ZIP_PRESIGNED);

      mockMvc
          .perform(
              get("/api/read/collections/smith-wedding/download")
                  .cookie(new Cookie("gallery_access_smith-wedding", "tok-123")))
          .andExpect(status().isFound())
          .andExpect(header().string("Location", ZIP_PRESIGNED.toString()));
    }

    @Test
    void singleImage_skipsZipAndRedirectsToImage() throws Exception {
      // The client-reported case: "Download Selected" with ONE image (imageIds=1583). No ZIP —
      // redirect straight to the image's presigned URL so it isn't wrapped in a one-entry archive.
      when(collectionService.findEntityBySlug("smith-wedding")).thenReturn(protectedGallery());
      when(clientGalleryAuthService.validateAccessToken("smith-wedding", "tok-123"))
          .thenReturn(true);
      when(contentService.resolveCollectionDownloadEntries(eq(1L), eq("original"), any()))
          .thenReturn(List.of(jpegResolution("only.jpg")));
      when(downloadUrlService.presignObject(
              "Image/Original/2025/01/only.jpg", "image/jpeg", "only.jpg"))
          .thenReturn(PRESIGNED);

      mockMvc
          .perform(
              get("/api/read/collections/smith-wedding/download")
                  .param("format", "original")
                  .param("imageIds", "1583")
                  .cookie(new Cookie("gallery_access_smith-wedding", "tok-123")))
          .andExpect(status().isFound())
          .andExpect(header().string("Location", PRESIGNED.toString()));

      verify(downloadUrlService, never()).zipToS3AndPresign(any(), any());
    }

    @Test
    void protectedCollection_noCookie_returns401() throws Exception {
      when(collectionService.findEntityBySlug("smith-wedding")).thenReturn(protectedGallery());

      mockMvc
          .perform(get("/api/read/collections/smith-wedding/download"))
          .andExpect(status().isUnauthorized());

      verify(contentService, never()).resolveCollectionDownloadEntries(any(), any(), any());
      verify(downloadUrlService, never()).zipToS3AndPresign(any(), any());
      verify(downloadUrlService, never()).presignObject(any(), any(), any());
    }

    @Test
    void emptyResolution_returns404() throws Exception {
      when(collectionService.findEntityBySlug("open-portfolio")).thenReturn(openCollection());
      when(contentService.resolveCollectionDownloadEntries(2L, "web", null)).thenReturn(List.of());

      mockMvc
          .perform(get("/api/read/collections/open-portfolio/download"))
          .andExpect(status().isNotFound());

      verify(downloadUrlService, never()).zipToS3AndPresign(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void imageIdsSubset_passesIdsToResolverAndUsesSelectionFilename() throws Exception {
      when(collectionService.findEntityBySlug("smith-wedding")).thenReturn(protectedGallery());
      when(clientGalleryAuthService.validateAccessToken("smith-wedding", "tok-123"))
          .thenReturn(true);
      when(contentService.resolveCollectionDownloadEntries(eq(1L), eq("web"), any()))
          .thenReturn(List.of(webResolution("first.webp"), webResolution("third.webp")));
      when(contentService.collectionZipFilename("smith-wedding", 1L))
          .thenReturn("smith-wedding-1.zip");
      when(downloadUrlService.zipToS3AndPresign(any(), any())).thenReturn(ZIP_PRESIGNED);

      mockMvc
          .perform(
              get("/api/read/collections/smith-wedding/download")
                  .param("format", "web")
                  .param("imageIds", "1", "3")
                  .cookie(new Cookie("gallery_access_smith-wedding", "tok-123")))
          .andExpect(status().isFound());

      // Subset ZIPs carry a -selection-<count> suffix so they don't collide with the "all" ZIP.
      verify(downloadUrlService).zipToS3AndPresign(any(), eq("smith-wedding-1-selection-2.zip"));

      ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
      verify(contentService)
          .resolveCollectionDownloadEntries(eq(1L), eq("web"), idsCaptor.capture());
      assertThat(idsCaptor.getValue()).containsExactly(1L, 3L);
    }
  }
}
