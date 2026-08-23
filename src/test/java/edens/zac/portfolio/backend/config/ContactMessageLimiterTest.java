package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContactMessageLimiterTest {

  @Test
  void firstFiveAttemptsSucceedSixthFails() {
    ContactMessageLimiter limiter = new ContactMessageLimiter(5, 1_000_000);
    for (int i = 0; i < 5; i++) {
      assertThat(limiter.tryConsume("user@example.com")).isTrue();
    }
    assertThat(limiter.tryConsume("user@example.com")).isFalse();
  }

  @Test
  void differentEmailsHaveIndependentBuckets() {
    ContactMessageLimiter limiter = new ContactMessageLimiter(2, 1_000_000);
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
    ContactMessageLimiter limiter = new ContactMessageLimiter(2, 1_000_000);
    assertThat(limiter.tryConsume("Foo@Example.com")).isTrue();
    assertThat(limiter.tryConsume("  foo@example.com ")).isTrue();
    // Same effective key — third attempt must fail.
    assertThat(limiter.tryConsume("FOO@EXAMPLE.COM")).isFalse();
  }

  @Test
  void nullOrBlankPassesThrough() {
    ContactMessageLimiter limiter = new ContactMessageLimiter(1, 1_000_000);
    assertThat(limiter.tryConsume(null)).isTrue();
    assertThat(limiter.tryConsume("")).isTrue();
    assertThat(limiter.tryConsume("   ")).isTrue();
  }

  @Test
  void globalDailyCapRefusesEveryoneOnceReached() {
    ContactMessageLimiter limiter = new ContactMessageLimiter(100, 3);

    assertThat(limiter.tryConsume("a@example.com")).isTrue();
    assertThat(limiter.tryConsume("b@example.com")).isTrue();
    assertThat(limiter.tryConsume("c@example.com")).isTrue();

    // Per-email budget is untouched for this address, but the table's daily budget is spent.
    assertThat(limiter.tryConsume("d@example.com")).isFalse();
    assertThat(limiter.tryConsume("a@example.com")).isFalse();
  }

  @Test
  void rotatingTheEmailKeyCannotEscapeTheGlobalCap() {
    ContactMessageLimiter limiter = new ContactMessageLimiter(1, 4);

    // The per-email limit is the one an attacker picks the key for: a fresh address each time
    // never trips it. Only the global bucket bounds total inserts.
    for (int i = 0; i < 4; i++) {
      assertThat(limiter.tryConsume("throwaway" + i + "@example.com")).isTrue();
    }
    assertThat(limiter.tryConsume("throwaway99@example.com")).isFalse();
  }

  @Test
  void blankEmailStillConsumesGlobalBudget() {
    ContactMessageLimiter limiter = new ContactMessageLimiter(100, 2);

    assertThat(limiter.tryConsume(null)).isTrue();
    assertThat(limiter.tryConsume("  ")).isTrue();
    assertThat(limiter.tryConsume(null)).isFalse();
  }
}
