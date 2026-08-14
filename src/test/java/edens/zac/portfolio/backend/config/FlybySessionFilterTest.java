package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.entity.ShareLinkEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.services.ShareLinkService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Pins the three properties the share-link gate depends on: a real session outranks a link, an
 * unresolvable token produces no principal at all, and a resolved link produces a principal with no
 * userId and no authorities.
 */
class FlybySessionFilterTest {

  private final ShareLinkService shareLinkService = mock(ShareLinkService.class);
  private final FlybySessionFilter filter = new FlybySessionFilter(shareLinkService);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void resolvedTokenYieldsAFlybyPrincipalWithNoUserIdAndNoAuthorities() throws Exception {
    when(shareLinkService.resolveByRawToken("good"))
        .thenReturn(Optional.of(ShareLinkEntity.builder().id(42L).userId(7L).build()));

    runFilterWithCookie("good");

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    AuthPrincipal principal = (AuthPrincipal) auth.getPrincipal();
    assertThat(principal.shareId()).isEqualTo(42L);
    // No userId is what makes every identity-bearing endpoint refuse this principal.
    assertThat(principal.userId()).isNull();
    assertThat(principal.isAdmin()).isFalse();
    // No authorities is what makes every hasRole rule refuse it.
    assertThat(auth.getAuthorities()).isEmpty();
  }

  @Test
  void realSessionBeatsAPresentFlybyCookie() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                AuthPrincipal.client(7L, "u@example.com", true), null, List.of()));

    runFilterWithCookie("good");

    AuthPrincipal principal =
        (AuthPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    assertThat(principal.userId()).isEqualTo(7L);
    assertThat(principal.shareId()).isNull();
    // Not merely overridden afterwards -- the share is never even looked up.
    verifyNoInteractions(shareLinkService);
  }

  @Test
  void unknownOrRotatedTokenLeavesTheContextEmpty() throws Exception {
    when(shareLinkService.resolveByRawToken("stale")).thenReturn(Optional.empty());

    runFilterWithCookie("stale");

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void requestWithNoFlybyCookieIsUntouched() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/read/collections");
    filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verifyNoInteractions(shareLinkService);
  }

  @Test
  void filterSetsNoCookieOfItsOwn() throws Exception {
    when(shareLinkService.resolveByRawToken("good"))
        .thenReturn(Optional.of(ShareLinkEntity.builder().id(42L).userId(7L).build()));

    MockHttpServletResponse response = runFilterWithCookie("good");

    // A Set-Cookie here would ride on cacheable /api/read/** responses behind CloudFront, where a
    // cached cookie can reach the wrong viewer. Rolling the window is the share endpoints' job.
    assertThat(response.getHeaders("Set-Cookie")).isEmpty();
  }

  private MockHttpServletResponse runFilterWithCookie(String token) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/read/collections");
    request.setCookies(new Cookie(FlybyCookies.COOKIE_NAME, token));
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, mock(FilterChain.class));
    return response;
  }
}
