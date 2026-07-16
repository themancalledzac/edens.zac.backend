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
import edens.zac.portfolio.backend.services.DownloadUrlService;
import edens.zac.portfolio.backend.services.UserCollectionService;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.net.URI;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Verifies the CLIENT-membership download bypass in {@link ContentDownloadControllerProd}: a
 * principal holding a CLIENT membership is authorized without a cookie (302 redirect to the
 * presigned URL); no CLIENT membership yields 401; and anonymous paths still consult the cookie
 * gate unchanged. Auth semantics only — the redirect target is produced by {@link
 * DownloadUrlService}, stubbed here.
 */
@ExtendWith(MockitoExtension.class)
class ContentDownloadAuthTest {

  private static final URI PRESIGNED = URI.create("https://bucket.s3.amazonaws.com/obj?sig=abc");

  @Mock private CollectionService collectionService;
  @Mock private ContentService contentService;
  @Mock private ClientGalleryAuthService clientGalleryAuthService;
  @Mock private UserCollectionService userCollectionService;
  @Mock private DownloadUrlService downloadUrlService;

  @InjectMocks private ContentDownloadControllerProd controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate(Long userId) {
    var principal = new AuthPrincipal(userId, "c@example.com", false, true);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
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
  //  Image download — CLIENT membership bypass
  // ---------------------------------------------------------------------------

  @Nested
  class ImageDownloadGrantBypass {

    @Test
    void clientMember_redirects_withoutCookie() throws Exception {
      authenticate(7L);
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.of(protectedGallery()));
      when(userCollectionService.isClient(7L, 1L)).thenReturn(true);
      when(contentService.resolveImageDownload(10L, "web")).thenReturn(webResolution("img.webp"));
      when(downloadUrlService.presignObject(any(), any(), any())).thenReturn(PRESIGNED);

      mockMvc.perform(get("/api/read/content/images/10/download")).andExpect(status().isFound());

      verify(clientGalleryAuthService, never()).validateAccessToken(any(), any());
    }

    @Test
    void nonClientMember_gets401() throws Exception {
      authenticate(7L);
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.of(protectedGallery()));
      when(userCollectionService.isClient(7L, 1L)).thenReturn(false);

      mockMvc
          .perform(get("/api/read/content/images/10/download"))
          .andExpect(status().isUnauthorized());

      verify(contentService, never()).resolveImageDownload(any(), any());
    }

    @Test
    void anonymous_noMembership_noCookie_gets401() throws Exception {
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.of(protectedGallery()));

      mockMvc
          .perform(get("/api/read/content/images/10/download"))
          .andExpect(status().isUnauthorized());

      verify(userCollectionService, never()).isClient(any(), any());
    }

    @Test
    void anonymous_validCookie_redirects() throws Exception {
      when(contentService.findCollectionForImage(10L)).thenReturn(Optional.of(protectedGallery()));
      when(clientGalleryAuthService.validateAccessToken("smith-wedding", "tok")).thenReturn(true);
      when(contentService.resolveImageDownload(10L, "web")).thenReturn(webResolution("img.webp"));
      when(downloadUrlService.presignObject(any(), any(), any())).thenReturn(PRESIGNED);

      mockMvc
          .perform(
              get("/api/read/content/images/10/download")
                  .cookie(new jakarta.servlet.http.Cookie("gallery_access_smith-wedding", "tok")))
          .andExpect(status().isFound());

      verify(userCollectionService, never()).isClient(any(), any());
    }
  }

  // ---------------------------------------------------------------------------
  //  Collection download — CLIENT membership bypass
  // ---------------------------------------------------------------------------

  @Nested
  class CollectionDownloadGrantBypass {

    @Test
    void clientMember_redirects_withoutCookie() throws Exception {
      authenticate(7L);
      when(collectionService.findEntityBySlug("smith-wedding")).thenReturn(protectedGallery());
      when(userCollectionService.isClient(7L, 1L)).thenReturn(true);
      when(contentService.resolveCollectionDownloadEntries(1L, "web", null))
          .thenReturn(List.of(webResolution("img.webp")));
      when(downloadUrlService.presignObject(any(), any(), any())).thenReturn(PRESIGNED);

      mockMvc
          .perform(get("/api/read/collections/smith-wedding/download"))
          .andExpect(status().isFound());

      verify(clientGalleryAuthService, never()).validateAccessToken(any(), any());
    }

    @Test
    void nonClientMember_gets401() throws Exception {
      authenticate(7L);
      when(collectionService.findEntityBySlug("smith-wedding")).thenReturn(protectedGallery());
      when(userCollectionService.isClient(7L, 1L)).thenReturn(false);

      mockMvc
          .perform(get("/api/read/collections/smith-wedding/download"))
          .andExpect(status().isUnauthorized());

      verify(contentService, never()).resolveCollectionDownloadEntries(any(), any(), any());
    }

    @Test
    void anonymous_noMembership_noCookie_gets401() throws Exception {
      when(collectionService.findEntityBySlug("smith-wedding")).thenReturn(protectedGallery());

      mockMvc
          .perform(get("/api/read/collections/smith-wedding/download"))
          .andExpect(status().isUnauthorized());

      verify(userCollectionService, never()).isClient(any(), any());
    }

    @Test
    void anonymous_validCookie_redirects() throws Exception {
      when(collectionService.findEntityBySlug("smith-wedding")).thenReturn(protectedGallery());
      when(clientGalleryAuthService.validateAccessToken("smith-wedding", "tok")).thenReturn(true);
      when(contentService.resolveCollectionDownloadEntries(1L, "web", null))
          .thenReturn(List.of(webResolution("img.webp")));
      when(downloadUrlService.presignObject(any(), any(), any())).thenReturn(PRESIGNED);

      mockMvc
          .perform(
              get("/api/read/collections/smith-wedding/download")
                  .cookie(new jakarta.servlet.http.Cookie("gallery_access_smith-wedding", "tok")))
          .andExpect(status().isFound());

      verify(userCollectionService, never()).isClient(any(), any());
    }
  }
}
