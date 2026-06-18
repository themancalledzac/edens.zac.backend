package edens.zac.portfolio.backend.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A registered WebAuthn (passkey) credential belonging to an app_user. Mirrors webauthn_credential.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebAuthnCredentialEntity {

  private Long id;
  private Long userId;
  private byte[] credentialId;
  private byte[] publicKey;
  private long signCount;
  private String transports;
  private String label;
  private LocalDateTime createdAt;
  private LocalDateTime lastUsedAt;
}
