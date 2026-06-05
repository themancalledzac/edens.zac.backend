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
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.ContentService;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * Controller-level tests. The controller is intentionally thin -- format-vs-field, MIME, and
 * extension decisions all live in {@link ContentService}, so these tests stub the high-level
 * service methods ({@code resolveImageDownload}, {@code resolveCollectionDownloadEntries}) and
 * focus on auth gating, S3 streaming, and exception-to-HTTP-status mapping. Resolution logic is
 * exercised separately in {@code ContentServiceDownloadTest}.
 */
@ExtendWith(MockitoExtension.class)
class ContentDownloadControllerProdTest {

  private static final String BUCKET = "test-bucket";

  private MockMvc mockMvc;

  @Mock private S3Client s3Client;
  @Mock private CollectionService collectionService;
  @Mock private ContentService contentService;
  @Mock private ClientGalleryAuthService clientGalleryAuthService;

  @InjectMocks private ContentDownloadControllerProd controller;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(controller, "bucketName", BUCKET);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  // ---------------------------------------------------------------------------
  //  Helpers
  // ---------------------------------------------------------------------------

  private static ResponseInputStream<GetObjectResponse> fakeS3Stream(byte[] bytes) {
    GetObjectResponse meta = GetObjectResponse.builder().contentLength((long) bytes.length).build();
    return new ResponseInputStream<>(
        meta, AbortableInputStream.create(new ByteArrayInputStream(bytes)));
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
    void protectedCollection_validCookie_returns200WithBytes() throws Exception {
      byte[] body = new byte[] {0x52, 0x49, 0x46, 0x46}; // RIFF magic, just a fake stub
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.of(protectedGallery()));
      when(clientGalleryAuthService.validateAccessToken("smith-wedding", "tok-123"))
          .thenReturn(true);
      when(contentService.resolveImageDownload(10L, "web"))
          .thenReturn(webResolution("smith-001.webp"));
      when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(fakeS3Stream(body));

      MvcResult result =
          mockMvc
              .perform(
                  get("/api/read/content/images/10/download")
                      .cookie(new Cookie("gallery_access_smith-wedding", "tok-123")))
              .andExpect(status().isOk())
              .andExpect(header().string("Content-Type", "image/webp"))
              .andExpect(
                  header()
                      .string(
                          "Content-Disposition",
                          org.hamcrest.Matchers.startsWith("attachment; filename=\"")))
              .andReturn();

      assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(body);
    }

    @Test
    void protectedCollection_noCookie_returns401() throws Exception {
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.of(protectedGallery()));

      mockMvc
          .perform(get("/api/read/content/images/10/download"))
          .andExpect(status().isUnauthorized());

      verify(contentService, never()).resolveImageDownload(any(), any());
      verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    void unprotectedCollection_noCookie_returns200() throws Exception {
      byte[] body = new byte[] {0x01, 0x02, 0x03, 0x04};
      when(contentService.findCollectionForImage(11L)).thenReturn(Optional.of(openCollection()));
      when(contentService.resolveImageDownload(11L, "web"))
          .thenReturn(webResolution("open-001.webp"));
      when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(fakeS3Stream(body));

      MvcResult result =
          mockMvc
              .perform(get("/api/read/content/images/11/download"))
              .andExpect(status().isOk())
              .andReturn();

      assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(body);
      verify(clientGalleryAuthService, never())
          .validateAccessToken(
              org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void orphanImage_noCollection_returns200() throws Exception {
      byte[] body = new byte[] {0x42};
      when(contentService.findCollectionForImage(12L)).thenReturn(Optional.empty());
      when(contentService.resolveImageDownload(12L, "web"))
          .thenReturn(webResolution("orphan.webp"));
      when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(fakeS3Stream(body));

      mockMvc.perform(get("/api/read/content/images/12/download")).andExpect(status().isOk());
    }

    @Test
    void formatOriginal_returns200WithJpeg() throws Exception {
      byte[] body = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}; // JPEG magic bytes
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.of(protectedGallery()));
      when(clientGalleryAuthService.validateAccessToken("smith-wedding", "tok-123"))
          .thenReturn(true);
      when(contentService.resolveImageDownload(10L, "original"))
          .thenReturn(jpegResolution("smith-001.jpg"));
      when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(fakeS3Stream(body));

      mockMvc
          .perform(
              get("/api/read/content/images/10/download")
                  .param("format", "original")
                  .cookie(new Cookie("gallery_access_smith-wedding", "tok-123")))
          .andExpect(status().isOk())
          .andExpect(header().string("Content-Type", "image/jpeg"))
          .andExpect(
              header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".jpg")));
    }

    @Test
    void formatOriginal_serviceThrowsNotFound_returns404() throws Exception {
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.empty());
      when(contentService.resolveImageDownload(10L, "original"))
          .thenThrow(new ResourceNotFoundException("No original available for image 10"));

      mockMvc
          .perform(get("/api/read/content/images/10/download").param("format", "original"))
          .andExpect(status().isNotFound());

      verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    void formatUnsupported_serviceThrowsIllegalArgument_returns400() throws Exception {
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.empty());
      when(contentService.resolveImageDownload(10L, "raw"))
          .thenThrow(new IllegalArgumentException("Unsupported download format: raw"));

      mockMvc
          .perform(get("/api/read/content/images/10/download").param("format", "raw"))
          .andExpect(status().isBadRequest());

      verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    void invalidCookie_returns401() throws Exception {
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.of(protectedGallery()));
      when(clientGalleryAuthService.validateAccessToken("smith-wedding", "garbage"))
          .thenReturn(false);

      mockMvc
          .perform(
              get("/api/read/content/images/10/download")
                  .cookie(new Cookie("gallery_access_smith-wedding", "garbage")))
          .andExpect(status().isUnauthorized());

      verify(contentService, never()).resolveImageDownload(any(), any());
      verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }
  }

  // ---------------------------------------------------------------------------
  //  Collection ZIP download
  // ---------------------------------------------------------------------------

  @Nested
  class CollectionDownload {

    @Test
    void protectedCollection_validCookie_returnsZipWithEntries() throws Exception {
      CollectionEntity gallery = protectedGallery();
      when(collectionService.findEntityBySlug("smith-wedding")).thenReturn(gallery);
      when(clientGalleryAuthService.validateAccessToken("smith-wedding", "tok-123"))
          .thenReturn(true);
      when(contentService.resolveCollectionDownloadEntries(1L, "web", null))
          .thenReturn(List.of(webResolution("first.webp"), webResolution("second.webp")));
      when(s3Client.getObject(any(GetObjectRequest.class)))
          .thenReturn(fakeS3Stream("aaaa".getBytes()))
          .thenReturn(fakeS3Stream("bbbb".getBytes()));

      MvcResult result =
          mockMvc
              .perform(
                  get("/api/read/collections/smith-wedding/download")
                      .cookie(new Cookie("gallery_access_smith-wedding", "tok-123")))
              .andExpect(status().isOk())
              .andExpect(header().string("Content-Type", "application/zip"))
              .andReturn();

      byte[] zipBytes = result.getResponse().getContentAsByteArray();
      try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
        ZipEntry entry;
        int count = 0;
        boolean sawFirst = false;
        boolean sawSecond = false;
        while ((entry = zis.getNextEntry()) != null) {
          count++;
          if (entry.getName().contains("first")) {
            sawFirst = true;
            assertThat(entry.getName()).endsWith(".webp");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            zis.transferTo(out);
            assertThat(out.toByteArray()).isEqualTo("aaaa".getBytes());
          }
          if (entry.getName().contains("second")) {
            sawSecond = true;
            assertThat(entry.getName()).endsWith(".webp");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            zis.transferTo(out);
            assertThat(out.toByteArray()).isEqualTo("bbbb".getBytes());
          }
        }
        assertThat(count).isEqualTo(2);
        assertThat(sawFirst).isTrue();
        assertThat(sawSecond).isTrue();
      }
    }

    @Test
    void protectedCollection_noCookie_returns401() throws Exception {
      when(collectionService.findEntityBySlug("smith-wedding")).thenReturn(protectedGallery());

      mockMvc
          .perform(get("/api/read/collections/smith-wedding/download"))
          .andExpect(status().isUnauthorized());

      verify(contentService, never()).resolveCollectionDownloadEntries(any(), any(), any());
      verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    void unprotectedCollection_noCookie_returns200() throws Exception {
      when(collectionService.findEntityBySlug("open-portfolio")).thenReturn(openCollection());
      when(contentService.resolveCollectionDownloadEntries(2L, "web", null))
          .thenReturn(List.of(webResolution("open.webp")));
      when(s3Client.getObject(any(GetObjectRequest.class)))
          .thenReturn(fakeS3Stream("zzzz".getBytes()));

      mockMvc
          .perform(get("/api/read/collections/open-portfolio/download"))
          .andExpect(status().isOk())
          .andExpect(header().string("Content-Type", "application/zip"));
    }

    @Test
    void formatOriginal_returnsZipWithJpgEntries() throws Exception {
      when(collectionService.findEntityBySlug("smith-wedding")).thenReturn(protectedGallery());
      when(clientGalleryAuthService.validateAccessToken("smith-wedding", "tok-123"))
          .thenReturn(true);
      when(contentService.resolveCollectionDownloadEntries(1L, "original", null))
          .thenReturn(List.of(jpegResolution("first.jpg"), jpegResolution("second.jpg")));
      when(s3Client.getObject(any(GetObjectRequest.class)))
          .thenReturn(fakeS3Stream("JPEG1".getBytes()))
          .thenReturn(fakeS3Stream("JPEG2".getBytes()));

      MvcResult result =
          mockMvc
              .perform(
                  get("/api/read/collections/smith-wedding/download")
                      .param("format", "original")
                      .cookie(new Cookie("gallery_access_smith-wedding", "tok-123")))
              .andExpect(status().isOk())
              .andReturn();

      byte[] zipBytes = result.getResponse().getContentAsByteArray();
      try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
        ZipEntry entry;
        int count = 0;
        while ((entry = zis.getNextEntry()) != null) {
          count++;
          assertThat(entry.getName()).endsWith(".jpg");
        }
        assertThat(count).isEqualTo(2);
      }
    }

    @Test
    void formatUnsupported_serviceThrowsIllegalArgument_returns400() throws Exception {
      when(collectionService.findEntityBySlug("open-portfolio")).thenReturn(openCollection());
      when(contentService.resolveCollectionDownloadEntries(eq(2L), eq("raw"), eq(null)))
          .thenThrow(new IllegalArgumentException("Unsupported download format: raw"));

      mockMvc
          .perform(get("/api/read/collections/open-portfolio/download").param("format", "raw"))
          .andExpect(status().isBadRequest());

      verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    void zipsRemainingImagesWhenOneS3FetchFails() throws Exception {
      when(collectionService.findEntityBySlug("open-portfolio")).thenReturn(openCollection());
      when(contentService.resolveCollectionDownloadEntries(2L, "web", null))
          .thenReturn(
              List.of(
                  webResolution("first.webp"),
                  webResolution("second.webp"),
                  webResolution("third.webp")));
      when(s3Client.getObject(any(GetObjectRequest.class)))
          .thenReturn(fakeS3Stream("aaaa".getBytes()))
          .thenThrow(SdkClientException.builder().message("connection reset").build())
          .thenReturn(fakeS3Stream("cccc".getBytes()));

      MvcResult result =
          mockMvc
              .perform(get("/api/read/collections/open-portfolio/download"))
              .andExpect(status().isOk())
              .andExpect(header().string("Content-Type", "application/zip"))
              .andReturn();

      byte[] zipBytes = result.getResponse().getContentAsByteArray();
      boolean sawFirst = false;
      boolean sawErrorForSecond = false;
      boolean sawThird = false;
      try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
          if (entry.getName().contains("first") && entry.getName().endsWith(".webp")) {
            sawFirst = true;
          } else if (entry.getName().contains("second") && entry.getName().endsWith(".error.txt")) {
            sawErrorForSecond = true;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            zis.transferTo(out);
            assertThat(new String(out.toByteArray()))
                .contains("Could not include this image")
                .contains("SdkClientException");
          } else if (entry.getName().contains("third") && entry.getName().endsWith(".webp")) {
            sawThird = true;
          }
        }
      }
      assertThat(sawFirst).as("first image survived").isTrue();
      assertThat(sawErrorForSecond).as("second image got placeholder error entry").isTrue();
      assertThat(sawThird).as("third image survived").isTrue();
    }

    @Test
    void emptyCollection_returnsEmptyZip() throws Exception {
      when(collectionService.findEntityBySlug("open-portfolio")).thenReturn(openCollection());
      when(contentService.resolveCollectionDownloadEntries(2L, "web", null)).thenReturn(List.of());

      MvcResult result =
          mockMvc
              .perform(get("/api/read/collections/open-portfolio/download"))
              .andExpect(status().isOk())
              .andReturn();

      byte[] zipBytes = result.getResponse().getContentAsByteArray();
      try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
        assertThat(zis.getNextEntry()).isNull();
      }
    }

    @Test
    @SuppressWarnings("unchecked")
    void imageIdsSubset_passesIdsToResolverAndUsesSelectionFilename() throws Exception {
      CollectionEntity gallery = protectedGallery();
      when(collectionService.findEntityBySlug("smith-wedding")).thenReturn(gallery);
      when(clientGalleryAuthService.validateAccessToken("smith-wedding", "tok-123"))
          .thenReturn(true);
      when(contentService.resolveCollectionDownloadEntries(eq(1L), eq("web"), any()))
          .thenReturn(List.of(webResolution("first.webp"), webResolution("third.webp")));
      when(contentService.collectionZipFilename("smith-wedding", 1L))
          .thenReturn("smith-wedding-1.zip");
      when(s3Client.getObject(any(GetObjectRequest.class)))
          .thenReturn(fakeS3Stream("aaaa".getBytes()))
          .thenReturn(fakeS3Stream("cccc".getBytes()));

      mockMvc
          .perform(
              get("/api/read/collections/smith-wedding/download")
                  .param("format", "web")
                  .param("imageIds", "1", "3")
                  .cookie(new Cookie("gallery_access_smith-wedding", "tok-123")))
          .andExpect(status().isOk())
          .andExpect(header().string("Content-Type", "application/zip"))
          // Subset ZIPs carry a -selection-<count> suffix so they don't collide with the "all" ZIP.
          .andExpect(
              header()
                  .string(
                      "Content-Disposition",
                      org.hamcrest.Matchers.containsString("smith-wedding-1-selection-2.zip")));

      ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
      verify(contentService)
          .resolveCollectionDownloadEntries(eq(1L), eq("web"), idsCaptor.capture());
      assertThat(idsCaptor.getValue()).containsExactly(1L, 3L);
    }

    @Test
    void protectedCollection_noCookie_withImageIds_returns401() throws Exception {
      when(collectionService.findEntityBySlug("smith-wedding")).thenReturn(protectedGallery());

      // Auth gate precedes resolution: a subset request from an unauthorized client is still 401.
      mockMvc
          .perform(
              get("/api/read/collections/smith-wedding/download")
                  .param("format", "web")
                  .param("imageIds", "1", "3"))
          .andExpect(status().isUnauthorized());

      verify(contentService, never()).resolveCollectionDownloadEntries(any(), any(), any());
      verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }
  }
}
