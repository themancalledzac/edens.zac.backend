package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.FilterChain;
import java.lang.reflect.Field;
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

    @Test
    void xRealIpIsReadBeforeXForwardedFor() throws Exception {
      // Burn the X-Real-IP bucket: 2 successful requests then 1 over-limit.
      String realIpClient = "203.0.113.99";
      String xffClient = "198.51.100.7";

      for (int i = 0; i < 2; i++) {
        var req = new MockHttpServletRequest();
        req.setRequestURI("/api/public/messages");
        req.setRemoteAddr("172.16.0.1");
        req.addHeader("X-Real-IP", realIpClient);
        req.addHeader("X-Forwarded-For", xffClient);
        filter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));
      }

      // 3rd request from same X-Real-IP should now 429 — confirming the bucket key was X-Real-IP,
      // not X-Forwarded-For. If X-Forwarded-For had been the key, this would still be the 1st hit
      // for xffClient.
      var thirdReq = new MockHttpServletRequest();
      thirdReq.setRequestURI("/api/public/messages");
      thirdReq.setRemoteAddr("172.16.0.1");
      thirdReq.addHeader("X-Real-IP", realIpClient);
      thirdReq.addHeader("X-Forwarded-For", xffClient);
      var resp = new MockHttpServletResponse();
      filter.doFilter(thirdReq, resp, mock(FilterChain.class));

      assertThat(resp.getStatus()).isEqualTo(429);
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

  @Nested
  class CaffeineEviction {

    @Test
    void bucketsBackingCacheIsCaffeineBounded() throws Exception {
      // Compile-time assertion: the buckets field must be a Caffeine Cache, not a
      // ConcurrentHashMap. This guards against a regression where the unbounded map returns.
      Field f = RateLimitFilter.class.getDeclaredField("ipBuckets");
      f.setAccessible(true);
      Object value = f.get(filter);
      assertThat(value).isInstanceOf(Cache.class);
    }

    @Test
    void manyUniqueIpsStayWithinMaximumSizeBound() throws Exception {
      // Push 50k unique IPs through the filter and confirm the cache stayed bounded
      // (maximumSize=10_000). Caffeine evicts asynchronously so we allow some slack.
      FilterChain chain = mock(FilterChain.class);
      for (int i = 0; i < 50_000; i++) {
        var req = new MockHttpServletRequest();
        req.setRequestURI("/api/public/messages");
        req.setRemoteAddr("10." + ((i >> 16) & 0xff) + "." + ((i >> 8) & 0xff) + "." + (i & 0xff));
        filter.doFilter(req, new MockHttpServletResponse(), chain);
      }

      Field f = RateLimitFilter.class.getDeclaredField("ipBuckets");
      f.setAccessible(true);
      Cache<?, ?> cache = (Cache<?, ?>) f.get(filter);
      // Force any pending eviction maintenance.
      cache.cleanUp();
      // Caffeine maximumSize is a soft bound; allow ~20% slack.
      assertThat(cache.estimatedSize()).isLessThanOrEqualTo(12_000L);
    }
  }
}
