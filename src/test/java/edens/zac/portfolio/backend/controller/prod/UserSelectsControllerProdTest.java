package edens.zac.portfolio.backend.controller.prod;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.UserSelectGroup;
import edens.zac.portfolio.backend.services.UserSelectsService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UserSelectsControllerProdTest {

  @Mock private UserSelectsService userSelectsService;

  @InjectMocks private UserSelectsControllerProd controller;

  private MockMvc mockMvc;

  private final AuthPrincipal client = new AuthPrincipal(7L, "c@b.com", true);

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private RequestPostProcessor asUser(AuthPrincipal principal) {
    return request -> {
      SecurityContextHolder.getContext()
          .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
      return request;
    };
  }

  @Test
  void addAnonymousIsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/api/read/user/selects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"collectionId\":3,\"contentId\":42}"))
        .andExpect(status().isUnauthorized());

    verify(userSelectsService, never()).add(anyLong(), anyLong(), anyLong());
  }

  @Test
  void addAuthenticatedReturns201AndCallsService() throws Exception {
    mockMvc
        .perform(
            post("/api/read/user/selects")
                .with(asUser(client))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"collectionId\":3,\"contentId\":42}"))
        .andExpect(status().isCreated());

    verify(userSelectsService).add(7L, 3L, 42L);
  }

  @Test
  void deleteReturns204() throws Exception {
    mockMvc
        .perform(delete("/api/read/user/selects/42").with(asUser(client)))
        .andExpect(status().isNoContent());

    verify(userSelectsService).remove(7L, 42L);
  }

  @Test
  void listIdsReturnsArray() throws Exception {
    when(userSelectsService.listSelectIds(7L, 3L)).thenReturn(List.of(42L, 43L));

    mockMvc
        .perform(get("/api/read/user/selects").param("collectionId", "3").with(asUser(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value(42))
        .andExpect(jsonPath("$[1]").value(43));
  }

  @Test
  void listAllReturnsGroups() throws Exception {
    when(userSelectsService.listAll(7L))
        .thenReturn(
            List.of(UserSelectGroup.builder().collectionId(3L).contentIds(List.of(42L)).build()));

    mockMvc
        .perform(get("/api/read/user/selects").with(asUser(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].collectionId").value(3))
        .andExpect(jsonPath("$[0].contentIds[0]").value(42));
  }

  @Test
  void listAllAnonymousIsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/read/user/selects")).andExpect(status().isUnauthorized());

    verify(userSelectsService, never()).listAll(anyLong());
  }
}
