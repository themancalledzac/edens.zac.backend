package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ClientGalleryAccessLimiterTest {

  @Test
  void firstFiveAttemptsSucceedSixthFails() {
    ClientGalleryAccessLimiter limiter = new ClientGalleryAccessLimiter(5, 15);
    for (int i = 0; i < 5; i++) {
      assertThat(limiter.allow("203.0.113.1", "client-gallery")).isTrue();
    }
    assertThat(limiter.allow("203.0.113.1", "client-gallery")).isFalse();
  }

  @Test
  void differentIpsHaveIndependentBuckets() {
    ClientGalleryAccessLimiter limiter = new ClientGalleryAccessLimiter(2, 15);
    assertThat(limiter.allow("203.0.113.1", "gallery")).isTrue();
    assertThat(limiter.allow("203.0.113.1", "gallery")).isTrue();
    assertThat(limiter.allow("203.0.113.1", "gallery")).isFalse();

    // Different IP, same slug — fresh budget
    assertThat(limiter.allow("198.51.100.4", "gallery")).isTrue();
    assertThat(limiter.allow("198.51.100.4", "gallery")).isTrue();
    assertThat(limiter.allow("198.51.100.4", "gallery")).isFalse();
  }

  @Test
  void differentSlugsHaveIndependentBuckets() {
    ClientGalleryAccessLimiter limiter = new ClientGalleryAccessLimiter(2, 15);
    assertThat(limiter.allow("203.0.113.1", "gallery-a")).isTrue();
    assertThat(limiter.allow("203.0.113.1", "gallery-a")).isTrue();
    assertThat(limiter.allow("203.0.113.1", "gallery-a")).isFalse();

    // Same IP, different slug — fresh budget
    assertThat(limiter.allow("203.0.113.1", "gallery-b")).isTrue();
    assertThat(limiter.allow("203.0.113.1", "gallery-b")).isTrue();
    assertThat(limiter.allow("203.0.113.1", "gallery-b")).isFalse();
  }

  @Test
  void slugCaseAndWhitespaceIsNormalized() {
    ClientGalleryAccessLimiter limiter = new ClientGalleryAccessLimiter(2, 15);
    assertThat(limiter.allow("203.0.113.1", "Gallery-Slug")).isTrue();
    assertThat(limiter.allow("203.0.113.1", "  gallery-slug ")).isTrue();
    // Same effective key — third attempt must fail.
    assertThat(limiter.allow("203.0.113.1", "GALLERY-SLUG")).isFalse();
  }

  @Test
  void limiterAndCookieKeyShareNormalization() {
    // BE-H1 invariant: the limiter key and the cookie name must derive from the same
    // normalizer, so brute-force protection cannot be bypassed by varying slug casing.
    ClientGalleryAccessLimiter limiter = new ClientGalleryAccessLimiter(2, 15);

    // Two attempts on "MyGallery" (mixed case) — bucket key uses normalized "mygallery".
    assertThat(limiter.allow("203.0.113.1", "MyGallery")).isTrue();
    assertThat(limiter.allow("203.0.113.1", "MyGallery")).isTrue();

    // The cookie that POST /access would set is named after the same normalized slug, so
    // a follow-up attempt at "mygallery" hits the same bucket and is blocked.
    assertThat(GalleryAccessCookies.cookieName("MyGallery"))
        .isEqualTo(GalleryAccessCookies.cookieName("mygallery"));
    assertThat(limiter.allow("203.0.113.1", "mygallery")).isFalse();
  }

  @Test
  void nullOrBlankPassesThrough() {
    ClientGalleryAccessLimiter limiter = new ClientGalleryAccessLimiter(1, 15);
    assertThat(limiter.allow(null, "gallery")).isTrue();
    assertThat(limiter.allow("", "gallery")).isTrue();
    assertThat(limiter.allow("   ", "gallery")).isTrue();
    assertThat(limiter.allow("203.0.113.1", null)).isTrue();
    assertThat(limiter.allow("203.0.113.1", "")).isTrue();
    assertThat(limiter.allow("203.0.113.1", "   ")).isTrue();
  }

  @Test
  void windowRefillRestoresBudget() throws InterruptedException {
    // Use the test-only millisecond-window constructor so we don't have to sleep for minutes.
    ClientGalleryAccessLimiter limiter = new ClientGalleryAccessLimiter(2, Duration.ofMillis(200));

    assertThat(limiter.allow("203.0.113.5", "gallery")).isTrue();
    assertThat(limiter.allow("203.0.113.5", "gallery")).isTrue();
    assertThat(limiter.allow("203.0.113.5", "gallery")).isFalse();

    // Wait past the refill interval; Bucket4j's refillIntervally restores full capacity.
    Thread.sleep(250L);

    assertThat(limiter.allow("203.0.113.5", "gallery")).isTrue();
    assertThat(limiter.allow("203.0.113.5", "gallery")).isTrue();
    assertThat(limiter.allow("203.0.113.5", "gallery")).isFalse();
  }
}
