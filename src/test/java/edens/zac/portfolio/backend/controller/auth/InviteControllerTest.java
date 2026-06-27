package edens.zac.portfolio.backend.controller.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
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
import edens.zac.portfolio.backend.services.SessionService;
import edens.zac.portfolio.backend.services.UserInviteService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InviteControllerTest {

  private MockMvc mockMvc;

  @Mock private UserInviteService userInviteService;
  @Mock private AppUserRepository appUserRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private SessionService sessionService;

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

  private AppUserEntity activeUser(Long id, String email, String displayName) {
    return AppUserEntity.builder()
        .id(id)
        .email(email)
        .name(displayName)
        .status(UserStatus.INVITED)
        .webauthnUserHandle(UUID.randomUUID())
        .build();
  }

  @Nested
  class PreviewInvite {

    @Test
    void validTokenReturns200WithEmailAndDisplayName() throws Exception {
      when(userInviteService.validate("good-token"))
          .thenReturn(Optional.of(invite(10L, "bob@example.com")));
      when(appUserRepository.findById(10L))
          .thenReturn(Optional.of(activeUser(10L, "bob@example.com", "Bob")));

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
          .thenReturn(Optional.of(activeUser(10L, "bob@example.com", null)));

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
    void validTokenAcceptsAndReturns204WithSession() throws Exception {
      UserInviteEntity inv = invite(10L, "bob@example.com");
      AppUserEntity user = activeUser(10L, "bob@example.com", "OldName");

      when(userInviteService.redeem("good-token")).thenReturn(Optional.of(inv));
      when(appUserRepository.findById(10L)).thenReturn(Optional.of(user));
      when(passwordEncoder.encode("newpass1")).thenReturn("{bcrypt}hashed");

      mockMvc
          .perform(
              post("/api/auth/invite/good-token/accept")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Bob\",\"password\":\"newpass1\"}"))
          .andExpect(status().isNoContent());

      verify(appUserRepository).updatePasswordHash(10L, "{bcrypt}hashed");
      verify(appUserRepository).updateName(10L, "Bob");
      verify(appUserRepository).updateStatus(10L, UserStatus.ACTIVE);
      verify(sessionService).create(eq(user), eq(false), any(), any());
    }

    @Test
    void alreadyUsedTokenReturns410() throws Exception {
      // redeem()'s atomic gate yields empty for an already-redeemed token → 410 Gone.
      when(userInviteService.redeem("used-token")).thenReturn(Optional.empty());

      mockMvc
          .perform(
              post("/api/auth/invite/used-token/accept")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Bob\",\"password\":\"newpass1\"}"))
          .andExpect(status().isGone());

      verify(appUserRepository, never()).updatePasswordHash(anyLong(), anyString());
      verify(appUserRepository, never()).updateStatus(anyLong(), any());
      verify(sessionService, never()).create(any(), anyBoolean(), any(), any());
    }

    @Test
    void expiredTokenReturns410() throws Exception {
      // redeem() also yields empty for an expired token (collapsed with the used case) → 410 Gone.
      when(userInviteService.redeem("expired-token")).thenReturn(Optional.empty());

      mockMvc
          .perform(
              post("/api/auth/invite/expired-token/accept")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Bob\",\"password\":\"newpass1\"}"))
          .andExpect(status().isGone());

      verify(appUserRepository, never()).updatePasswordHash(anyLong(), anyString());
      verify(sessionService, never()).create(any(), anyBoolean(), any(), any());
    }

    @Test
    void shortPasswordReturns400() throws Exception {
      mockMvc
          .perform(
              post("/api/auth/invite/good-token/accept")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"Bob\",\"password\":\"short\"}"))
          .andExpect(status().isBadRequest());

      verify(userInviteService, never()).redeem(anyString());
    }

    @Test
    void blankDisplayNameReturns400() throws Exception {
      mockMvc
          .perform(
              post("/api/auth/invite/good-token/accept")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"\",\"password\":\"validpass\"}"))
          .andExpect(status().isBadRequest());
    }
  }
}
