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
import edens.zac.portfolio.backend.services.UserFollowsService;
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
class UserFollowsControllerProdTest {

  @Mock private UserFollowsService userFollowsService;

  @InjectMocks private UserFollowsControllerProd controller;

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
            post("/api/read/user/follows")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"collectionId\":3}"))
        .andExpect(status().isUnauthorized());

    verify(userFollowsService, never()).add(anyLong(), anyLong());
  }

  @Test
  void addAuthenticatedReturns201AndCallsService() throws Exception {
    mockMvc
        .perform(
            post("/api/read/user/follows")
                .with(asUser(client))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"collectionId\":3}"))
        .andExpect(status().isCreated());

    verify(userFollowsService).add(7L, 3L);
  }

  @Test
  void deleteReturns204() throws Exception {
    mockMvc
        .perform(delete("/api/read/user/follows/3").with(asUser(client)))
        .andExpect(status().isNoContent());

    verify(userFollowsService).remove(7L, 3L);
  }

  @Test
  void listReturnsArray() throws Exception {
    when(userFollowsService.listFollowedCollectionIds(7L)).thenReturn(List.of(3L, 4L));

    mockMvc
        .perform(get("/api/read/user/follows").with(asUser(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value(3))
        .andExpect(jsonPath("$[1]").value(4));
  }

  @Test
  void listAnonymousIsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/read/user/follows")).andExpect(status().isUnauthorized());

    verify(userFollowsService, never()).listFollowedCollectionIds(anyLong());
  }
}
