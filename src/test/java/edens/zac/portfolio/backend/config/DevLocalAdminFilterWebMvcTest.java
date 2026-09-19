package edens.zac.portfolio.backend.config;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.ShareLinkEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.services.SessionService;
import edens.zac.portfolio.backend.services.ShareLinkService;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-profile companion to {@link AdminAuthorizationEnforcedWebMvcTest}. With {@code dev} active
 * and a bootstrap admin configured, an anonymous request reaches {@code /api/admin/**} as that
 * admin; an explicit non-admin session still gets 403, and routes outside the admin surface stay
 * anonymous. Runs the real security chain so the filter's position in it is what is under test.
 */
@WebMvcTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "app.auth.admin.bootstrap-email=admin@example.com")
@Import({
  SecurityConfig.class,
  SessionAuthenticationFilter.class,
  FlybySessionFilter.class,
  DevLocalAdminFilter.class,
  DevLocalAdminFilterWebMvcTest.StubControllers.class
})
class DevLocalAdminFilterWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private SessionService sessionService;

  @MockBean private ShareLinkService shareLinkService;

  @MockBean private AppUserRepository appUserRepository;

  @Configuration
  static class StubControllers {
    @Bean
    StubController stubController() {
      return new StubController();
    }
  }

  @RestController
  static class StubController {
    @GetMapping("/api/admin/ping")
    String adminGet() {
      return "pong";
    }

    @PostMapping("/api/admin/ping")
    String adminPost() {
      return "pong";
    }

    @GetMapping("/api/auth/me")
    String me() {
      return "me";
    }
  }

  @BeforeEach
  void bootstrapAdminRowExists() {
    when(appUserRepository.findByEmail(eq("admin@example.com")))
        .thenReturn(
            Optional.of(
                AppUserEntity.builder().id(1L).email("admin@example.com").isAdmin(true).build()));
  }

  @Test
  void anonGetAdminIsAllowedAsBootstrapAdmin() throws Exception {
    mockMvc.perform(get("/api/admin/ping")).andExpect(status().isOk());
  }

  @Test
  void anonPostAdminIsAllowedAsBootstrapAdmin() throws Exception {
    mockMvc.perform(post("/api/admin/ping")).andExpect(status().isOk());
  }

  @Test
  void explicitNonAdminSessionIsStillForbidden() throws Exception {
    when(sessionService.resolve(eq("user-token")))
        .thenReturn(Optional.of(new AuthPrincipal(7L, "user@example.com", false, false)));

    mockMvc
        .perform(get("/api/admin/ping").cookie(new Cookie("ezac_session", "user-token")))
        .andExpect(status().isForbidden());
  }

  @Test
  void shareLinkCookieDoesNotLockTheDevAdminOut() throws Exception {
    when(shareLinkService.resolveByRawToken(eq("flyby-token")))
        .thenReturn(Optional.of(ShareLinkEntity.builder().id(42L).build()));

    mockMvc
        .perform(get("/api/admin/ping").cookie(new Cookie(FlybyCookies.COOKIE_NAME, "flyby-token")))
        .andExpect(status().isOk());
  }

  @Test
  void routesOutsideAdminSurfaceStayAnonymous() throws Exception {
    mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }
}
