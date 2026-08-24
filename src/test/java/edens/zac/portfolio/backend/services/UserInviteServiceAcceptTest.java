package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.UserInviteRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.UserInviteEntity;
import edens.zac.portfolio.backend.services.UserInviteService.AcceptResult;
import edens.zac.portfolio.backend.types.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The status rule on {@link UserInviteService#accept}. Redemption itself is covered by {@code
 * UserInviteServiceIntegrationTest}; these pin which accounts a redeemed token may activate.
 */
@ExtendWith(MockitoExtension.class)
class UserInviteServiceAcceptTest {

  @Mock private UserInviteRepository inviteRepository;
  @Mock private AppUserRepository appUserRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private SessionService sessionService;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;

  @InjectMocks private UserInviteService service;

  private void givenRedeemableToken(Long userId) {
    UserInviteEntity invite =
        UserInviteEntity.builder()
            .id(1L)
            .userId(userId)
            .email("bob@example.com")
            .expiresAt(LocalDateTime.now().plusDays(3))
            .build();
    when(inviteRepository.findByTokenHash(anyString())).thenReturn(Optional.of(invite));
    when(inviteRepository.markUsedIfUnused(anyLong(), any())).thenReturn(1);
  }

  private void givenUser(Long id, UserStatus status) {
    when(appUserRepository.findById(id))
        .thenReturn(
            Optional.of(
                AppUserEntity.builder().id(id).email("bob@example.com").status(status).build()));
  }

  @Test
  void invitedUserIsActivatedAndGetsASession() {
    givenRedeemableToken(10L);
    givenUser(10L, UserStatus.INVITED);
    when(passwordEncoder.encode("newpass1")).thenReturn("{bcrypt}hashed");

    AcceptResult result = service.accept("raw-token", "Bob", "newpass1", request, response);

    assertThat(result).isEqualTo(AcceptResult.ACCEPTED);
    verify(appUserRepository).updatePasswordHash(10L, "{bcrypt}hashed");
    verify(appUserRepository).updateName(10L, "Bob");
    verify(appUserRepository).updateStatus(10L, UserStatus.ACTIVE);
    verify(sessionService).create(any(), eq(false), any(), any());
  }

  @Test
  void activeUserAcceptsForPasswordReset() {
    // AdminUserController.regenerateInvite issues a reset link for an ACTIVE user, who redeems it
    // through this same call. Mutation this catches: narrow mayAcceptInvite to INVITED alone and
    // admin-issued password reset starts being rejected.
    givenRedeemableToken(11L);
    givenUser(11L, UserStatus.ACTIVE);
    when(passwordEncoder.encode("newpass1")).thenReturn("{bcrypt}hashed");

    AcceptResult result = service.accept("raw-token", "Bob", "newpass1", request, response);

    assertThat(result).isEqualTo(AcceptResult.ACCEPTED);
    verify(appUserRepository).updatePasswordHash(11L, "{bcrypt}hashed");
    verify(sessionService).create(any(), anyBoolean(), any(), any());
  }

  @Test
  void disabledUserIsRejectedAndNothingIsWritten() {
    // S-7: a disabled account holding an unexpired invite must not re-activate itself.
    // Mutation this catches: drop the mayAcceptInvite test and the flip to ACTIVE runs.
    givenRedeemableToken(12L);
    givenUser(12L, UserStatus.DISABLED);

    AcceptResult result = service.accept("raw-token", "Dan", "newpass1", request, response);

    assertThat(result).isEqualTo(AcceptResult.REJECTED);
    verify(appUserRepository, never()).updatePasswordHash(anyLong(), anyString());
    verify(appUserRepository, never()).updateName(anyLong(), anyString());
    verify(appUserRepository, never()).updateStatus(anyLong(), any());
    verify(sessionService, never()).create(any(), anyBoolean(), any(), any());
  }

  @Test
  void personRowIsRejected() {
    // PERSON is a tag-only identity with no login account. Mutation this catches: rewrite
    // mayAcceptInvite as a "status != DISABLED" denylist and a PERSON row becomes a real account.
    givenRedeemableToken(13L);
    givenUser(13L, UserStatus.PERSON);

    AcceptResult result = service.accept("raw-token", "Pat", "newpass1", request, response);

    assertThat(result).isEqualTo(AcceptResult.REJECTED);
    verify(appUserRepository, never()).updateStatus(anyLong(), any());
    verify(sessionService, never()).create(any(), anyBoolean(), any(), any());
  }

  @Test
  void unusableTokenIsRejectedWithoutReadingTheUser() {
    when(inviteRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

    AcceptResult result = service.accept("raw-token", "Bob", "newpass1", request, response);

    assertThat(result).isEqualTo(AcceptResult.REJECTED);
    verify(appUserRepository, never()).findById(anyLong());
    verify(sessionService, never()).create(any(), anyBoolean(), any(), any());
  }
}
