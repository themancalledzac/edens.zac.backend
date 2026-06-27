package edens.zac.portfolio.backend.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Shared utility for CSPRNG token generation and SHA-256 hashing. Raw tokens are generated as
 * 256-bit base64url strings; only their hash is stored so a DB leak yields no usable token.
 */
final class TokenUtil {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private TokenUtil() {}

  /**
   * Generate a 256-bit CSPRNG token encoded as base64url without padding.
   *
   * @return raw token string safe for URL embedding
   */
  static String generateRawToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Compute the SHA-256 hex digest of a UTF-8 string.
   *
   * @param value the input string
   * @return lowercase hex SHA-256 digest
   */
  static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
