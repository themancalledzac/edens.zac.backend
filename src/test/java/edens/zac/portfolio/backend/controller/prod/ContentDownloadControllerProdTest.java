package edens.zac.portfolio.backend.controller.prod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.services.ClientGalleryAuthService;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.ContentService;
import edens.zac.portfolio.backend.types.CollectionType;
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

@ExtendWith(MockitoExtension.class)
class ContentDownloadControllerProdTest {

  private static final String CLOUDFRONT_DOMAIN = "cdn.example.com";
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
    ReflectionTestUtils.setField(controller, "cloudfrontDomain", CLOUDFRONT_DOMAIN);
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

  private static ContentImageEntity image(Long id, String filename, String suffix) {
    return ContentImageEntity.builder()
        .id(id)
        .imageUrlWeb("https://" + CLOUDFRONT_DOMAIN + "/Image/Web/2025/01/" + suffix)
        .originalFilename(filename)
        .build();
  }

  private static CollectionEntity protectedGallery() {
    return CollectionEntity.builder()
        .id(1L)
        .type(CollectionType.CLIENT_GALLERY)
        .title("Smith Wedding")
        .slug("smith-wedding")
        .visible(false)
        .passwordHash("$2a$10$fakehashfakehashfakehashfakeha")
        .build();
  }

  private static CollectionEntity openCollection() {
    return CollectionEntity.builder()
        .id(2L)
        .type(CollectionType.PORTFOLIO)
        .title("Open Portfolio")
        .slug("open-portfolio")
        .visible(true)
        .passwordHash(null)
        .build();
  }

  // ---------------------------------------------------------------------------
  //  Image download
  // ---------------------------------------------------------------------------

  @Nested
  class ImageDownload {

    @Test
    void protectedCollection_validCookie_returns200WithBytes() throws Exception {
      ContentImageEntity img = image(10L, "smith-001.jpg", "smith-001.webp");
      byte[] body = new byte[] {0x52, 0x49, 0x46, 0x46}; // RIFF magic, just a fake stub
      when(contentService.findImageById(10L)).thenReturn(img);
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.of(protectedGallery()));
      when(clientGalleryAuthService.validateAccessToken("smith-wedding", "tok-123"))
          .thenReturn(true);
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
      ContentImageEntity img = image(10L, "smith-001.jpg", "smith-001.webp");
      when(contentService.findImageById(10L)).thenReturn(img);
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.of(protectedGallery()));

      mockMvc
          .perform(get("/api/read/content/images/10/download"))
          .andExpect(status().isUnauthorized());

      verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    void unprotectedCollection_noCookie_returns200() throws Exception {
      ContentImageEntity img = image(11L, "open-001.jpg", "open-001.webp");
      byte[] body = new byte[] {0x01, 0x02, 0x03, 0x04};
      when(contentService.findImageById(11L)).thenReturn(img);
      when(contentService.findCollectionForImage(11L)).thenReturn(Optional.of(openCollection()));
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
      ContentImageEntity img = image(12L, "orphan.jpg", "orphan.webp");
      byte[] body = new byte[] {0x42};
      when(contentService.findImageById(12L)).thenReturn(img);
      when(contentService.findCollectionForImage(12L)).thenReturn(Optional.empty());
      when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(fakeS3Stream(body));

      mockMvc.perform(get("/api/read/content/images/12/download")).andExpect(status().isOk());
    }

    @Test
    void formatOriginal_returns400() throws Exception {
      mockMvc
          .perform(get("/api/read/content/images/10/download").param("format", "original"))
          .andExpect(status().isBadRequest());

      verify(contentService, never()).findImageById(org.mockito.ArgumentMatchers.anyLong());
      verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    void invalidCookie_returns401() throws Exception {
      ContentImageEntity img = image(10L, "smith-001.jpg", "smith-001.webp");
      when(contentService.findImageById(10L)).thenReturn(img);
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.of(protectedGallery()));
      when(clientGalleryAuthService.validateAccessToken("smith-wedding", "garbage"))
          .thenReturn(false);

      mockMvc
          .perform(
              get("/api/read/content/images/10/download")
                  .cookie(new Cookie("gallery_access_smith-wedding", "garbage")))
          .andExpect(status().isUnauthorized());

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
      ContentImageEntity img1 = image(10L, "first.jpg", "first.webp");
      ContentImageEntity img2 = image(11L, "second.jpg", "second.webp");
      when(collectionService.findEntityBySlug("smith-wedding")).thenReturn(gallery);
      when(clientGalleryAuthService.validateAccessToken("smith-wedding", "tok-123"))
          .thenReturn(true);
      when(contentService.findImagesForCollection(1L)).thenReturn(List.of(img1, img2));

      // Each S3 fetch returns a fresh stream (the controller reads them sequentially).
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

      // Verify ZIP contents
      byte[] zipBytes = result.getResponse().getContentAsByteArray();
      try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
        ZipEntry entry;
        int count = 0;
        boolean sawFirst = false;
        boolean sawSecond = false;
        while ((entry = zis.getNextEntry()) != null) {
          count++;
          // Each entry name is "{seq}_{base}.webp"
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

      verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    void unprotectedCollection_noCookie_returns200() throws Exception {
      CollectionEntity open = openCollection();
      ContentImageEntity img = image(10L, "open.jpg", "open.webp");
      when(collectionService.findEntityBySlug("open-portfolio")).thenReturn(open);
      when(contentService.findImagesForCollection(2L)).thenReturn(List.of(img));
      when(s3Client.getObject(any(GetObjectRequest.class)))
          .thenReturn(fakeS3Stream("zzzz".getBytes()));

      mockMvc
          .perform(get("/api/read/collections/open-portfolio/download"))
          .andExpect(status().isOk())
          .andExpect(header().string("Content-Type", "application/zip"));
    }

    @Test
    void formatOriginal_returns400() throws Exception {
      mockMvc
          .perform(get("/api/read/collections/smith-wedding/download").param("format", "original"))
          .andExpect(status().isBadRequest());

      verify(collectionService, never()).findEntityBySlug(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void zipsRemainingImagesWhenOneS3FetchFails() throws Exception {
      // BE-H2: a per-image S3 failure must not corrupt the rest of the ZIP. The controller
      // writes a placeholder error entry for the failed image and continues with the others.
      CollectionEntity gallery = openCollection();
      ContentImageEntity img1 = image(10L, "first.jpg", "first.webp");
      ContentImageEntity img2 = image(11L, "second.jpg", "second.webp");
      ContentImageEntity img3 = image(12L, "third.jpg", "third.webp");
      when(collectionService.findEntityBySlug("open-portfolio")).thenReturn(gallery);
      when(contentService.findImagesForCollection(2L)).thenReturn(List.of(img1, img2, img3));

      // First image succeeds, second fails with SdkClientException, third succeeds.
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
      CollectionEntity gallery = openCollection();
      when(collectionService.findEntityBySlug("open-portfolio")).thenReturn(gallery);
      when(contentService.findImagesForCollection(2L)).thenReturn(List.of());

      MvcResult result =
          mockMvc
              .perform(get("/api/read/collections/open-portfolio/download"))
              .andExpect(status().isOk())
              .andReturn();

      // ZIP is non-empty (header bytes) but contains no entries.
      byte[] zipBytes = result.getResponse().getContentAsByteArray();
      try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
        assertThat(zis.getNextEntry()).isNull();
      }
    }
  }
}
