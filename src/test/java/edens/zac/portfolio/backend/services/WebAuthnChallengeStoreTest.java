package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WebAuthnChallengeStoreTest {

  @Test
  void putThenTakeReturnsStoredValue() {
    WebAuthnChallengeStore store = new WebAuthnChallengeStore(Duration.ofMinutes(5));
    store.put("key-1", "challenge-options");

    Optional<Object> taken = store.take("key-1");

    assertThat(taken).contains("challenge-options");
  }

  @Test
  void takeIsSingleUse() {
    WebAuthnChallengeStore store = new WebAuthnChallengeStore(Duration.ofMinutes(5));
    store.put("key-1", "challenge-options");

    assertThat(store.take("key-1")).isPresent();
    assertThat(store.take("key-1")).isEmpty();
  }

  @Test
  void takeMissingKeyIsEmpty() {
    WebAuthnChallengeStore store = new WebAuthnChallengeStore(Duration.ofMinutes(5));
    assertThat(store.take("nope")).isEmpty();
  }

  @Test
  void entriesExpireAfterTtl() throws InterruptedException {
    WebAuthnChallengeStore store = new WebAuthnChallengeStore(Duration.ofMillis(50));
    store.put("key-1", "challenge-options");

    Thread.sleep(120);

    assertThat(store.take("key-1")).isEmpty();
  }
}
