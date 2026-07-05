package edens.zac.portfolio.backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.services.SessionService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-parity companion to {@link AdminAuthorizationEnforcedWebMvcTest}: with the authZ toggle OFF
 * (as in application-dev.properties), /api/admin/** falls through to permitAll so local dev stays
 * login-free. The prod-only InternalSecretFilter is the perimeter there, not this matrix.
 */
@WebMvcTest
@Import({
  SecurityConfig.class,
  SessionAuthenticationFilter.class,
  AdminAuthorizationDisabledWebMvcTest.StubAdminControllers.class
})
@TestPropertySource(properties = "app.admin.enforce-authz=false")
class AdminAuthorizationDisabledWebMvcTest {

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
  }

  @Test
  void anonGetIsNotForbidden() throws Exception {
    mockMvc.perform(get("/api/admin/ping")).andExpect(status().isOk());
  }
}
