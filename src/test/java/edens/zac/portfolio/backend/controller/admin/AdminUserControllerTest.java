package edens.zac.portfolio.backend.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.services.UserInviteService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

  private MockMvc mockMvc;

  @Mock private AppUserRepository appUserRepository;
  @Mock private UserInviteService userInviteService;

  // Trailing slash on purpose: exercises the trailing-slash-safe invite-URL join.
  private static final String FRONTEND_BASE_URL = "https://app.example.com/";

  @BeforeEach
  void setUp() {
    AdminUserController controller =
        new AdminUserController(appUserRepository, userInviteService, FRONTEND_BASE_URL);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Nested
  class CreateUser {

    @Test
    void createUserReturns201WithUserIdAndInviteUrl() throws Exception {
      when(appUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
      when(appUserRepository.insert(any(AppUserEntity.class))).thenReturn(42L);
      when(userInviteService.createInvite(42L, "alice@example.com")).thenReturn("raw-token-abc");

      mockMvc
          .perform(
              post("/api/admin/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"alice@example.com\",\"displayName\":\"Alice\"}"))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.userId").value(42))
          // Trailing slash on the base URL must not produce a double slash before "invite".
          .andExpect(jsonPath("$.inviteUrl").value("https://app.example.com/invite/raw-token-abc"));
    }

    @Test
    void emailIsNormalizedToLowercase() throws Exception {
      when(appUserRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
      when(appUserRepository.insert(any(AppUserEntity.class))).thenReturn(7L);
      when(userInviteService.createInvite(7L, "alice@example.com")).thenReturn("tok");

      mockMvc
          .perform(
              post("/api/admin/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"ALICE@EXAMPLE.COM\"}"))
          .andExpect(status().isCreated());

      // verify findByEmail was called with lowercase email
      verify(appUserRepository).findByEmail("alice@example.com");
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
      when(appUserRepository.findByEmail("alice@example.com"))
          .thenReturn(Optional.of(AppUserEntity.builder().id(1L).build()));

      mockMvc
          .perform(
              post("/api/admin/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"alice@example.com\"}"))
          .andExpect(status().isConflict());

      verify(appUserRepository, never()).insert(any());
      verify(userInviteService, never()).createInvite(anyLong(), anyString());
    }

    @Test
    void missingEmailReturns400() throws Exception {
      mockMvc
          .perform(
              post("/api/admin/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"NoEmail\"}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    void invalidEmailReturns400() throws Exception {
      mockMvc
          .perform(
              post("/api/admin/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"not-an-email\"}"))
          .andExpect(status().isBadRequest());
    }
  }
}
