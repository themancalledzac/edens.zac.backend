package edens.zac.portfolio.backend.config;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.services.CollectionAccessService;
import edens.zac.portfolio.backend.services.SessionService;
import edens.zac.portfolio.backend.types.AccessLevel;
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
 * The /api/edit/** gate on the REAL security chain and interceptor: anonymous 401 (matcher),
 * below-COLLABORATOR 403 (interceptor), COLLABORATOR and admin pass. Uses a stub controller so the
 * gate is pinned independently of the real EditController (Task 8). RoleRepository is the only mock
 * below the seam, so CollectionAccessService's admin sentinel logic runs for real.
 */
@WebMvcTest
@Import({
  SecurityConfig.class,
  SessionAuthenticationFilter.class,
  EditAccessWebConfig.class,
  CollectionAccessService.class,
  EditAccessWebMvcTest.StubControllers.class
})
class EditAccessWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private SessionService sessionService;
  @MockBean private RoleRepository roleRepository;

  @Configuration
  static class StubControllers {
    @Bean
    StubEditController stubEditController() {
      return new StubEditController();
    }

    @Bean
    StubScopelessEditController stubScopelessEditController() {
      return new StubScopelessEditController();
    }
  }

  @RestController
  static class StubEditController {
    @GetMapping("/api/edit/collections/{collectionId}/ping")
    String ping(@PathVariable Long collectionId) {
      return "pong";
    }
  }

  @RestController
  static class StubScopelessEditController {
    @GetMapping("/api/edit/ping")
    String ping() {
      return "pong";
    }
  }

  private static final String PATH = "/api/edit/collections/5/ping";

  private Cookie sessionFor(AuthPrincipal principal) {
    when(sessionService.resolve(eq("tok"))).thenReturn(Optional.of(principal));
    return new Cookie("ezac_session", "tok");
  }

  @Test
  void anonymousIsUnauthorized() throws Exception {
    mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
  }

  @Test
  void generalGrantIsForbidden() throws Exception {
    Cookie cookie = sessionFor(AuthPrincipal.client(7L, "g@x.com", false));
    when(roleRepository.highestLevel(7L, 5L)).thenReturn(Optional.of(AccessLevel.GENERAL));
    mockMvc.perform(get(PATH).cookie(cookie)).andExpect(status().isForbidden());
  }

  @Test
  void clientGrantIsForbidden() throws Exception {
    Cookie cookie = sessionFor(AuthPrincipal.client(7L, "c@x.com", false));
    when(roleRepository.highestLevel(7L, 5L)).thenReturn(Optional.of(AccessLevel.CLIENT));
    mockMvc.perform(get(PATH).cookie(cookie)).andExpect(status().isForbidden());
  }

  @Test
  void noGrantAtAllIsForbidden() throws Exception {
    Cookie cookie = sessionFor(AuthPrincipal.client(7L, "n@x.com", false));
    when(roleRepository.highestLevel(7L, 5L)).thenReturn(Optional.empty());
    mockMvc.perform(get(PATH).cookie(cookie)).andExpect(status().isForbidden());
  }

  @Test
  void collaboratorGrantPasses() throws Exception {
    Cookie cookie = sessionFor(AuthPrincipal.client(7L, "co@x.com", false));
    when(roleRepository.highestLevel(7L, 5L)).thenReturn(Optional.of(AccessLevel.COLLABORATOR));
    mockMvc.perform(get(PATH).cookie(cookie)).andExpect(status().isOk());
  }

  @Test
  void adminWithNoGrantPasses() throws Exception {
    Cookie cookie = sessionFor(new AuthPrincipal(1L, "a@x.com", true, false));
    mockMvc.perform(get(PATH).cookie(cookie)).andExpect(status().isOk());
  }

  @Test
  void editRouteWithoutCollectionScopeIsDeniedFailClosed() throws Exception {
    Cookie cookie = sessionFor(new AuthPrincipal(1L, "a@x.com", true, false));
    mockMvc.perform(get("/api/edit/ping").cookie(cookie)).andExpect(status().isForbidden());
  }
}
