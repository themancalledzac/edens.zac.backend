package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

  private RateLimitFilter filter;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    filter = new RateLimitFilter(2, objectMapper);
  }

  private MockHttpServletRequest publicRequest(String ip) {
    var req = new MockHttpServletRequest();
    req.setRequestURI("/api/public/messages");
    req.setRemoteAddr(ip);
    return req;
  }

  @Nested
  class PublicPaths {

    @Test
    void firstTwoRequestsPass() throws Exception {
      FilterChain chain = mock(FilterChain.class);
      for (int i = 0; i < 2; i++) {
        var resp = new MockHttpServletResponse();
        filter.doFilter(publicRequest("1.2.3.4"), resp, chain);
        assertThat(resp.getStatus()).isNotEqualTo(429);
      }
      verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void thirdRequestReturns429WithRetryAfterHeader() throws Exception {
      for (int i = 0; i < 2; i++) {
        filter.doFilter(
            publicRequest("5.6.7.8"), new MockHttpServletResponse(), mock(FilterChain.class));
      }
      var limitedResp = new MockHttpServletResponse();
      FilterChain chain = mock(FilterChain.class);
      filter.doFilter(publicRequest("5.6.7.8"), limitedResp, chain);

      assertThat(limitedResp.getStatus()).isEqualTo(429);
      assertThat(limitedResp.getHeader("Retry-After")).isNotNull();
      verify(chain, times(0)).doFilter(any(), any());
    }

    @Test
    void differentIpsHaveIndependentBuckets() throws Exception {
      for (int i = 0; i < 2; i++) {
        filter.doFilter(
            publicRequest("10.0.0.1"), new MockHttpServletResponse(), mock(FilterChain.class));
      }
      var resp = new MockHttpServletResponse();
      FilterChain chain = mock(FilterChain.class);
      filter.doFilter(publicRequest("10.0.0.2"), resp, chain);

      assertThat(resp.getStatus()).isNotEqualTo(429);
      verify(chain).doFilter(any(), any());
    }

    @Test
    void xForwardedForFirstHopIsUsedAsIp() throws Exception {
      FilterChain chain = mock(FilterChain.class);
      var req = new MockHttpServletRequest();
      req.setRequestURI("/api/public/messages");
      req.setRemoteAddr("172.16.0.1");
      req.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");

      var resp = new MockHttpServletResponse();
      filter.doFilter(req, resp, chain);

      assertThat(resp.getStatus()).isNotEqualTo(429);
      verify(chain).doFilter(any(), any());
    }
  }

  @Nested
  class NonPublicPaths {

    @Test
    void nonPublicPathSkipsRateLimit() throws Exception {
      FilterChain chain = mock(FilterChain.class);
      var req = new MockHttpServletRequest();
      req.setRequestURI("/api/read/collections");
      req.setRemoteAddr("1.2.3.4");
      var resp = new MockHttpServletResponse();

      filter.doFilter(req, resp, chain);

      assertThat(resp.getStatus()).isNotEqualTo(429);
      verify(chain).doFilter(req, resp);
    }
  }
}
