package edens.zac.portfolio.backend.config;

import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.WebAuthnCredentialRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.WebAuthnCredentialEntity;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Component;

/**
 * Adapts Spring Security's {@link UserCredentialRepository} onto {@code webauthn_credential} (via
 * the JDBC {@link WebAuthnCredentialRepository}) and {@code app_user} (to map our {@code user_id}
 * back to the WebAuthn user handle).
 *
 * <p>{@link #save(CredentialRecord)} handles both registration (insert) and assertion sign-count
 * bumps (update). {@link #delete(Bytes)} is a no-op (credential management is an out-of-band
 * concern). The WebAuthn user handle is {@code webauthn_user_handle} encoded as UTF-8 bytes; an id
 * that is not a valid UUID string (e.g. the random handle the operations engine mints for an
 * unknown username while building login options) resolves to an empty credential list rather than
 * throwing — this keeps login anti-enumeration intact.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JdbcUserCredentialRepository implements UserCredentialRepository {

  private final WebAuthnCredentialRepository credentialRepository;
  private final AppUserRepository appUserRepository;

  private CredentialRecord toRecord(WebAuthnCredentialEntity entity) {
    AppUserEntity user =
        appUserRepository
            .findById(entity.getUserId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Credential references missing app_user: " + entity.getUserId()));
    Bytes userHandle =
        new Bytes(user.getWebauthnUserHandle().toString().getBytes(StandardCharsets.UTF_8));
    return ImmutableCredentialRecord.builder()
        .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
        .credentialId(new Bytes(entity.getCredentialId()))
        .userEntityUserId(userHandle)
        .publicKey(new ImmutablePublicKeyCose(entity.getPublicKey()))
        .signatureCount(entity.getSignCount())
        .build();
  }

  @Override
  public void save(CredentialRecord record) {
    byte[] credId = record.getCredentialId().getBytes();
    UUID handle =
        JdbcPublicKeyCredentialUserEntityRepository.parseHandle(record.getUserEntityUserId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "WebAuthn credential has no valid user handle: "
                            + record.getUserEntityUserId()));
    AppUserEntity user =
        appUserRepository
            .findByWebauthnUserHandle(handle)
            .orElseThrow(
                () -> new IllegalStateException("No app_user for WebAuthn handle: " + handle));

    credentialRepository
        .findByCredentialId(credId)
        .ifPresentOrElse(
            existing ->
                credentialRepository.updateSignCountAndLastUsed(
                    existing.getId(),
                    record.getSignatureCount(),
                    LocalDateTime.now(ZoneOffset.UTC)),
            () ->
                credentialRepository.insert(
                    WebAuthnCredentialEntity.builder()
                        .userId(user.getId())
                        .credentialId(credId)
                        .publicKey(record.getPublicKey().getBytes())
                        .signCount(record.getSignatureCount())
                        .label(record.getLabel())
                        .build()));
  }

  @Override
  public void delete(Bytes credentialId) {
    log.debug("WebAuthn credential delete() ignored");
  }

  @Override
  public CredentialRecord findByCredentialId(Bytes credentialId) {
    return credentialRepository
        .findByCredentialId(credentialId.getBytes())
        .map(this::toRecord)
        .orElse(null);
  }

  @Override
  public List<CredentialRecord> findByUserId(Bytes userId) {
    return JdbcPublicKeyCredentialUserEntityRepository.parseHandle(userId)
        .flatMap(appUserRepository::findByWebauthnUserHandle)
        .map(user -> credentialRepository.findByUserId(user.getId()))
        .orElseGet(List::of)
        .stream()
        .map(this::toRecord)
        .toList();
  }
}
