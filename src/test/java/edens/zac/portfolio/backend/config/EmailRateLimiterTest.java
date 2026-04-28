package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailRateLimiterTest {

  @Test
  void firstFiveAttemptsSucceedSixthFails() {
    EmailRateLimiter limiter = new EmailRateLimiter(5);
    for (int i = 0; i < 5; i++) {
      assertThat(limiter.tryConsume("user@example.com")).isTrue();
    }
    assertThat(limiter.tryConsume("user@example.com")).isFalse();
  }

  @Test
  void differentEmailsHaveIndependentBuckets() {
    EmailRateLimiter limiter = new EmailRateLimiter(2);
    assertThat(limiter.tryConsume("a@example.com")).isTrue();
    assertThat(limiter.tryConsume("a@example.com")).isTrue();
    assertThat(limiter.tryConsume("a@example.com")).isFalse();

    // b@example.com still has full budget
    assertThat(limiter.tryConsume("b@example.com")).isTrue();
    assertThat(limiter.tryConsume("b@example.com")).isTrue();
    assertThat(limiter.tryConsume("b@example.com")).isFalse();
  }

  @Test
  void caseAndWhitespaceIsNormalized() {
    EmailRateLimiter limiter = new EmailRateLimiter(2);
    assertThat(limiter.tryConsume("Foo@Example.com")).isTrue();
    assertThat(limiter.tryConsume("  foo@example.com ")).isTrue();
    // Same effective key — third attempt must fail.
    assertThat(limiter.tryConsume("FOO@EXAMPLE.COM")).isFalse();
  }

  @Test
  void nullOrBlankPassesThrough() {
    EmailRateLimiter limiter = new EmailRateLimiter(1);
    assertThat(limiter.tryConsume(null)).isTrue();
    assertThat(limiter.tryConsume("")).isTrue();
    assertThat(limiter.tryConsume("   ")).isTrue();
  }
}
