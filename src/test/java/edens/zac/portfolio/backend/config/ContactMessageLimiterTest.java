package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContactMessageLimiterTest {

  @Test
  void firstFiveAttemptsSucceedSixthFails() {
    ContactMessageLimiter limiter = new ContactMessageLimiter(5);
    for (int i = 0; i < 5; i++) {
      assertThat(limiter.tryConsume("user@example.com")).isTrue();
    }
    assertThat(limiter.tryConsume("user@example.com")).isFalse();
  }

  @Test
  void differentEmailsHaveIndependentBuckets() {
    ContactMessageLimiter limiter = new ContactMessageLimiter(2);
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
    ContactMessageLimiter limiter = new ContactMessageLimiter(2);
    assertThat(limiter.tryConsume("Foo@Example.com")).isTrue();
    assertThat(limiter.tryConsume("  foo@example.com ")).isTrue();
    // Same effective key — third attempt must fail.
    assertThat(limiter.tryConsume("FOO@EXAMPLE.COM")).isFalse();
  }

  @Test
  void nullOrBlankPassesThrough() {
    ContactMessageLimiter limiter = new ContactMessageLimiter(1);
    assertThat(limiter.tryConsume(null)).isTrue();
    assertThat(limiter.tryConsume("")).isTrue();
    assertThat(limiter.tryConsume("   ")).isTrue();
  }
}
