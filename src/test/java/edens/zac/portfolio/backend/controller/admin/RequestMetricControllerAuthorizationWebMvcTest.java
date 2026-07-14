package edens.zac.portfolio.backend.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.config.SecurityConfig;
import edens.zac.portfolio.backend.config.SessionAuthenticationFilter;
import edens.zac.portfolio.backend.dao.RequestMetricRepository;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.services.SessionService;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins {@link RequestMetricController} inside the real {@code /api/admin/**} authorization gate
 * (SecurityConfig's {@code hasRole("ADMIN")}), mirroring {@link
 * AdminControllerAuthorizationWebMvcTest}: anonymous is 401, an authenticated non-admin is 403, and
 * an admin is 200.
 */
@WebMvcTest(RequestMetricController.class)
@Import({SecurityConfig.class, SessionAuthenticationFilter.class})
class RequestMetricControllerAuthorizationWebMvcTest {

  private static final String PATH = "/api/admin/metrics/requests";

  @Autowired private MockMvc mockMvc;

  @MockBean private SessionService sessionService;
  @MockBean private RequestMetricRepository requestMetricRepository;

  @Test
  void anonymousIsRejected() throws Exception {
    mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedNonAdminIsForbidden() throws Exception {
    when(sessionService.resolve(eq("user-token")))
        .thenReturn(Optional.of(new AuthPrincipal(7L, "user@example.com", false, false)));

    mockMvc
        .perform(get(PATH).cookie(new Cookie("ezac_session", "user-token")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminIsAllowed() throws Exception {
    when(sessionService.resolve(eq("admin-token")))
        .thenReturn(Optional.of(new AuthPrincipal(1L, "admin@example.com", true, false)));
    when(requestMetricRepository.findByDayRange(any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(List.of());

    mockMvc
        .perform(get(PATH).cookie(new Cookie("ezac_session", "admin-token")))
        .andExpect(status().isOk());
  }
}
