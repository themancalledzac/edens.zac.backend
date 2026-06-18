package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.types.Role;
import edens.zac.portfolio.backend.types.UserStatus;
import jakarta.annotation.PostConstruct;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the first ADMIN principal from environment variables on startup, so no admin secret lives
 * in git. Idempotent: it inserts an admin only when none exists and both env values are present;
 * once an admin exists it does nothing but logs a warning if the bootstrap password is still
 * configured (the operator's cue to remove it). Never throws on normal paths.
 */
@Component
@Slf4j
public class AdminBootstrap {

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final String bootstrapEmail;
  private final String bootstrapPassword;

  /** Binds the repositories and the env-driven bootstrap email/password. */
  public AdminBootstrap(
      AppUserRepository appUserRepository,
      PasswordEncoder passwordEncoder,
      @Value("${app.auth.admin.bootstrap-email:}") String bootstrapEmail,
      @Value("${app.auth.admin.bootstrap-password:}") String bootstrapPassword) {
    this.appUserRepository = appUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.bootstrapEmail = bootstrapEmail;
    this.bootstrapPassword = bootstrapPassword;
  }

  /** Runs once on startup; seeds or skips the admin per the class contract. */
  @PostConstruct
  void init() {
    if (isBlank(bootstrapEmail) && isBlank(bootstrapPassword)) {
      return;
    }

    boolean adminExists = appUserRepository.existsByRole(Role.ADMIN);
    if (adminExists) {
      if (!isBlank(bootstrapPassword)) {
        log.warn(
            "An ADMIN already exists but ADMIN_BOOTSTRAP_PASSWORD is still set;"
                + " remove it from the environment.");
      }
      return;
    }

    if (isBlank(bootstrapEmail) || isBlank(bootstrapPassword)) {
      log.warn(
          "Admin bootstrap skipped: both ADMIN_BOOTSTRAP_EMAIL and"
              + " ADMIN_BOOTSTRAP_PASSWORD are required.");
      return;
    }

    AppUserEntity admin =
        AppUserEntity.builder()
            .email(bootstrapEmail)
            .role(Role.ADMIN)
            .passwordHash(passwordEncoder.encode(bootstrapPassword))
            .webauthnUserHandle(UUID.randomUUID())
            .status(UserStatus.ACTIVE)
            .build();
    appUserRepository.insert(admin);
    log.info("Seeded bootstrap ADMIN user: {}", bootstrapEmail);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
