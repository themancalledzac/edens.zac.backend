package edens.zac.portfolio.backend.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Short-TTL, single-use store for in-flight WebAuthn ceremony options (the challenge). Because we
 * use Spring Security's WebAuthn operations programmatically (not the session-backed DSL), the
 * options minted in {@code /start} must be held server-side until {@code /finish}.
 *
 * <p>Keyed by {@code webauthn_user_handle} (registration) or a per-attempt id (login). {@link
 * #take(String)} removes-and-returns, so each challenge is consumed once (replay defence). Caffeine
 * bounds memory and expires stragglers after the TTL.
 */
@Component
public class WebAuthnChallengeStore {

  private final Cache<String, Object> cache;

  @Autowired
  public WebAuthnChallengeStore(
      @Value("${app.auth.webauthn.challenge-ttl-minutes:5}") long ttlMinutes) {
    this(Duration.ofMinutes(ttlMinutes));
  }

  /** Test-friendly constructor accepting an explicit TTL so expiry can be exercised in millis. */
  WebAuthnChallengeStore(Duration ttl) {
    this.cache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(10_000).build();
  }

  /**
   * Store ceremony options under a key (user handle or per-attempt id).
   *
   * @param key cache key
   * @param options ceremony options object to store
   */
  public void put(String key, Object options) {
    cache.put(key, options);
  }

  /**
   * Remove and return the stored options, or empty if absent/expired. Single-use.
   *
   * @param key cache key
   * @return stored value if present, otherwise empty
   */
  public Optional<Object> take(String key) {
    Object value = cache.asMap().remove(key);
    return Optional.ofNullable(value);
  }
}
