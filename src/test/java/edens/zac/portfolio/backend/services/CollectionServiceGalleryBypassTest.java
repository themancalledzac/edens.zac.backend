package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.CollectionSiblingRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.types.Role;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests verifying that a logged-in principal with a gallery_access grant bypasses the cookie
 * gate in {@link CollectionService#isGalleryAccessAuthorized}, and that anonymous and shared-code
 * (fingerprint cookie) paths are unchanged.
 */
@ExtendWith(MockitoExtension.class)
class CollectionServiceGalleryBypassTest {

  @Mock private CollectionRepository collectionRepository;

  @Mock
  private edens.zac.portfolio.backend.dao.CollectionPeopleRepository collectionPeopleRepository;

  @Mock private CollectionSiblingRepository collectionSiblingRepository;
  @Mock private ContentRepository contentRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private TagRepository tagRepository;
  @Mock private ContentMutationUtil contentMutationUtil;
  @Mock private ContentModelConverter contentModelConverter;
  @Mock private CollectionProcessingUtil collectionProcessingUtil;
  @Mock private MetadataService metadataService;
  @Mock private EmailService emailService;
  @Mock private SyntheticCollectionResolver syntheticResolver;
  @Mock private ClientGalleryAuthService clientGalleryAuthService;
  @Mock private GalleryAccessService galleryAccessService;
  @Mock private org.springframework.core.env.Environment springEnv;

  @InjectMocks private CollectionService service;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate(Long userId) {
    var principal = new AuthPrincipal(userId, "c@example.com", Role.CLIENT, true);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }

  private CollectionEntity protectedCollection(Long id, String slug) {
    return CollectionEntity.builder().id(id).slug(slug).galleryPassword("secret").build();
  }

  // ---------------------------------------------------------------------------
  //  Grant bypass
  // ---------------------------------------------------------------------------

  @Test
  void grantedPrincipal_bypasses_passwordGate() {
    CollectionEntity entity = protectedCollection(42L, "g");
    when(collectionRepository.findBySlug("g")).thenReturn(Optional.of(entity));
    authenticate(7L);
    when(galleryAccessService.hasGrant(7L, 42L)).thenReturn(true);

    HttpServletRequest request = new MockHttpServletRequest();
    assertThat(service.isGalleryAccessAuthorized("g", request)).isTrue();

    // Cookie path must not be consulted once the grant short-circuits.
    verify(clientGalleryAuthService, never())
        .validateAccessToken(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void loggedIn_noGrant_falls_through_to_cookieGate_andDenies() {
    CollectionEntity entity = protectedCollection(42L, "g");
    when(collectionRepository.findBySlug("g")).thenReturn(Optional.of(entity));
    authenticate(7L);
    when(galleryAccessService.hasGrant(7L, 42L)).thenReturn(false);
    // No cookie present — cookie gate returns false.
    when(clientGalleryAuthService.validateAccessToken(
            org.mockito.ArgumentMatchers.eq("g"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(false);
    when(clientGalleryAuthService.passwordFingerprint("secret")).thenReturn("FP");
    when(clientGalleryAuthService.validatePasswordAccessToken(
            org.mockito.ArgumentMatchers.eq("secret"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(false);

    HttpServletRequest request = new MockHttpServletRequest();
    assertThat(service.isGalleryAccessAuthorized("g", request)).isFalse();
  }

  @Test
  void expiredGrant_returnsNotGranted_andFallsThroughToCookieGate() {
    CollectionEntity entity = protectedCollection(42L, "g");
    when(collectionRepository.findBySlug("g")).thenReturn(Optional.of(entity));
    authenticate(7L);
    // hasGrant performs expiry logic internally; returning false simulates an expired grant.
    when(galleryAccessService.hasGrant(7L, 42L)).thenReturn(false);
    when(clientGalleryAuthService.validateAccessToken(
            org.mockito.ArgumentMatchers.eq("g"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(false);
    when(clientGalleryAuthService.passwordFingerprint("secret")).thenReturn("FP");
    when(clientGalleryAuthService.validatePasswordAccessToken(
            org.mockito.ArgumentMatchers.eq("secret"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(false);

    assertThat(service.isGalleryAccessAuthorized("g", new MockHttpServletRequest())).isFalse();
  }

  // ---------------------------------------------------------------------------
  //  Anonymous paths unchanged
  // ---------------------------------------------------------------------------

  @Test
  void anonymous_noGrant_fallsThrough_toCookieGate_andDenies() {
    // No authentication set — SecurityContextHolder has anonymous auth.
    CollectionEntity entity = protectedCollection(42L, "g");
    when(collectionRepository.findBySlug("g")).thenReturn(Optional.of(entity));
    when(clientGalleryAuthService.validateAccessToken(
            org.mockito.ArgumentMatchers.eq("g"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(false);
    when(clientGalleryAuthService.passwordFingerprint("secret")).thenReturn("FP");
    when(clientGalleryAuthService.validatePasswordAccessToken(
            org.mockito.ArgumentMatchers.eq("secret"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(false);

    assertThat(service.isGalleryAccessAuthorized("g", new MockHttpServletRequest())).isFalse();
    // galleryAccessService must not be consulted for anonymous callers.
    verify(galleryAccessService, never())
        .hasGrant(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void anonymous_validCookie_returnsTrue() {
    CollectionEntity entity = protectedCollection(42L, "g");
    when(collectionRepository.findBySlug("g")).thenReturn(Optional.of(entity));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new jakarta.servlet.http.Cookie("gallery_access_g", "valid-token"));
    when(clientGalleryAuthService.validateAccessToken("g", "valid-token")).thenReturn(true);

    assertThat(service.isGalleryAccessAuthorized("g", request)).isTrue();
    verify(galleryAccessService, never())
        .hasGrant(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void missingCollection_returnsTrue_regardlessOfAuth() {
    when(collectionRepository.findBySlug("missing")).thenReturn(Optional.empty());
    authenticate(7L);

    assertThat(service.isGalleryAccessAuthorized("missing", new MockHttpServletRequest())).isTrue();
    verify(galleryAccessService, never())
        .hasGrant(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }
}
