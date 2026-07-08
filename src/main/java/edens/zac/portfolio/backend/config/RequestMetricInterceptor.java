package edens.zac.portfolio.backend.config;

import edens.zac.portfolio.backend.dao.RequestMetricRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Records a private, admin-only aggregate request count per {@code (day, route, slug)}. A {@link
 * HandlerInterceptor} (not a servlet {@code Filter}) is used deliberately: it runs AFTER Spring's
 * handler mapping, so {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} yields the bounded
 * route pattern (e.g. {@code /api/read/collections/{slug}}) and {@link
 * HandlerMapping#URI_TEMPLATE_VARIABLES_ATTRIBUTE} yields the resolved path variables — neither of
 * which a pre-mapping Filter can see.
 *
 * <p>NO PII is recorded: only day, the route pattern, and the single meaningful slug are captured —
 * never ip, user id, user-agent, query strings, or per-request rows. In prod the {@link
 * InternalSecretFilter} servlet filter ({@code @Order(-200)}, before the MVC DispatcherServlet)
 * rejects requests lacking the BFF secret, so those never reach this interceptor.
 *
 * <p>Recording is best-effort and failure-safe: the entire record step is wrapped so any exception
 * (DB down, etc.) is caught, logged at WARN, and never propagates — a metrics failure must never
 * break or slow the real response. Recording happens in {@link #afterCompletion} so only
 * successfully-routed requests are counted.
 *
 * <p>This is intentionally NOT a Spring {@code @Component}: it is instantiated by {@link
 * RequestMetricWebConfig} only when a {@link RequestMetricRepository} is present. That keeps sliced
 * {@code @WebMvcTest} contexts (which pick up {@code HandlerInterceptor} beans but have no DAO
 * layer) loading cleanly.
 */
@Slf4j
public class RequestMetricInterceptor implements HandlerInterceptor {

  /**
   * Path-variable names, in priority order, from which the single meaningful slug is taken. The
   * read routes name their collection/location identifier {@code slug}.
   */
  private static final String[] SLUG_VARIABLE_NAMES = {"slug"};

  private final RequestMetricRepository requestMetricRepository;
  private final Clock clock;

  public RequestMetricInterceptor(RequestMetricRepository requestMetricRepository) {
    this(requestMetricRepository, Clock.systemUTC());
  }

  /** Test seam: inject a fixed {@link Clock} so the recorded {@code day} is deterministic. */
  RequestMetricInterceptor(RequestMetricRepository requestMetricRepository, Clock clock) {
    this.requestMetricRepository = requestMetricRepository;
    this.clock = clock;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    try {
      String route = bestMatchingPattern(request);
      if (route == null) {
        // No route pattern means the request was not mapped to a handler — nothing to bound.
        return;
      }
      String slug = extractSlug(request);
      requestMetricRepository.increment(LocalDate.now(clock), route, slug);
    } catch (Exception e) {
      // Failure-safety: never let a metrics write break or slow the real response.
      log.warn("Failed to record request metric for {}", request.getRequestURI(), e);
    }
  }

  private static String bestMatchingPattern(HttpServletRequest request) {
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    return pattern instanceof String s && !s.isBlank() ? s : null;
  }

  @SuppressWarnings("unchecked")
  private static String extractSlug(HttpServletRequest request) {
    Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (!(vars instanceof Map<?, ?> map) || map.isEmpty()) {
      return null;
    }
    Map<String, String> pathVariables = (Map<String, String>) map;
    for (String name : SLUG_VARIABLE_NAMES) {
      String value = pathVariables.get(name);
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }
}
