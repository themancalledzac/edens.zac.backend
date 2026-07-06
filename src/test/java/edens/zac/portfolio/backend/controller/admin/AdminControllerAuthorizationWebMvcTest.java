package edens.zac.portfolio.backend.controller.admin;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.config.SecurityConfig;
import edens.zac.portfolio.backend.config.SessionAuthenticationFilter;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.GeneralMetadataDTO;
import edens.zac.portfolio.backend.services.AdminHomeService;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.ContentService;
import edens.zac.portfolio.backend.services.ImageUploadPipelineService;
import edens.zac.portfolio.backend.services.JobTrackingService;
import edens.zac.portfolio.backend.services.MetadataService;
import edens.zac.portfolio.backend.services.SessionService;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins {@link AdminController} inside the {@code /api/admin/**} authorization gate on the REAL
 * security filter chain (not a stub controller, unlike its siblings in {@code config/}) — this is
 * the regression test for the 0207 prod bug: the controller used to be {@code @Profile("dev")},
 * which meant the route did not exist in prod at all (a {@code NoResourceFoundException}, mis-
 * reported as 500) rather than being correctly rejected by the security layer. Exercises {@code GET
 * /api/admin/collections/metadata} specifically because that was one of the two endpoints reported
 * 500ing in prod logs.
 *
 * @see edens.zac.portfolio.backend.config.AdminAuthorizationEnforcedWebMvcTest
 */
@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, SessionAuthenticationFilter.class})
class AdminControllerAuthorizationWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private SessionService sessionService;
  @MockBean private AdminHomeService adminHomeService;
  @MockBean private CollectionService collectionService;
  @MockBean private ContentService contentService;
  @MockBean private ImageUploadPipelineService imageUploadPipelineService;
  @MockBean private JobTrackingService jobTrackingService;
  @MockBean private MetadataService metadataService;

  private static final String METADATA_PATH = "/api/admin/collections/metadata";

  @Test
  void anonymousIsRejected() throws Exception {
    mockMvc.perform(get(METADATA_PATH)).andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedNonAdminIsForbidden() throws Exception {
    when(sessionService.resolve(eq("user-token")))
        .thenReturn(Optional.of(new AuthPrincipal(7L, "user@example.com", false, false)));

    mockMvc
        .perform(get(METADATA_PATH).cookie(new Cookie("ezac_session", "user-token")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminIsAllowed() throws Exception {
    when(sessionService.resolve(eq("admin-token")))
        .thenReturn(Optional.of(new AuthPrincipal(1L, "admin@example.com", true, false)));
    when(collectionService.getGeneralMetadata())
        .thenReturn(new GeneralMetadataDTO(null, null, null, null, null, null, null, null));

    mockMvc
        .perform(get(METADATA_PATH).cookie(new Cookie("ezac_session", "admin-token")))
        .andExpect(status().isOk());
  }
}
