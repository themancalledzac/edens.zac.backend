package edens.zac.portfolio.backend.config;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pins {@link EditAccessWebConfig}'s fail-closed fallback: when {@link
 * edens.zac.portfolio.backend.services.CollectionAccessService} is absent from the slice (as here
 * -- deliberately not {@code @Import}ed and not mocked), the deny-all interceptor must engage, not
 * a no-op registration. Enforcement is on (the default), so this exercises the branch none of the
 * other /api/edit slices reach: {@link EditAccessWebMvcTest} always imports {@code
 * CollectionAccessService}, and {@link EditAuthorizationDisabledWebMvcTest} turns enforcement off
 * (skipping interceptor registration entirely, a different code path). An authenticated ADMIN
 * principal is used deliberately: it is the strongest possible principal, so a 403 here proves the
 * fallback denies unconditionally rather than merely failing to special-case a weaker one.
 */
@WebMvcTest
@Import({
  SecurityConfig.class,
  SessionAuthenticationFilter.class,
  EditAccessWebConfig.class,
  EditAccessFailClosedWebMvcTest.StubControllers.class
})
class EditAccessFailClosedWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private SessionService sessionService;

  @Configuration
  static class StubControllers {
    @Bean
    StubEditController stubEditController() {
      return new StubEditController();
    }
  }

  @RestController
  static class StubEditController {
    @GetMapping("/api/edit/collections/{collectionId}/ping")
    String ping(@PathVariable Long collectionId) {
      return "pong";
    }
  }

  @Test
  void adminIsDeniedWhenAccessServiceIsUnavailable() throws Exception {
    when(sessionService.resolve(eq("admin-token")))
        .thenReturn(Optional.of(new AuthPrincipal(1L, "a@x.com", true, false)));

    mockMvc
        .perform(
            get("/api/edit/collections/5/ping").cookie(new Cookie("ezac_session", "admin-token")))
        .andExpect(status().isForbidden());
  }
}
