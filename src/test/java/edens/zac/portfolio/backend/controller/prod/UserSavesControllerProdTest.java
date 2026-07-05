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

import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.services.UserSavesService;
import edens.zac.portfolio.backend.types.ContentType;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
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

  private final AuthPrincipal client = new AuthPrincipal(7L, "c@b.com", false, true);

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
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
  void addNullImageIdIsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/read/user/saves")
                .with(asUser(client))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imageId\":null}"))
        .andExpect(status().isBadRequest());

    verify(userSavesService, never()).add(anyLong(), anyLong());
  }

  @Test
  void addNonexistentImageIsNotFound() throws Exception {
    Mockito.doThrow(new ResourceNotFoundException("Image not found with ID: 999"))
        .when(userSavesService)
        .add(7L, 999L);

    mockMvc
        .perform(
            post("/api/read/user/saves")
                .with(asUser(client))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imageId\":999}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteAnonymousIsUnauthorized() throws Exception {
    mockMvc.perform(delete("/api/read/user/saves/42")).andExpect(status().isUnauthorized());

    verify(userSavesService, never()).remove(anyLong(), anyLong());
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

  private ContentModels.Image imageModel(Long id, String title, String imageUrl) {
    return new ContentModels.Image(
        id,
        ContentType.IMAGE,
        title,
        null,
        null,
        null,
        imageUrl,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        null,
        List.of(),
        List.of(),
        List.of());
  }

  @Test
  void listImagesReturnsModels() throws Exception {
    when(userSavesService.listSavedImages(7L))
        .thenReturn(
            List.of(
                imageModel(42L, "Newer", "https://cdn.example.com/newer.jpg"),
                imageModel(43L, "Older", "https://cdn.example.com/older.jpg")));

    mockMvc
        .perform(get("/api/read/user/saves/images").with(asUser(client)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(42))
        .andExpect(jsonPath("$[0].title").value("Newer"))
        .andExpect(jsonPath("$[0].imageUrl").value("https://cdn.example.com/newer.jpg"))
        .andExpect(jsonPath("$[1].id").value(43));
  }

  @Test
  void listImagesAnonymousIsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/read/user/saves/images")).andExpect(status().isUnauthorized());

    verify(userSavesService, never()).listSavedImages(anyLong());
  }
}
