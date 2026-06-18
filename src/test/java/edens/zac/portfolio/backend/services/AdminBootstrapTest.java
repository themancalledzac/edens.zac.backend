package edens.zac.portfolio.backend.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.types.Role;
import edens.zac.portfolio.backend.types.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

  @Mock private AppUserRepository appUserRepository;
  @Mock private PasswordEncoder passwordEncoder;

  @Test
  void createsAdminWhenAbsent() {
    when(appUserRepository.existsByRole(Role.ADMIN)).thenReturn(false);
    when(passwordEncoder.encode("s3cret")).thenReturn("{bcrypt}$2a$10$encoded");

    AdminBootstrap bootstrap =
        new AdminBootstrap(appUserRepository, passwordEncoder, "admin@example.com", "s3cret");
    bootstrap.init();

    ArgumentCaptor<AppUserEntity> captor = ArgumentCaptor.forClass(AppUserEntity.class);
    verify(appUserRepository).insert(captor.capture());
    AppUserEntity inserted = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(inserted.getEmail()).isEqualTo("admin@example.com");
    org.assertj.core.api.Assertions.assertThat(inserted.getRole()).isEqualTo(Role.ADMIN);
    org.assertj.core.api.Assertions.assertThat(inserted.getStatus()).isEqualTo(UserStatus.ACTIVE);
    org.assertj.core.api.Assertions.assertThat(inserted.getPasswordHash())
        .isEqualTo("{bcrypt}$2a$10$encoded");
    org.assertj.core.api.Assertions.assertThat(inserted.getWebauthnUserHandle()).isNotNull();
  }

  @Test
  void doesNothingWhenEnvBlank() {
    AdminBootstrap bootstrap = new AdminBootstrap(appUserRepository, passwordEncoder, "", "");
    bootstrap.init();
    verify(appUserRepository, never()).existsByRole(any());
    verify(appUserRepository, never()).insert(any());
  }

  @Test
  void idempotentWhenAdminAlreadyExists() {
    when(appUserRepository.existsByRole(Role.ADMIN)).thenReturn(true);

    AdminBootstrap bootstrap =
        new AdminBootstrap(appUserRepository, passwordEncoder, "admin@example.com", "s3cret");
    bootstrap.init();

    verify(appUserRepository, never()).insert(any());
    verify(passwordEncoder, never()).encode(eq("s3cret"));
  }
}
