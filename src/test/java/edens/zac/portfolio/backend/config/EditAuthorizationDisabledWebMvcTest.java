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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * With app.admin.enforce-authz=false (local dev), /api/edit/** is login-free like /api/admin/**.
 *
 * <p>Defines its own nested stub controller rather than importing {@code
 * EditAccessWebMvcTest.StubControllers}. The root cause is default-configuration-class detection,
 * not the {@code @Import}: {@code SpringBootTestContextBootstrapper} first looks for a static
 * nested {@code @Configuration} class declared directly on THIS test class; {@code @Import}ing
 * another class's nested config does not register one for this class. With none found, it falls
 * back to the app's {@code @SpringBootConfiguration} ({@code Application}), which component-scans
 * the whole app -- pulling in the package-private {@code AdminController} and its six unmocked
 * service dependencies, and failing context load. Declaring an own nested {@code @Configuration}
 * (even an empty-looking stub-controller one) suppresses that fallback. Every other regression
 * slice in this package (e.g. {@link AdminAuthorizationDisabledWebMvcTest}) already does this.
 */
@WebMvcTest
@Import({
  SecurityConfig.class,
  SessionAuthenticationFilter.class,
  EditAccessWebConfig.class,
  EditAuthorizationDisabledWebMvcTest.StubControllers.class
})
@TestPropertySource(properties = "app.admin.enforce-authz=false")
class EditAuthorizationDisabledWebMvcTest {

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
  void anonymousReachesEditRoutesWhenEnforcementIsOff() throws Exception {
    mockMvc.perform(get("/api/edit/collections/5/ping")).andExpect(status().isOk());
  }
}
