package edens.zac.portfolio.backend.config;

import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.stereotype.Component;

/**
 * Adapts Spring Security's {@link PublicKeyCredentialUserEntityRepository} onto {@code app_user}.
 * The WebAuthn user handle is {@code app_user.webauthn_user_handle} (a UUID) encoded as UTF-8 bytes
 * so the WebAuthn {@link Bytes} id round-trips to the UUID; the user {@code name} is the email (no
 * separate username concept — admin/client identity is the email).
 *
 * <p>{@code app_user} rows are provisioned out-of-band (admin invite flow / client onboarding), so
 * {@link #save(PublicKeyCredentialUserEntity)} and {@link #delete(Bytes)} are no-ops here, but the
 * SPI requires the methods. The operations engine calls {@code save} for an unknown username when
 * minting login options; the no-op (combined with the credential-less allow-list) keeps login
 * anti-enumeration intact without creating a row.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JdbcPublicKeyCredentialUserEntityRepository
    implements PublicKeyCredentialUserEntityRepository {

  private final AppUserRepository appUserRepository;

  private static PublicKeyCredentialUserEntity toEntity(AppUserEntity user) {
    return ImmutablePublicKeyCredentialUserEntity.builder()
        .id(new Bytes(user.getWebauthnUserHandle().toString().getBytes(StandardCharsets.UTF_8)))
        .name(user.getEmail())
        .displayName(user.getName() != null ? user.getName() : user.getEmail())
        .build();
  }

  @Override
  public PublicKeyCredentialUserEntity findById(Bytes id) {
    return parseHandle(id)
        .flatMap(appUserRepository::findByWebauthnUserHandle)
        .map(JdbcPublicKeyCredentialUserEntityRepository::toEntity)
        .orElse(null);
  }

  @Override
  public PublicKeyCredentialUserEntity findByUsername(String username) {
    return appUserRepository
        .findByEmail(username)
        .map(JdbcPublicKeyCredentialUserEntityRepository::toEntity)
        .orElse(null);
  }

  @Override
  public void save(PublicKeyCredentialUserEntity userEntity) {
    // No-op: app_user rows are provisioned by the admin invite flow, not by the
    // WebAuthn ceremony. The user must already exist before registration starts. The operations
    // engine also invokes save() for an unknown username while minting login options; we do not
    // persist here (anti-enumeration: no account is created from a login attempt).
    Optional.ofNullable(userEntity)
        .ifPresent(
            u -> log.debug("WebAuthn save() ignored for user handle (provisioned out-of-band)"));
  }

  @Override
  public void delete(Bytes id) {
    // No-op: WebAuthn user-entity deletion is an out-of-band credential-management concern.
    log.debug("WebAuthn user-entity delete() ignored");
  }

  /**
   * Decode a WebAuthn {@link Bytes} id back to our {@code webauthn_user_handle} UUID. Returns empty
   * when the bytes are not a valid UUID string (e.g. the random handle the operations engine mints
   * for an unknown username during login-options) so callers resolve to "not found" rather than
   * throwing — this preserves login anti-enumeration.
   *
   * @param id the WebAuthn user-handle bytes
   * @return the decoded UUID, or empty if the bytes are not a UUID string
   */
  static Optional<UUID> parseHandle(Bytes id) {
    if (id == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(new String(id.getBytes(), StandardCharsets.UTF_8)));
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
  }
}
