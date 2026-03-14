package edens.zac.portfolio.backend.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@Profile("prod")
@Slf4j
public class InternalSecretFilter implements Filter {

  private final String expectedSecret;

  public InternalSecretFilter(@Value("${internal.api.secret}") String expectedSecret) {
    this.expectedSecret = expectedSecret;
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest request = (HttpServletRequest) req;
    HttpServletResponse response = (HttpServletResponse) res;

    String uri = request.getRequestURI();
    if ("/actuator/health".equals(uri)
        || "/actuator/health/liveness".equals(uri)
        || "/actuator/health/readiness".equals(uri)) {
      chain.doFilter(req, res);
      return;
    }

    String secret = request.getHeader("X-Internal-Secret");
    if (secret == null || !constantTimeEquals(expectedSecret, secret)) {
      log.warn("Rejected request missing or invalid X-Internal-Secret: {}", uri);
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    chain.doFilter(req, res);
  }

  private boolean constantTimeEquals(String expected, String provided) {
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
  }
}
