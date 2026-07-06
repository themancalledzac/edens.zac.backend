package edens.zac.portfolio.backend.config;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.services.SessionService;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The exploit-chain regression for the anonymous /api/admin/** hole, with the authZ toggle ON (prod
 * behaviour, and the default everywhere). Exercises the REAL security filter chain (@WebMvcTest +
 * SecurityConfig + SessionAuthenticationFilter) — standalone MockMvc would not run the chain and
 * could not assert authZ. A stub controller stands in for the admin surface so the test is
 * decoupled from any specific AdminController route.
 */
@WebMvcTest
@Import({
  SecurityConfig.class,
  SessionAuthenticationFilter.class,
  AdminAuthorizationEnforcedWebMvcTest.StubAdminControllers.class
})
@TestPropertySource(properties = "app.admin.enforce-authz=true")
class AdminAuthorizationEnforcedWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private SessionService sessionService;

  @Configuration
  static class StubAdminControllers {
    @Bean
    StubAdminController stubAdminController() {
      return new StubAdminController();
    }
  }

  @RestController
  static class StubAdminController {
    @GetMapping("/api/admin/ping")
    String pingGet() {
      return "pong";
    }

    @PostMapping("/api/admin/ping")
    String pingPost() {
      return "pong";
    }
  }

  // Anonymous (no credentials) is rejected by the authenticationEntryPoint, which this app wires to
  // sendError(401) — the same 401-for-unauthenticated contract SecurityConfigWebMvcTest pins for
  // /api/auth/me. An authenticated-but-non-admin principal instead trips the accessDeniedHandler
  // (403). Either way /api/admin/** is closed to non-admins; only the status code differs by cause.
  @Test
  void anonPostIsRejected() throws Exception {
    mockMvc.perform(post("/api/admin/ping")).andExpect(status().isUnauthorized());
  }

  @Test
  void anonGetIsRejected() throws Exception {
    mockMvc.perform(get("/api/admin/ping")).andExpect(status().isUnauthorized());
  }

  @Test
  void nonAdminSessionIsForbidden() throws Exception {
    when(sessionService.resolve(eq("user-token")))
        .thenReturn(Optional.of(new AuthPrincipal(7L, "user@example.com", false, false)));

    mockMvc
        .perform(get("/api/admin/ping").cookie(new Cookie("ezac_session", "user-token")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminSessionIsAllowed() throws Exception {
    when(sessionService.resolve(eq("admin-token")))
        .thenReturn(Optional.of(new AuthPrincipal(1L, "admin@example.com", true, false)));

    mockMvc
        .perform(get("/api/admin/ping").cookie(new Cookie("ezac_session", "admin-token")))
        .andExpect(status().isOk());
  }
}
