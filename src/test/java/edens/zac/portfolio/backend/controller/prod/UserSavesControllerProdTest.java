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
import edens.zac.portfolio.backend.services.UserSavesService;
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
class UserSavesControllerProdTest {

  @Mock private UserSavesService userSavesService;

  @InjectMocks private UserSavesControllerProd controller;

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
            post("/api/read/user/saves")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imageId\":42}"))
        .andExpect(status().isUnauthorized());

    verify(userSavesService, never()).add(anyLong(), anyLong());
  }

  @Test
  void addAuthenticatedReturns201AndCallsService() throws Exception {
    mockMvc
        .perform(
            post("/api/read/user/saves")
                .with(asUser(client))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imageId\":42}"))
        .andExpect(status().isCreated());

    verify(userSavesService).add(7L, 42L);
  }

  @Test
  void deleteReturns204() throws Exception {
    mockMvc
        .perform(delete("/api/read/user/saves/42").with(asUser(client)))
        .andExpect(status().isNoContent());

    verify(userSavesService).remove(7L, 42L);
  }

  @Test
  void listReturnsArray() throws Exception {
    when(userSavesService.listSavedImageIds(7L)).thenReturn(List.of(42L, 43L));

    mockMvc
        .perform(get("/api/read/user/saves").with(asUser(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value(42))
        .andExpect(jsonPath("$[1]").value(43));
  }

  @Test
  void listAnonymousIsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/read/user/saves")).andExpect(status().isUnauthorized());

    verify(userSavesService, never()).listSavedImageIds(anyLong());
  }
}
