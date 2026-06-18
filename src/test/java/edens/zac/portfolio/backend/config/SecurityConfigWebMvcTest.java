package edens.zac.portfolio.backend.config;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.services.SessionService;
import edens.zac.portfolio.backend.types.Role;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest
@Import({
  SecurityConfig.class,
  SessionAuthenticationFilter.class,
  SecurityConfigWebMvcTest.StubControllers.class
})
class SecurityConfigWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private SessionService sessionService;

  @Configuration
  static class StubControllers {
    @Bean
    StubReadController stubReadController() {
      return new StubReadController();
    }

    @Bean
    StubMeController stubMeController() {
      return new StubMeController();
    }
  }

  @RestController
  static class StubReadController {
    @GetMapping("/api/read/ping")
    String ping() {
      return "pong";
    }
  }

  @RestController
  static class StubMeController {
    @GetMapping("/api/auth/me")
    String me() {
      Object principal =
          SecurityContextHolder.getContext().getAuthentication() == null
              ? null
              : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
      if (principal instanceof AuthPrincipal p) {
        return p.email();
      }
      // Spring Security's anonymous authentication has a String "anonymousUser" principal.
      return "anon";
    }
  }

  @Test
  void existingReadEndpointStays200ForAnonymous() throws Exception {
    mockMvc
        .perform(get("/api/read/ping"))
        .andExpect(status().isOk())
        .andExpect(content().string("pong"));
  }

  @Test
  void meReturns401ForAnonymous() throws Exception {
    mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void meResolvesPrincipalWhenCookiePresent() throws Exception {
    when(sessionService.resolve(eq("valid-token")))
        .thenReturn(Optional.of(new AuthPrincipal(7L, "admin@example.com", Role.ADMIN, false)));

    mockMvc
        .perform(get("/api/auth/me").cookie(new Cookie("ezac_session", "valid-token")))
        .andExpect(status().isOk())
        .andExpect(content().string("admin@example.com"));
  }
}
