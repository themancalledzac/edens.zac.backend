package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalSecretFilterTest {

  private final InternalSecretFilter filter = new InternalSecretFilter("the-secret", "");

  @Test
  void orderIsBelowSpringSecurityChain() {
    Order order = InternalSecretFilter.class.getAnnotation(Order.class);
    assertThat(order).isNotNull();
    // Spring Security's FilterChainProxy registers at -100; the channel gate must run before it.
    assertThat(order.value()).isEqualTo(-200);
  }

  @Test
  void rejectsMissingSecretWith403() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/read/ping");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  void passesWithValidSecret() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/read/ping");
    request.addHeader("X-Internal-Secret", "the-secret");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  void healthEndpointBypassesGate() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }
}
