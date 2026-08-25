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
    givenRedeemableToken(userId, "bob@example.com");
  }

  private void givenRedeemableToken(Long userId, String issuedTo) {
    UserInviteEntity invite =
        UserInviteEntity.builder()
            .id(1L)
            .userId(userId)
            .email(issuedTo)
            .expiresAt(LocalDateTime.now().plusDays(3))
            .build();
    when(inviteRepository.findByTokenHash(anyString())).thenReturn(Optional.of(invite));
    when(inviteRepository.markUsedIfUnused(anyLong(), any())).thenReturn(1);
  }

  private void givenUser(Long id, UserStatus status) {
    givenUser(id, status, "bob@example.com");
  }

  private void givenUser(Long id, UserStatus status, String email) {
    when(appUserRepository.findById(id))
        .thenReturn(
            Optional.of(AppUserEntity.builder().id(id).email(email).status(status).build()));
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
  void inviteIssuedToAnAddressTheAccountNoLongerHoldsIsRejected() {
    // S-10: an admin mints a reset link for old@example.com, then corrects the account's email.
    // The account is ACTIVE, so neither invite sweep in AdminUserController fires, and the old
    // inbox still holds a live token. Redemption must refuse it -- otherwise whoever controls the
    // prior address sets a password and takes the account.
    // Mutation this catches: drop the email test from accept and this goes green on a takeover.
    givenRedeemableToken(14L, "old@example.com");
    givenUser(14L, UserStatus.ACTIVE, "new@example.com");

    AcceptResult result = service.accept("raw-token", "Mal", "newpass1", request, response);

    assertThat(result).isEqualTo(AcceptResult.REJECTED);
    verify(appUserRepository, never()).updatePasswordHash(anyLong(), anyString());
    verify(appUserRepository, never()).updateName(anyLong(), anyString());
    verify(appUserRepository, never()).updateStatus(anyLong(), any());
    verify(sessionService, never()).create(any(), anyBoolean(), any(), any());
  }

  @Test
  void aRejectedStaleInviteIsStillSpent() {
    // Same shape as the status rejection: the token is redeemed before the account is read, so a
    // link presented against an address that has moved on is burnt rather than left live for a
    // second attempt.
    givenRedeemableToken(15L, "old@example.com");
    givenUser(15L, UserStatus.ACTIVE, "new@example.com");

    assertThat(service.accept("raw-token", "Mal", "newpass1", request, response))
        .isEqualTo(AcceptResult.REJECTED);
    verify(inviteRepository).markUsedIfUnused(anyLong(), any());
  }

  @Test
  void anInviteAddressDifferingOnlyInCaseStillAccepts() {
    // The legitimate case the guard must not break. Every write path lowercases before storing, so
    // this only arises for rows predating that -- but a case-sensitive comparison would reject a
    // real invitee, and per working rule 18 a wrong allowlist fails closed and surfaces late.
    // Mutation this catches: swap equalsIgnoreCase for equals.
    givenRedeemableToken(16L, "Bob@Example.com");
    givenUser(16L, UserStatus.INVITED, "bob@example.com");
    when(passwordEncoder.encode("newpass1")).thenReturn("{bcrypt}hashed");

    AcceptResult result = service.accept("raw-token", "Bob", "newpass1", request, response);

    assertThat(result).isEqualTo(AcceptResult.ACCEPTED);
    verify(appUserRepository).updateStatus(16L, UserStatus.ACTIVE);
  }

  @Test
  void anAccountWithNoEmailIsRejected() {
    // users.email is nullable (V35 relaxed it for tag-only PERSON rows), so the comparison has to
    // survive a null on the account side rather than throwing out of a security check.
    givenRedeemableToken(17L, "old@example.com");
    givenUser(17L, UserStatus.ACTIVE, null);

    AcceptResult result = service.accept("raw-token", "Mal", "newpass1", request, response);

    assertThat(result).isEqualTo(AcceptResult.REJECTED);
    verify(sessionService, never()).create(any(), anyBoolean(), any(), any());
  }

  @Test
  void invalidateInvitesForStatusSweepsWhenLeavingTheInviteLifecycle() {
    // S-9: an invite outlives the account being disabled by up to its 7-day TTL, so the token must
    // die with the transition. Mutation this catches: invert mayAcceptInvite here and a disabled
    // user keeps a redeemable link.
    when(inviteRepository.invalidateUnusedForUser(8L)).thenReturn(1);

    assertThat(service.invalidateInvitesForStatus(8L, UserStatus.DISABLED)).isEqualTo(1);
    verify(inviteRepository).invalidateUnusedForUser(8L);
  }

  @Test
  void invalidateInvitesForStatusSweepsForPersonToo() {
    when(inviteRepository.invalidateUnusedForUser(9L)).thenReturn(1);

    assertThat(service.invalidateInvitesForStatus(9L, UserStatus.PERSON)).isEqualTo(1);
    verify(inviteRepository).invalidateUnusedForUser(9L);
  }

  @Test
  void invalidateInvitesForStatusLeavesEligibleAccountsAlone() {
    // The scope guard: restoring an account must not break the password-reset link just sent to it,
    // and a resend to an INVITED user must survive. Mutation this catches: sweep unconditionally.
    assertThat(service.invalidateInvitesForStatus(8L, UserStatus.ACTIVE)).isZero();
    assertThat(service.invalidateInvitesForStatus(8L, UserStatus.INVITED)).isZero();
    verify(inviteRepository, never()).invalidateUnusedForUser(anyLong());
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
