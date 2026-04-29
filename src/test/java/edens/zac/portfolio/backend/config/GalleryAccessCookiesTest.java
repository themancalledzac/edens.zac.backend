package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

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
}
