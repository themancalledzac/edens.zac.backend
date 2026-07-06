package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.types.UserStatus;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Designates the SPECIFIC admin from environment variables on startup, so no admin identity lives
 * in git (the repo is public). Admin capability is the {@code users.is_admin} boolean — never a
 * mere session — so it survives independently of who is logged in and impersonation stays
 * non-admin. Idempotent: if the configured user already exists it flips {@code is_admin} on (a
 * no-op once already set); if the user is absent and a bootstrap password is provided it seeds a
 * new ACTIVE admin. Never throws on normal paths.
 */
@Component
@Slf4j
public class AdminBootstrap {

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final String bootstrapEmail;
  private final String bootstrapPassword;

  /** Binds the repository, password encoder, and the env-driven bootstrap email/password. */
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

  /** Runs once on startup (Flyway has already migrated by context init); designates or skips. */
  @PostConstruct
  void init() {
    if (isBlank(bootstrapEmail)) {
      return;
    }

    Optional<AppUserEntity> existing = appUserRepository.findByEmail(bootstrapEmail);
    if (existing.isPresent()) {
      AppUserEntity user = existing.get();
      if (!user.isAdmin()) {
        appUserRepository.setAdmin(user.getId(), true);
        log.info("Admin bootstrap: designated existing user as admin: {}", bootstrapEmail);
      } else if (!isBlank(bootstrapPassword)) {
        log.warn(
            "Admin bootstrap: {} is already admin but ADMIN_BOOTSTRAP_PASSWORD is still set;"
                + " remove it from the environment.",
            bootstrapEmail);
      }
      return;
    }

    if (isBlank(bootstrapPassword)) {
      log.warn(
          "Admin bootstrap: no user with the configured email and no bootstrap-password to seed"
              + " one; skipping.");
      return;
    }

    AppUserEntity admin =
        AppUserEntity.builder()
            .email(bootstrapEmail)
            .passwordHash(passwordEncoder.encode(bootstrapPassword))
            .webauthnUserHandle(UUID.randomUUID())
            .status(UserStatus.ACTIVE)
            .isAdmin(true)
            .build();
    Long id = appUserRepository.insert(admin);
    // insert() intentionally omits is_admin (DB default false), so flip it on explicitly. This
    // two-step sequence is not atomic, but it is intentionally fail-safe-by-restart: a crash
    // between the two calls leaves a non-admin row, which the existing.isPresent() &&
    // !user.isAdmin() branch above self-heals to admin on the next boot -- the failure direction
    // is always "not enough admin," never "accidental admin." Do not "fix" this into a single
    // statement without preserving that property.
    appUserRepository.setAdmin(id, true);
    log.info("Admin bootstrap: seeded new admin user: {}", bootstrapEmail);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
