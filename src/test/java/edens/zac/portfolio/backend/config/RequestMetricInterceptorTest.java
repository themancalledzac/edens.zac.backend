package edens.zac.portfolio.backend.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import edens.zac.portfolio.backend.dao.RequestMetricRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

@ExtendWith(MockitoExtension.class)
class RequestMetricInterceptorTest {

  private static final LocalDate FIXED_DAY = LocalDate.of(2026, 7, 7);
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-07-07T10:15:30Z"), ZoneOffset.UTC);

  @Mock private RequestMetricRepository repository;

  private RequestMetricInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new RequestMetricInterceptor(repository, FIXED_CLOCK);
  }

  private MockHttpServletRequest routedRequest(String pattern, Map<String, String> pathVars) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/read/anything");
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
    if (pathVars != null) {
      request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVars);
    }
    return request;
  }

  @Test
  void incrementsWithRoutePatternAndSlug() {
    MockHttpServletRequest request =
        routedRequest("/api/read/collections/{slug}", Map.of("slug", "iceland-2026"));

    interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);

    verify(repository).increment(FIXED_DAY, "/api/read/collections/{slug}", "iceland-2026");
  }

  @Test
  void incrementsWithNullSlugForSlugLessRoute() {
    MockHttpServletRequest request = routedRequest("/api/read/collections", Map.of());

    interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);

    verify(repository).increment(FIXED_DAY, "/api/read/collections", null);
  }

  @Test
  void recordsNothingWhenRouteWasNotMapped() {
    // No BEST_MATCHING_PATTERN_ATTRIBUTE — the request never matched a handler.
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/read/nope");

    interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);

    verifyNoInteractions(repository);
  }

  @Test
  void swallowsRepositoryExceptionAndNeverPropagates() {
    MockHttpServletRequest request =
        routedRequest("/api/read/collections/{slug}", Map.of("slug", "boom"));
    doThrow(new RuntimeException("db down"))
        .when(repository)
        .increment(any(LocalDate.class), eq("/api/read/collections/{slug}"), eq("boom"));

    // Must NOT throw — a metrics failure cannot break the real response.
    interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);

    verify(repository).increment(FIXED_DAY, "/api/read/collections/{slug}", "boom");
  }
}
