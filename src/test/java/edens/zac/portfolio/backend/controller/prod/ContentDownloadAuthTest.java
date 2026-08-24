package edens.zac.portfolio.backend.controller.prod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.DownloadResolution;
import edens.zac.portfolio.backend.services.ClientGalleryAuthService;
import edens.zac.portfolio.backend.services.CollectionAccessService;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.ContentService;
import edens.zac.portfolio.backend.services.DownloadUrlService;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Verifies the two non-per-slug-cookie ways a download is authorized in {@link
 * ContentDownloadControllerProd}.
 *
 * <ul>
 *   <li>CLIENT membership: a principal holding a CLIENT membership is authorized without a cookie
 *       (302 redirect to the presigned URL); no CLIENT membership yields 401; and anonymous paths
 *       still consult the cookie gate unchanged.
 *   <li>Shared password-fingerprint cookie: a viewer who unlocked a PARENT gallery downloads from a
 *       propagated CLIENT_GALLERY child without re-prompting, because both galleries share the
 *       password and therefore the {@code gallery_access_pw_<fingerprint>} cookie.
 * </ul>
 *
 * <p>Auth semantics only — the redirect target is produced by {@link DownloadUrlService}, stubbed
 * here.
 */
@ExtendWith(MockitoExtension.class)
class ContentDownloadAuthTest {

  private static final URI PRESIGNED = URI.create("https://bucket.s3.amazonaws.com/obj?sig=abc");

  @Mock private CollectionService collectionService;
  @Mock private ContentService contentService;
  @Mock private ClientGalleryAuthService clientGalleryAuthService;
  @Mock private CollectionAccessService collectionAccessService;
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

  /** The principal {@link #authenticate} installs, for stubbing the grant check against. */
  private static final AuthPrincipal MEMBER = new AuthPrincipal(7L, "c@example.com", false, true);

  private void authenticateAdmin(Long userId) {
    var principal = new AuthPrincipal(userId, "admin@example.com", true, true);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
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
        .title("Smith Wedding")
        .slug("smith-wedding")
        .visibility(CollectionVisibility.UNLISTED)
        .galleryPassword("sunshine")
        .build();
  }

  private static CollectionEntity openCollection() {
    return CollectionEntity.builder()
        .id(2L)
        .title("Open Portfolio")
        .slug("open-portfolio")
        .visibility(CollectionVisibility.LISTED)
        .galleryPassword(null)
        .build();
  }

  /**
   * A CLIENT_GALLERY child that inherited the parent's password by propagation. Same password as
   * {@link #protectedGallery()}, different slug — so it shares the fingerprint cookie but has no
   * per-slug cookie of its own.
   */
  private static CollectionEntity propagatedChildGallery() {
    return CollectionEntity.builder()
        .id(5L)
        .title("Smith Wedding — Prints")
        .slug("smith-wedding-prints")
        .visibility(CollectionVisibility.UNLISTED)
        .isClient(true)
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
      when(contentService.findProtectedCollectionsForImage(10L))
          .thenReturn(List.of(protectedGallery()));
      when(collectionAccessService.isClient(MEMBER, 1L)).thenReturn(true);
      when(contentService.resolveImageDownload(10L, "web")).thenReturn(webResolution("img.webp"));
      when(downloadUrlService.presignObject(any(), any(), any())).thenReturn(PRESIGNED);

      mockMvc.perform(get("/api/read/content/images/10/download")).andExpect(status().isFound());

      verify(clientGalleryAuthService, never()).validateAccessToken(any(), any());
    }

    @Test
    void adminPrincipal_redirects_withNoGrantAndNoCookie() throws Exception {
      // S-6 / working rule 20. This gate read CurrentUser.userId(), so isAdmin never reached the
      // CLIENT check and an admin was 401'd on a download from a gallery the read gate let them
      // into. The whole principal must arrive here.
      authenticateAdmin(1L);
      when(contentService.findProtectedCollectionsForImage(10L))
          .thenReturn(List.of(protectedGallery()));
      when(collectionAccessService.isClient(any(AuthPrincipal.class), eq(1L))).thenReturn(true);
      when(contentService.resolveImageDownload(10L, "web")).thenReturn(webResolution("img.webp"));
      when(downloadUrlService.presignObject(any(), any(), any())).thenReturn(PRESIGNED);

      mockMvc.perform(get("/api/read/content/images/10/download")).andExpect(status().isFound());

      ArgumentCaptor<AuthPrincipal> captor = ArgumentCaptor.forClass(AuthPrincipal.class);
      verify(collectionAccessService).isClient(captor.capture(), eq(1L));
      assertThat(captor.getValue().isAdmin()).isTrue();
      verify(clientGalleryAuthService, never()).validateAccessToken(any(), any());
    }

    @Test
    void nonClientMember_gets401() throws Exception {
      authenticate(7L);
      when(contentService.findProtectedCollectionsForImage(10L))
          .thenReturn(List.of(protectedGallery()));
      when(collectionAccessService.isClient(MEMBER, 1L)).thenReturn(false);

      mockMvc
          .perform(get("/api/read/content/images/10/download"))
          .andExpect(status().isUnauthorized());

      verify(contentService, never()).resolveImageDownload(any(), any());
    }

    @Test
    void anonymous_noMembership_noCookie_gets401() throws Exception {
      when(contentService.findProtectedCollectionsForImage(10L))
          .thenReturn(List.of(protectedGallery()));

      mockMvc
          .perform(get("/api/read/content/images/10/download"))
          .andExpect(status().isUnauthorized());

      verify(collectionAccessService, never()).isClient(any(), any());
    }

    @Test
    void anonymous_validCookie_redirects() throws Exception {
      when(contentService.findProtectedCollectionsForImage(10L))
          .thenReturn(List.of(protectedGallery()));
      when(clientGalleryAuthService.validateAccessToken("smith-wedding", "tok")).thenReturn(true);
      when(contentService.resolveImageDownload(10L, "web")).thenReturn(webResolution("img.webp"));
      when(downloadUrlService.presignObject(any(), any(), any())).thenReturn(PRESIGNED);

      mockMvc
          .perform(
              get("/api/read/content/images/10/download")
                  .cookie(new jakarta.servlet.http.Cookie("gallery_access_smith-wedding", "tok")))
          .andExpect(status().isFound());

      verify(collectionAccessService, never()).isClient(any(), any());
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
      when(collectionAccessService.isClient(MEMBER, 1L)).thenReturn(true);
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
      when(collectionAccessService.isClient(MEMBER, 1L)).thenReturn(false);

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

      verify(collectionAccessService, never()).isClient(any(), any());
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

      verify(collectionAccessService, never()).isClient(any(), any());
    }

    @Test
    void clientOfTheForeignGallery_redirects_throughAPublicWrapper() throws Exception {
      // The requested slug is an unprotected wrapper, so its own password gate is a no-op and the
      // CLIENT grant is consumed by the SECOND gate instead: the protected gallery that also holds
      // the images this download would serve. Fail-closed must not mean fail-always -- a grant that
      // satisfies the gating gallery still gets the download through the wrapper.
      authenticate(7L);
      when(collectionService.findEntityBySlug("open-portfolio")).thenReturn(openCollection());
      when(contentService.findProtectedCollectionsForCollectionDownload(2L, null))
          .thenReturn(List.of(protectedGallery()));
      when(collectionAccessService.isClient(MEMBER, 1L)).thenReturn(true);
      when(contentService.resolveCollectionDownloadEntries(2L, "web", null))
          .thenReturn(List.of(webResolution("img.webp")));
      when(downloadUrlService.presignObject(any(), any(), any())).thenReturn(PRESIGNED);

      mockMvc
          .perform(get("/api/read/collections/open-portfolio/download"))
          .andExpect(status().isFound());

      verify(clientGalleryAuthService, never()).validateAccessToken(any(), any());
    }

    @Test
    void nonClientOfTheForeignGallery_gets401_throughAPublicWrapper() throws Exception {
      // Same wrapper, no CLIENT grant on the gating gallery: the second gate refuses. The gate runs
      // before resolution, so nothing is even resolved for an unauthorized caller.
      authenticate(7L);
      when(collectionService.findEntityBySlug("open-portfolio")).thenReturn(openCollection());
      when(contentService.findProtectedCollectionsForCollectionDownload(2L, null))
          .thenReturn(List.of(protectedGallery()));
      when(collectionAccessService.isClient(MEMBER, 1L)).thenReturn(false);

      mockMvc
          .perform(get("/api/read/collections/open-portfolio/download"))
          .andExpect(status().isUnauthorized());

      verify(contentService, never()).resolveCollectionDownloadEntries(any(), any(), any());
      verify(downloadUrlService, never()).presignObject(any(), any(), any());
      verify(downloadUrlService, never()).zipToS3AndPresign(any(), any());
    }
  }

  // ---------------------------------------------------------------------------
  //  Shared password-fingerprint cookie (PARENT unlock -> propagated CHILD download)
  // ---------------------------------------------------------------------------

  /**
   * The paying-client delivery path. Unlocking the PARENT issues two cookies: {@code
   * gallery_access_<parent-slug>} and the shared {@code gallery_access_pw_<fingerprint>}. Only the
   * second one is meaningful for a propagated child, whose slug differs. The download gate used the
   * per-slug-only overload while the read gate used the fingerprint-aware one, so the child
   * rendered with an ENABLED download button that 401'd.
   */
  @Nested
  class SharedPasswordUnlock {

    private static final String FINGERPRINT = "fp-sunshine";
    private static final String PW_COOKIE = "gallery_access_pw_" + FINGERPRINT;

    @Test
    void imageDownload_fingerprintCookieFromTheParent_authorizesTheChild() throws Exception {
      when(contentService.findProtectedCollectionsForImage(10L))
          .thenReturn(List.of(propagatedChildGallery()));
      when(clientGalleryAuthService.passwordFingerprint("sunshine")).thenReturn(FINGERPRINT);
      when(clientGalleryAuthService.validatePasswordAccessToken("sunshine", "pw-tok"))
          .thenReturn(true);
      when(contentService.resolveImageDownload(10L, "web")).thenReturn(webResolution("img.webp"));
      when(downloadUrlService.presignObject(any(), any(), any())).thenReturn(PRESIGNED);

      mockMvc
          .perform(
              get("/api/read/content/images/10/download")
                  .cookie(new jakarta.servlet.http.Cookie(PW_COOKIE, "pw-tok")))
          .andExpect(status().isFound());
    }

    @Test
    void collectionDownload_fingerprintCookieFromTheParent_authorizesTheChild() throws Exception {
      when(collectionService.findEntityBySlug("smith-wedding-prints"))
          .thenReturn(propagatedChildGallery());
      when(clientGalleryAuthService.passwordFingerprint("sunshine")).thenReturn(FINGERPRINT);
      when(clientGalleryAuthService.validatePasswordAccessToken("sunshine", "pw-tok"))
          .thenReturn(true);
      when(contentService.resolveCollectionDownloadEntries(5L, "web", null))
          .thenReturn(List.of(webResolution("img.webp")));
      when(downloadUrlService.presignObject(any(), any(), any())).thenReturn(PRESIGNED);

      mockMvc
          .perform(
              get("/api/read/collections/smith-wedding-prints/download")
                  .cookie(new jakarta.servlet.http.Cookie(PW_COOKIE, "pw-tok")))
          .andExpect(status().isFound());
    }

    @Test
    void collectionDownload_fingerprintCookieForADifferentPassword_gets401() throws Exception {
      // The cookie NAME carries the fingerprint, so a cookie minted by an unrelated password group
      // is simply not read for this gallery: no per-slug cookie, no matching fingerprint, 401.
      when(collectionService.findEntityBySlug("smith-wedding-prints"))
          .thenReturn(propagatedChildGallery());
      when(clientGalleryAuthService.passwordFingerprint("sunshine")).thenReturn(FINGERPRINT);

      mockMvc
          .perform(
              get("/api/read/collections/smith-wedding-prints/download")
                  .cookie(
                      new jakarta.servlet.http.Cookie("gallery_access_pw_fp-moonlight", "pw-tok")))
          .andExpect(status().isUnauthorized());

      verify(contentService, never()).resolveCollectionDownloadEntries(any(), any(), any());
      verify(downloadUrlService, never()).presignObject(any(), any(), any());
      verify(downloadUrlService, never()).zipToS3AndPresign(any(), any());
    }

    @Test
    void imageDownload_fingerprintCookie_stillFailsClosedOnAForeignGallery() throws Exception {
      // Widening the gate to fingerprint cookies must not weaken "EVERY protected parent". The
      // image also lives in jones-wedding, whose different password produces a different
      // fingerprint, so the sunshine cookie authorizes one parent and not the other.
      CollectionEntity foreignGallery =
          CollectionEntity.builder()
              .id(3L)
              .title("Jones Wedding")
              .slug("jones-wedding")
              .visibility(CollectionVisibility.UNLISTED)
              .galleryPassword("moonlight")
              .build();
      when(contentService.findProtectedCollectionsForImage(10L))
          .thenReturn(List.of(propagatedChildGallery(), foreignGallery));
      when(clientGalleryAuthService.passwordFingerprint("sunshine")).thenReturn(FINGERPRINT);
      when(clientGalleryAuthService.validatePasswordAccessToken("sunshine", "pw-tok"))
          .thenReturn(true);
      when(clientGalleryAuthService.passwordFingerprint("moonlight")).thenReturn("fp-moonlight");

      mockMvc
          .perform(
              get("/api/read/content/images/10/download")
                  .cookie(new jakarta.servlet.http.Cookie(PW_COOKIE, "pw-tok")))
          .andExpect(status().isUnauthorized());

      verify(contentService, never()).resolveImageDownload(any(), any());
      verify(downloadUrlService, never()).presignObject(any(), any(), any());
    }
  }
}
