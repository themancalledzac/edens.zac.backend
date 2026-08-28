package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShareEmailLimiterTest {

  private static ShareEmailLimiter limiter(int perSender, int globalPerDay) {
    return new ShareEmailLimiter(perSender, Duration.ofHours(1), globalPerDay, Duration.ofDays(1));
  }

  @Test
  @DisplayName("a sender gets their hourly allowance and no more")
  void firstFiveSendsSucceedSixthFails() {
    ShareEmailLimiter limiter = limiter(5, 1000);
    for (int i = 0; i < 5; i++) {
      assertThat(limiter.allow(7L)).isTrue();
    }
    assertThat(limiter.allow(7L)).isFalse();
  }

  @Test
  @DisplayName("one sender exhausting their bucket does not spend another sender's")
  void differentSendersHaveIndependentBuckets() {
    ShareEmailLimiter limiter = limiter(2, 1000);
    assertThat(limiter.allow(7L)).isTrue();
    assertThat(limiter.allow(7L)).isTrue();
    assertThat(limiter.allow(7L)).isFalse();

    assertThat(limiter.allow(8L)).isTrue();
    assertThat(limiter.allow(8L)).isTrue();
    assertThat(limiter.allow(8L)).isFalse();
  }

  @Test
  @DisplayName("the global cap bounds the shared SES reputation across every sender")
  void globalCapRefusesEveryoneOnceSpent() {
    // Per-sender allowance is generous; the global cap is what runs out first, which is the point:
    // a per-sender limit alone scales with the number of accounts, and an SES suspension takes the
    // invite and gallery-password emails down with it.
    ShareEmailLimiter limiter = limiter(100, 3);
    assertThat(limiter.allow(1L)).isTrue();
    assertThat(limiter.allow(2L)).isTrue();
    assertThat(limiter.allow(3L)).isTrue();

    assertThat(limiter.allow(4L)).isFalse();
    // A sender who has not sent anything is refused too -- the cap is on the shared resource.
    assertThat(limiter.allow(99L)).isFalse();
  }

  @Test
  @DisplayName("a spent global cap does not drain per-sender buckets on requests that cannot win")
  void globalCapIsCheckedBeforeThePerSenderBucket() throws InterruptedException {
    // The global period is short so it can refill inside the test; that refill is the only moment
    // the drained token becomes observable. A version of this test that never let the global cap
    // come back could not tell the two orderings apart -- both refuse everything forever once it
    // is spent, so the assertions passed either way. Working rule 15.
    ShareEmailLimiter limiter =
        new ShareEmailLimiter(2, Duration.ofHours(1), 1, Duration.ofMillis(200));

    assertThat(limiter.allow(7L)).isTrue();

    // Global is spent. Refused -- and 7L's second token must NOT have been consumed paying for it.
    assertThat(limiter.allow(7L)).isFalse();
    assertThat(limiter.allow(7L)).isFalse();

    Thread.sleep(250L);

    // Global is back. 7L still has one token if the global cap was checked first, and none if the
    // refusals above were charged to their bucket.
    assertThat(limiter.allow(7L)).isTrue();
  }

  @Test
  @DisplayName("a null sender still spends from the global cap")
  void nullSenderIsAllowedThroughButNotForFree() {
    // The route requires a session so this cannot happen in practice. It must not become the way
    // around the global cap if it ever does.
    ShareEmailLimiter limiter = limiter(5, 2);
    assertThat(limiter.allow(null)).isTrue();
    assertThat(limiter.allow(null)).isTrue();
    assertThat(limiter.allow(null)).isFalse();
    assertThat(limiter.allow(7L)).isFalse();
  }

  @Test
  @DisplayName("the sender's budget comes back after the window")
  void windowRefillRestoresBudget() throws InterruptedException {
    ShareEmailLimiter limiter =
        new ShareEmailLimiter(2, Duration.ofMillis(200), 1000, Duration.ofDays(1));

    assertThat(limiter.allow(7L)).isTrue();
    assertThat(limiter.allow(7L)).isTrue();
    assertThat(limiter.allow(7L)).isFalse();

    Thread.sleep(250L);

    assertThat(limiter.allow(7L)).isTrue();
    assertThat(limiter.allow(7L)).isTrue();
    assertThat(limiter.allow(7L)).isFalse();
  }
}
