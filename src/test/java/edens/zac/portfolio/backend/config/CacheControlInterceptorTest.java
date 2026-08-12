package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class CacheControlInterceptorTest {

  private static final String PUBLIC_HEADER =
      "max-age=60, public, s-maxage=300, stale-while-revalidate=300";

  private static final CacheControlInterceptor INTERCEPTOR =
      new CacheControlInterceptor(new ReadCachePolicy(60, 300));

  /**
   * Drives the interceptor the way Spring does: the best-matching pattern attribute is populated by
   * handler mapping before any interceptor runs.
   *
   * @param method HTTP method
   * @param routePattern resolved route pattern, or null to simulate an unmapped request
   * @return the Cache-Control header the interceptor stamped
   */
  private static String headerFor(
      CacheControlInterceptor interceptor, String method, String routePattern) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, "/irrelevant");
    if (routePattern != null) {
      request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, routePattern);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();

    interceptor.preHandle(request, response, new Object());

    return response.getHeader("Cache-Control");
  }

  private static String headerFor(String method, String routePattern) {
    return headerFor(INTERCEPTOR, method, routePattern);
  }

  @Nested
  @DisplayName("allow-listed routes")
  class AllowListed {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "/api/read/collections",
          "/api/read/collections/location/{slug}",
          "/api/read/content/tags",
          "/api/read/content/people",
          "/api/read/content/cameras",
          "/api/read/content/lenses",
          "/api/read/content/locations",
          "/api/read/content/film-metadata",
          "/api/read/content/images/search"
        })
    @DisplayName("GET on an allow-listed route is shared-cacheable")
    void getOnAllowListedRouteIsPublic(String route) {
      assertThat(headerFor("GET", route)).isEqualTo(PUBLIC_HEADER);
    }

    @Test
    @DisplayName("every allow-listed constant is actually honoured")
    void allowListIsFullyCovered() {
      assertThat(CacheControlInterceptor.PUBLIC_ROUTES)
          .allSatisfy(route -> assertThat(headerFor("GET", route)).isEqualTo(PUBLIC_HEADER));
    }
  }

  @Nested
  @DisplayName("default-deny")
  class DefaultDeny {

    @ParameterizedTest
    @ValueSource(
        strings = {
          // Per-user surfaces: the body depends on the caller's session.
          "/api/read/user/me/page",
          "/api/read/user/follows",
          "/api/read/user/saves",
          "/api/read/user/saves/images",
          "/api/read/user/selects",
          "/api/read/user/ratings",
          // Redirects to per-request signed URLs.
          "/api/read/content/images/{id}/download",
          "/api/read/collections/{slug}/download"
        })
    @DisplayName("GET on a non-allow-listed route is no-store")
    void getOnUnlistedRouteIsNoStore(String route) {
      assertThat(headerFor("GET", route)).isEqualTo("no-store");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/read/collections/{slug}", "/api/read/collections/{slug}/meta"})
    @DisplayName("slug-resolving collection routes are never publicly cacheable")
    void slugResolvingCollectionRoutesAreNotAllowListed(String route) {
      // REGRESSION GUARD. Resolving a collection by slug is viewer-dependent three ways:
      //
      //   1. a password-protected gallery's body varies on the gallery_access_<slug> cookie;
      //   2. enforceVisibility hides HIDDEN collections from anonymous callers, so the same URL
      //      is a 404 for the public and a 200 with full content for an admin or grantee;
      //   3. the synthetic all-collections slug is permission-scoped by verified identity, and
      //      is built with isPasswordProtected unset -- so a body-level password check reads
      //      null and cannot detect the scoping at all.
      //
      // An earlier version allow-listed .../{slug}/meta and had the controller mark {slug}
      // public whenever it was not password-protected. That let a shared cache store a
      // privileged viewer's response and serve it to the public. Do not re-add these without a
      // resolved-entity check that proves viewer-independence.
      assertThat(CacheControlInterceptor.PUBLIC_ROUTES).doesNotContain(route);
      assertThat(headerFor("GET", route)).isEqualTo("no-store");
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    @DisplayName("a non-GET method is no-store even on an allow-listed path")
    void nonGetIsNeverCacheable(String method) {
      assertThat(headerFor(method, "/api/read/collections")).isEqualTo("no-store");
    }

    @Test
    @DisplayName("an unmapped request is no-store")
    void unmappedRequestIsNoStore() {
      assertThat(headerFor("GET", null)).isEqualTo("no-store");
    }

    @Test
    @DisplayName("a route that merely starts with an allow-listed prefix is not cacheable")
    void prefixCollisionIsNotCacheable() {
      // Exact pattern matching, so no sibling route inherits a parent's TTL by prefix.
      assertThat(headerFor("GET", "/api/read/collections/{slug}/download")).isEqualTo("no-store");
      assertThat(headerFor("GET", "/api/read/content/tags/{id}")).isEqualTo("no-store");
    }
  }

  @Nested
  @DisplayName("configuration kill switch")
  class KillSwitch {

    @Test
    @DisplayName("max-age of zero collapses even allow-listed routes to no-store")
    void zeroMaxAgeDisablesCaching() {
      CacheControlInterceptor disabled = new CacheControlInterceptor(new ReadCachePolicy(0, 300));

      assertThat(headerFor(disabled, "GET", "/api/read/collections")).isEqualTo("no-store");
      assertThat(headerFor(disabled, "GET", "/api/read/content/tags")).isEqualTo("no-store");
    }
  }
}
