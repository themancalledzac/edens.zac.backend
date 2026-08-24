package edens.zac.portfolio.backend.controller.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.UserInviteEntity;
import edens.zac.portfolio.backend.services.UserInviteService;
import edens.zac.portfolio.backend.services.UserInviteService.AcceptResult;
import edens.zac.portfolio.backend.types.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InviteControllerTest {

  private MockMvc mockMvc;

  @Mock private UserInviteService userInviteService;
  @Mock private AppUserRepository appUserRepository;

  @InjectMocks private InviteController controller;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private UserInviteEntity invite(Long userId, String email) {
    return UserInviteEntity.builder().id(1L).userId(userId).email(email).build();
  }

  private AppUserEntity userWithStatus(
      Long id, String email, String displayName, UserStatus status) {
    return AppUserEntity.builder()
        .id(id)
        .email(email)
        .name(displayName)
        .status(status)
        .webauthnUserHandle(UUID.randomUUID())
        .build();
  }

  private AppUserEntity invitedUser(Long id, String email, String displayName) {
    return userWithStatus(id, email, displayName, UserStatus.INVITED);
  }

  @Nested
  class PreviewInvite {

    @Test
    void validTokenReturns200WithEmailAndDisplayName() throws Exception {
      when(userInviteService.validate("good-token"))
          .thenReturn(Optional.of(invite(10L, "bob@example.com")));
      when(appUserRepository.findById(10L))
          .thenReturn(Optional.of(invitedUser(10L, "bob@example.com", "Bob")));

      mockMvc
          .perform(get("/api/auth/invite/good-token"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value("bob@example.com"))
          .andExpect(jsonPath("$.displayName").value("Bob"));
    }

    @Test
    void validTokenWithNullDisplayNameReturns200() throws Exception {
      when(userInviteService.validate("good-token"))
          .thenReturn(Optional.of(invite(10L, "bob@example.com")));
      when(appUserRepository.findById(10L))
          .thenReturn(Optional.of(invitedUser(10L, "bob@example.com", null)));

      mockMvc
          .perform(get("/api/auth/invite/good-token"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value("bob@example.com"))
          .andExpect(jsonPath("$.displayName").isEmpty());
    }

    @Test
    void invalidOrExpiredTokenReturns404() throws Exception {
      when(userInviteService.validate("bad-token")).thenReturn(Optional.empty());

      mockMvc.perform(get("/api/auth/invite/bad-token")).andExpect(status().isNotFound());
    }
  }

  @Nested
  class AcceptInvite {

    @Test
    void acceptedResultReturns204() throws Exception {
      when(userInviteService.accept(eq("good-token"), eq("Bob"), eq("newpass1"), any(), any()))
          .thenReturn(AcceptResult.ACCEPTED);

      mockMvc
          .perform(
              post("/api/auth/invite/good-token/accept")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Bob\",\"password\":\"newpass1\"}"))
          .andExpect(status().isNoContent());
    }

    @Test
    void rejectedResultReturns410() throws Exception {
      when(userInviteService.accept(anyString(), anyString(), anyString(), any(), any()))
          .thenReturn(AcceptResult.REJECTED);

      mockMvc
          .perform(
              post("/api/auth/invite/used-token/accept")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Bob\",\"password\":\"newpass1\"}"))
          .andExpect(status().isGone());
    }

    @Test
    void shortPasswordReturns400() throws Exception {
      mockMvc
          .perform(
              post("/api/auth/invite/good-token/accept")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Bob\",\"password\":\"short\"}"))
          .andExpect(status().isBadRequest());

      verify(userInviteService, never())
          .accept(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void blankDisplayNameReturns400() throws Exception {
      mockMvc
          .perform(
              post("/api/auth/invite/good-token/accept")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"\",\"password\":\"validpass\"}"))
          .andExpect(status().isBadRequest());

      verify(userInviteService, never())
          .accept(anyString(), anyString(), anyString(), any(), any());
    }
  }
}
