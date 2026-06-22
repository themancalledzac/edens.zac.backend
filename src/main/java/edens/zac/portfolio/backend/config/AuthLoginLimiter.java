package edens.zac.portfolio.backend.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthLoginLimiter {

  private final int maxAttempts;
  private final Cache<String, Integer> failures;

  @Autowired
  public AuthLoginLimiter(
      @Value("${app.auth.login.max-attempts:5}") int maxAttempts,
      @Value("${app.auth.login.window-minutes:15}") int windowMinutes) {
    this.maxAttempts = maxAttempts;
    this.failures =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(windowMinutes))
            .maximumSize(10_000)
            .build();
  }

  public boolean isBlocked(String ip, String email) {
    String key = key(ip, email);
    if (key == null) {
      return false;
    }
    Integer count = failures.getIfPresent(key);
    return count != null && count >= maxAttempts;
  }

  public void recordFailure(String ip, String email) {
    String key = key(ip, email);
    if (key == null) {
      return;
    }
    failures.asMap().merge(key, 1, Integer::sum);
  }

  public void reset(String ip, String email) {
    String key = key(ip, email);
    if (key != null) {
      failures.invalidate(key);
    }
  }

  private static String key(String ip, String email) {
    if (ip == null || ip.isBlank() || email == null || email.isBlank()) {
      return null;
    }
    return ip.trim() + "|" + email.trim().toLowerCase(Locale.ROOT);
  }
}
