package edens.zac.portfolio.backend.controller.edit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.config.EditAccessWebConfig;
import edens.zac.portfolio.backend.config.SecurityConfig;
import edens.zac.portfolio.backend.config.SessionAuthenticationFilter;
import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.services.CollectionAccessService;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.ContentService;
import edens.zac.portfolio.backend.services.SessionService;
import edens.zac.portfolio.backend.types.AccessLevel;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The spec's authorization matrix on the REAL chain for every /api/edit endpoint: anonymous 401,
 * GENERAL 403, CLIENT 403, COLLABORATOR 200-class, admin-with-no-grant 200-class. RoleRepository is
 * the only mock below the resolution seam.
 */
@WebMvcTest(EditController.class)
@Import({
  SecurityConfig.class,
  SessionAuthenticationFilter.class,
  EditAccessWebConfig.class,
  CollectionAccessService.class
})
class EditControllerAuthorizationWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private SessionService sessionService;
  @MockBean private RoleRepository roleRepository;
  @MockBean private CollectionService collectionService;
  @MockBean private ContentService contentService;

  private static final long COLLECTION_ID = 5L;

  private MockHttpServletRequestBuilder[] endpointRequests() {
    return new MockHttpServletRequestBuilder[] {
      post("/api/edit/collections/5/reorder")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"reorders\":[{\"contentId\":42,\"newOrderIndex\":0}]}"),
      patch("/api/edit/collections/5/rating")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"rating\":3}"),
      put("/api/edit/collections/5")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"id\":5,\"title\":\"Renamed Gallery\"}"),
      patch("/api/edit/collections/5/images")
          .contentType(MediaType.APPLICATION_JSON)
          .content("[{\"id\":9,\"title\":\"T\"}]"),
    };
  }

  private Cookie sessionFor(AuthPrincipal principal) {
    when(sessionService.resolve(eq("tok"))).thenReturn(Optional.of(principal));
    return new Cookie("ezac_session", "tok");
  }

  private void stubHappyServices() {
    lenient()
        .when(collectionService.reorderContent(anyLong(), any()))
        .thenReturn(CollectionModel.builder().id(COLLECTION_ID).build());
    lenient()
        .when(collectionService.updateContentWithMetadata(anyLong(), any()))
        .thenReturn(
            new CollectionRequests.UpdateResponse(
                CollectionModel.builder().id(COLLECTION_ID).build(), null));
    lenient().when(contentService.updateImages(any())).thenReturn(new java.util.HashMap<>());
  }

  @Test
  void anonymousIsUnauthorizedOnEveryEndpoint() throws Exception {
    for (MockHttpServletRequestBuilder request : endpointRequests()) {
      mockMvc.perform(request).andExpect(status().isUnauthorized());
    }
  }

  @Test
  void generalAndClientAreForbiddenOnEveryEndpoint() throws Exception {
    for (AccessLevel level : new AccessLevel[] {AccessLevel.GENERAL, AccessLevel.CLIENT}) {
      Cookie cookie = sessionFor(AuthPrincipal.client(7L, "u@x.com", false));
      when(roleRepository.highestLevel(7L, COLLECTION_ID)).thenReturn(Optional.of(level));
      for (MockHttpServletRequestBuilder request : endpointRequests()) {
        mockMvc.perform(request.cookie(cookie)).andExpect(status().isForbidden());
      }
    }
  }

  @Test
  void collaboratorPassesOnEveryEndpoint() throws Exception {
    stubHappyServices();
    Cookie cookie = sessionFor(AuthPrincipal.client(7L, "co@x.com", false));
    when(roleRepository.highestLevel(7L, COLLECTION_ID))
        .thenReturn(Optional.of(AccessLevel.COLLABORATOR));
    for (MockHttpServletRequestBuilder request : endpointRequests()) {
      mockMvc.perform(request.cookie(cookie)).andExpect(status().is2xxSuccessful());
    }
  }

  @Test
  void adminWithNoGrantPassesOnEveryEndpoint() throws Exception {
    stubHappyServices();
    Cookie cookie = sessionFor(new AuthPrincipal(1L, "a@x.com", true, false));
    for (MockHttpServletRequestBuilder request : endpointRequests()) {
      mockMvc.perform(request.cookie(cookie)).andExpect(status().is2xxSuccessful());
    }
  }
}
