package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.services.ClientGalleryAuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GalleryAccessCookiesTest {

  @Test
  void normalizeSlug_lowercases() {
    assertThat(GalleryAccessCookies.normalizeSlug("MyGallery")).isEqualTo("mygallery");
  }

  @Test
  void normalizeSlug_replacesUnsafeChars() {
    assertThat(GalleryAccessCookies.normalizeSlug("foo bar/baz;qux")).isEqualTo("foo_bar_baz_qux");
  }

  @Test
  void normalizeSlug_handlesNullAndBlank() {
    assertThat(GalleryAccessCookies.normalizeSlug(null)).isEmpty();
    assertThat(GalleryAccessCookies.normalizeSlug("   ")).isEmpty();
  }

  @Test
  void cookieName_appliesPrefix() {
    assertThat(GalleryAccessCookies.cookieName("smith-wedding"))
        .isEqualTo("gallery_access_smith-wedding");
  }

  @Test
  void cookieName_isStableAcrossCase() {
    // The whole point: 'MyGallery' and 'mygallery' yield the same cookie name so the
    // limiter and the cookie reader stay in lockstep on mixed-case slugs.
    assertThat(GalleryAccessCookies.cookieName("MyGallery"))
        .isEqualTo(GalleryAccessCookies.cookieName("mygallery"));
  }

  @Test
  void readCookie_returnsNullWhenAbsent() {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getCookies()).thenReturn(null);
    assertThat(GalleryAccessCookies.readCookie(request, "gallery_access_foo")).isNull();
  }

  @Test
  void readCookie_findsByName() {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getCookies())
        .thenReturn(
            new Cookie[] {
              new Cookie("other", "ignored"),
              new Cookie("gallery_access_foo", "tokenA"),
              new Cookie("gallery_access_bar", "tokenB"),
            });
    assertThat(GalleryAccessCookies.readCookie(request, "gallery_access_foo")).isEqualTo("tokenA");
  }

  @Test
  void hasValidAccess_returnsTrue_whenCookiePresentAndTokenValid() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    Cookie cookie = new Cookie("gallery_access_my-gallery", "valid-token");
    when(request.getCookies()).thenReturn(new Cookie[] {cookie});
    ClientGalleryAuthService auth = mock(ClientGalleryAuthService.class);
    when(auth.validateAccessToken("my-gallery", "valid-token")).thenReturn(true);

    assertTrue(GalleryAccessCookies.hasValidAccess(request, "my-gallery", auth));
  }

  @Test
  void hasValidAccess_returnsFalse_whenNoCookies() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getCookies()).thenReturn(null);
    ClientGalleryAuthService auth = mock(ClientGalleryAuthService.class);
    when(auth.validateAccessToken(anyString(), isNull())).thenReturn(false);

    assertFalse(GalleryAccessCookies.hasValidAccess(request, "my-gallery", auth));
  }

  @Test
  void hasValidAccess_returnsFalse_whenTokenInvalid() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    Cookie cookie = new Cookie("gallery_access_my-gallery", "tampered");
    when(request.getCookies()).thenReturn(new Cookie[] {cookie});
    ClientGalleryAuthService auth = mock(ClientGalleryAuthService.class);
    when(auth.validateAccessToken("my-gallery", "tampered")).thenReturn(false);

    assertFalse(GalleryAccessCookies.hasValidAccess(request, "my-gallery", auth));
  }

  @Test
  void passwordCookieName_appliesPrefix() {
    assertThat(GalleryAccessCookies.passwordCookieName("abc123_-"))
        .isEqualTo("gallery_access_pw_abc123_-");
  }

  @Test
  void passwordCookieName_sanitizesUnsafeChars() {
    assertThat(GalleryAccessCookies.passwordCookieName("abc/=+xyz"))
        .isEqualTo("gallery_access_pw_abc___xyz");
  }

  @Test
  void passwordCookieName_handlesNullAndBlank() {
    assertThat(GalleryAccessCookies.passwordCookieName(null)).isEqualTo("gallery_access_pw_");
    assertThat(GalleryAccessCookies.passwordCookieName("")).isEqualTo("gallery_access_pw_");
  }

  @Test
  void passwordAwareHasValidAccess_returnsTrue_forUnprotectedGallery() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getCookies()).thenReturn(null);
    ClientGalleryAuthService auth = mock(ClientGalleryAuthService.class);

    assertTrue(GalleryAccessCookies.hasValidAccess(request, "any-slug", null, auth));
    assertTrue(GalleryAccessCookies.hasValidAccess(request, "any-slug", "", auth));
  }

  @Test
  void passwordAwareHasValidAccess_acceptsSlugCookie() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    Cookie slugCookie = new Cookie("gallery_access_my-gallery", "slug-token");
    when(request.getCookies()).thenReturn(new Cookie[] {slugCookie});
    ClientGalleryAuthService auth = mock(ClientGalleryAuthService.class);
    when(auth.validateAccessToken("my-gallery", "slug-token")).thenReturn(true);

    assertTrue(GalleryAccessCookies.hasValidAccess(request, "my-gallery", "secret", auth));
  }

  @Test
  void passwordAwareHasValidAccess_acceptsFingerprintCookie() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    Cookie fpCookie = new Cookie("gallery_access_pw_FINGER", "group-token");
    when(request.getCookies()).thenReturn(new Cookie[] {fpCookie});
    ClientGalleryAuthService auth = mock(ClientGalleryAuthService.class);
    when(auth.validateAccessToken("sibling-gallery", null)).thenReturn(false);
    when(auth.passwordFingerprint("shared-pw")).thenReturn("FINGER");
    when(auth.validatePasswordAccessToken("shared-pw", "group-token")).thenReturn(true);

    assertTrue(GalleryAccessCookies.hasValidAccess(request, "sibling-gallery", "shared-pw", auth));
  }

  @Test
  void passwordAwareHasValidAccess_rejectsWhenBothCookiesInvalid() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    Cookie fpCookie = new Cookie("gallery_access_pw_FINGER", "stale-token");
    when(request.getCookies()).thenReturn(new Cookie[] {fpCookie});
    ClientGalleryAuthService auth = mock(ClientGalleryAuthService.class);
    when(auth.validateAccessToken("sibling-gallery", null)).thenReturn(false);
    when(auth.passwordFingerprint("shared-pw")).thenReturn("FINGER");
    when(auth.validatePasswordAccessToken("shared-pw", "stale-token")).thenReturn(false);

    assertFalse(GalleryAccessCookies.hasValidAccess(request, "sibling-gallery", "shared-pw", auth));
  }

  @Test
  void passwordAwareHasValidAccess_rejectsWhenFingerprintMismatch() {
    // The user has a valid group cookie for password-A, but the gallery they're requesting
    // has password-B. The fingerprint cookie name doesn't match → effectively no group cookie.
    HttpServletRequest request = mock(HttpServletRequest.class);
    Cookie fpCookie = new Cookie("gallery_access_pw_FINGER_A", "good-token-for-A");
    when(request.getCookies()).thenReturn(new Cookie[] {fpCookie});
    ClientGalleryAuthService auth = mock(ClientGalleryAuthService.class);
    when(auth.validateAccessToken("solo-gallery", null)).thenReturn(false);
    when(auth.passwordFingerprint("password-B")).thenReturn("FINGER_B");
    when(auth.validatePasswordAccessToken("password-B", null)).thenReturn(false);

    assertFalse(GalleryAccessCookies.hasValidAccess(request, "solo-gallery", "password-B", auth));
  }
}
