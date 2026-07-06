package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.services.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit-tests the authority grant: every resolved session is a ROLE_USER, and ROLE_ADMIN is granted
 * iff the principal's {@code isAdmin} flag is set. This is what makes admin capability derive from
 * {@code users.is_admin} rather than from merely holding a session.
 */
class SessionAuthenticationFilterTest {

  private final SessionService sessionService = mock(SessionService.class);
  private final SessionAuthenticationFilter filter =
      new SessionAuthenticationFilter(sessionService);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void adminPrincipalGetsRoleAdmin() throws Exception {
    when(sessionService.resolve("admin-token"))
        .thenReturn(Optional.of(new AuthPrincipal(1L, "admin@example.com", true, false)));

    var authorities = runFilterWithCookie("admin-token");

    assertThat(authorities).contains("ROLE_USER", "ROLE_ADMIN");
  }

  @Test
  void nonAdminPrincipalDoesNotGetRoleAdmin() throws Exception {
    when(sessionService.resolve("user-token"))
        .thenReturn(Optional.of(new AuthPrincipal(7L, "user@example.com", false, false)));

    var authorities = runFilterWithCookie("user-token");

    assertThat(authorities).contains("ROLE_USER").doesNotContain("ROLE_ADMIN");
  }

  private java.util.List<String> runFilterWithCookie(String token) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/ping");
    request.setCookies(new Cookie("ezac_session", token));
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
  }
}
