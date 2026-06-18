package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthLoginLimiterTest {

  @Test
  void fiveFailuresAllowedSixthIsBlocked() {
    AuthLoginLimiter limiter = new AuthLoginLimiter(5, 15);
    for (int i = 0; i < 5; i++) {
      assertThat(limiter.isBlocked("203.0.113.1", "a@example.com")).isFalse();
      limiter.recordFailure("203.0.113.1", "a@example.com");
    }
    assertThat(limiter.isBlocked("203.0.113.1", "a@example.com")).isTrue();
  }

  @Test
  void resetClearsTheCounter() {
    AuthLoginLimiter limiter = new AuthLoginLimiter(2, 15);
    limiter.recordFailure("203.0.113.1", "b@example.com");
    limiter.recordFailure("203.0.113.1", "b@example.com");
    assertThat(limiter.isBlocked("203.0.113.1", "b@example.com")).isTrue();

    limiter.reset("203.0.113.1", "b@example.com");
    assertThat(limiter.isBlocked("203.0.113.1", "b@example.com")).isFalse();
  }

  @Test
  void keyIsCaseInsensitiveOnEmail() {
    AuthLoginLimiter limiter = new AuthLoginLimiter(2, 15);
    limiter.recordFailure("203.0.113.1", "Case@Example.com");
    limiter.recordFailure("203.0.113.1", "case@example.com");
    // Two failures against the same normalized key -> blocked.
    assertThat(limiter.isBlocked("203.0.113.1", "CASE@EXAMPLE.COM")).isTrue();
  }

  @Test
  void differentIpsAndEmailsHaveIndependentCounters() {
    AuthLoginLimiter limiter = new AuthLoginLimiter(1, 15);
    limiter.recordFailure("203.0.113.1", "x@example.com");
    assertThat(limiter.isBlocked("203.0.113.1", "x@example.com")).isTrue();
    assertThat(limiter.isBlocked("203.0.113.2", "x@example.com")).isFalse();
    assertThat(limiter.isBlocked("203.0.113.1", "y@example.com")).isFalse();
  }

  @Test
  void nullIpOrEmailNeverBlocks() {
    AuthLoginLimiter limiter = new AuthLoginLimiter(1, 15);
    assertThat(limiter.isBlocked(null, "z@example.com")).isFalse();
    assertThat(limiter.isBlocked("203.0.113.1", null)).isFalse();
    limiter.recordFailure(null, null);
    assertThat(limiter.isBlocked(null, null)).isFalse();
  }
}
