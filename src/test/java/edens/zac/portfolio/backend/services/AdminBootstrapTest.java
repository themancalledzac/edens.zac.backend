package edens.zac.portfolio.backend.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.types.UserStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * AdminBootstrap designates the SPECIFIC admin from env config, keeping identity out of the public
 * repo. Covers the three paths: designate an existing non-admin, seed when absent + password given,
 * and no-op when the email is blank.
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

  @Mock private AppUserRepository appUserRepository;
  @Mock private PasswordEncoder passwordEncoder;

  @Test
  void designatesExistingNonAdminUser() {
    AppUserEntity existing =
        AppUserEntity.builder().id(42L).email("boss@example.com").isAdmin(false).build();
    when(appUserRepository.findByEmail("boss@example.com")).thenReturn(Optional.of(existing));

    new AdminBootstrap(appUserRepository, passwordEncoder, "boss@example.com", "").init();

    verify(appUserRepository).setAdmin(42L, true);
    verify(appUserRepository, never()).insert(any());
  }

  @Test
  void noOpWhenExistingUserAlreadyAdmin() {
    AppUserEntity existing =
        AppUserEntity.builder().id(42L).email("boss@example.com").isAdmin(true).build();
    when(appUserRepository.findByEmail("boss@example.com")).thenReturn(Optional.of(existing));

    new AdminBootstrap(appUserRepository, passwordEncoder, "boss@example.com", "").init();

    verify(appUserRepository, never()).setAdmin(anyLong(), anyBoolean());
    verify(appUserRepository, never()).insert(any());
  }

  @Test
  void seedsNewAdminWhenAbsentAndPasswordProvided() {
    when(appUserRepository.findByEmail("boss@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("s3cret")).thenReturn("{bcrypt}hashed");
    when(appUserRepository.insert(any())).thenReturn(99L);

    new AdminBootstrap(appUserRepository, passwordEncoder, "boss@example.com", "s3cret").init();

    ArgumentCaptor<AppUserEntity> captor = ArgumentCaptor.forClass(AppUserEntity.class);
    verify(appUserRepository).insert(captor.capture());
    AppUserEntity seeded = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(seeded.getEmail()).isEqualTo("boss@example.com");
    org.assertj.core.api.Assertions.assertThat(seeded.getPasswordHash())
        .isEqualTo("{bcrypt}hashed");
    org.assertj.core.api.Assertions.assertThat(seeded.getStatus()).isEqualTo(UserStatus.ACTIVE);
    org.assertj.core.api.Assertions.assertThat(seeded.getWebauthnUserHandle()).isNotNull();
    verify(appUserRepository).setAdmin(99L, true);
  }

  @Test
  void skipsSeedWhenAbsentAndNoPassword() {
    when(appUserRepository.findByEmail("boss@example.com")).thenReturn(Optional.empty());

    new AdminBootstrap(appUserRepository, passwordEncoder, "boss@example.com", "").init();

    verify(appUserRepository, never()).insert(any());
    verify(appUserRepository, never()).setAdmin(anyLong(), anyBoolean());
  }

  @Test
  void noOpWhenEmailBlank() {
    new AdminBootstrap(appUserRepository, passwordEncoder, "", "s3cret").init();

    verifyNoInteractions(appUserRepository);
    verifyNoInteractions(passwordEncoder);
  }
}
