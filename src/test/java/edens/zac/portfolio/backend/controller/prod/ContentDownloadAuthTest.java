package edens.zac.portfolio.backend.controller.prod;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.DownloadResolution;
import edens.zac.portfolio.backend.services.ClientGalleryAuthService;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.ContentService;
import edens.zac.portfolio.backend.services.GalleryAccessService;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import edens.zac.portfolio.backend.types.Role;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * Verifies the grant-based download bypass in {@link ContentDownloadControllerProd}: a principal
 * holding a {@code can_download=true} grant sees 200 without a cookie; {@code can_download=false}
 * sees 401; and anonymous paths still consult the cookie gate unchanged.
 */
@ExtendWith(MockitoExtension.class)
class ContentDownloadAuthTest {

  private static final String BUCKET = "test-bucket";

  @Mock private S3Client s3Client;
  @Mock private CollectionService collectionService;
  @Mock private ContentService contentService;
  @Mock private ClientGalleryAuthService clientGalleryAuthService;
  @Mock private GalleryAccessService galleryAccessService;

  @InjectMocks private ContentDownloadControllerProd controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(controller, "bucketName", BUCKET);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate(Long userId) {
    var principal = new AuthPrincipal(userId, "c@example.com", Role.CLIENT, true);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }

  private static ResponseInputStream<GetObjectResponse> fakeStream() {
    GetObjectResponse meta = GetObjectResponse.builder().contentLength(4L).build();
    return new ResponseInputStream<>(
        meta, AbortableInputStream.create(new ByteArrayInputStream(new byte[] {1, 2, 3, 4})));
  }

  private static DownloadResolution webResolution(String filename) {
    return new DownloadResolution("Image/Web/2025/01/" + filename, ".webp", "image/webp", filename);
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

  // ---------------------------------------------------------------------------
  //  Image download — grant bypass
  // ---------------------------------------------------------------------------

  @Nested
  class ImageDownloadGrantBypass {

    @Test
    void grantedUser_withCanDownload_gets200_withoutCookie() throws Exception {
      authenticate(7L);
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.of(protectedGallery()));
      when(galleryAccessService.hasDownloadGrant(7L, 1L)).thenReturn(true);
      when(contentService.resolveImageDownload(10L, "web")).thenReturn(webResolution("img.webp"));
      when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(fakeStream());

      mockMvc.perform(get("/api/read/content/images/10/download")).andExpect(status().isOk());

      verify(clientGalleryAuthService, never()).validateAccessToken(any(), any());
    }

    @Test
    void grantedUser_withCanDownloadFalse_gets401() throws Exception {
      authenticate(7L);
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.of(protectedGallery()));
      when(galleryAccessService.hasDownloadGrant(7L, 1L)).thenReturn(false);
      // No cookie present; cookie gate returns false by default, yielding 401.

      mockMvc
          .perform(get("/api/read/content/images/10/download"))
          .andExpect(status().isUnauthorized());

      verify(contentService, never()).resolveImageDownload(any(), any());
    }

    @Test
    void anonymous_noGrant_noCookie_gets401() throws Exception {
      // No authentication set; no cookie present — cookie gate default returns false.
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.of(protectedGallery()));

      mockMvc
          .perform(get("/api/read/content/images/10/download"))
          .andExpect(status().isUnauthorized());

      verify(galleryAccessService, never()).hasDownloadGrant(any(), any());
    }

    @Test
    void anonymous_validCookie_gets200() throws Exception {
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.of(protectedGallery()));
      when(clientGalleryAuthService.validateAccessToken("smith-wedding", "tok")).thenReturn(true);
      when(contentService.resolveImageDownload(10L, "web")).thenReturn(webResolution("img.webp"));
      when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(fakeStream());

      mockMvc
          .perform(
              get("/api/read/content/images/10/download")
                  .cookie(new jakarta.servlet.http.Cookie("gallery_access_smith-wedding", "tok")))
          .andExpect(status().isOk());

      verify(galleryAccessService, never()).hasDownloadGrant(any(), any());
    }
  }

  // ---------------------------------------------------------------------------
  //  Collection ZIP download — grant bypass
  // ---------------------------------------------------------------------------

  @Nested
  class CollectionDownloadGrantBypass {

    @Test
    void grantedUser_withCanDownload_gets200_withoutCookie() throws Exception {
      authenticate(7L);
      when(collectionService.findEntityBySlug("smith-wedding")).thenReturn(protectedGallery());
      when(galleryAccessService.hasDownloadGrant(7L, 1L)).thenReturn(true);
      when(contentService.resolveCollectionDownloadEntries(1L, "web", null))
          .thenReturn(List.of(webResolution("img.webp")));
      when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(fakeStream());

      mockMvc
          .perform(get("/api/read/collections/smith-wedding/download"))
          .andExpect(status().isOk());

      verify(clientGalleryAuthService, never()).validateAccessToken(any(), any());
    }

    @Test
    void grantedUser_withCanDownloadFalse_gets401() throws Exception {
      authenticate(7L);
      when(collectionService.findEntityBySlug("smith-wedding")).thenReturn(protectedGallery());
      when(galleryAccessService.hasDownloadGrant(7L, 1L)).thenReturn(false);
      // No cookie present; cookie gate returns false by default, yielding 401.

      mockMvc
          .perform(get("/api/read/collections/smith-wedding/download"))
          .andExpect(status().isUnauthorized());

      verify(contentService, never()).resolveCollectionDownloadEntries(any(), any(), any());
    }

    @Test
    void anonymous_noGrant_noCookie_gets401() throws Exception {
      // No authentication set; no cookie present — cookie gate default returns false.
      when(collectionService.findEntityBySlug("smith-wedding")).thenReturn(protectedGallery());

      mockMvc
          .perform(get("/api/read/collections/smith-wedding/download"))
          .andExpect(status().isUnauthorized());

      verify(galleryAccessService, never()).hasDownloadGrant(any(), any());
    }

    @Test
    void anonymous_validCookie_gets200() throws Exception {
      when(collectionService.findEntityBySlug("smith-wedding")).thenReturn(protectedGallery());
      when(clientGalleryAuthService.validateAccessToken("smith-wedding", "tok")).thenReturn(true);
      when(contentService.resolveCollectionDownloadEntries(1L, "web", null))
          .thenReturn(List.of(webResolution("img.webp")));
      when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(fakeStream());

      mockMvc
          .perform(
              get("/api/read/collections/smith-wedding/download")
                  .cookie(new jakarta.servlet.http.Cookie("gallery_access_smith-wedding", "tok")))
          .andExpect(status().isOk());

      verify(galleryAccessService, never()).hasDownloadGrant(any(), any());
    }
  }
}
