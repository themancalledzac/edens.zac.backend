package edens.zac.portfolio.backend.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reversible at-rest protection for values the owner must be able to read back -- currently just
 * the share-link token.
 *
 * <p>This exists alongside {@link TokenUtil}, not instead of it, and the split is the point.
 * Hashing is correct for a credential the server only ever needs to *recognise* (an invite token, a
 * session token): the raw value is unrecoverable, so a database dump yields nothing. A share link
 * is different -- its owner needs to see it again to send it to a second person -- so recognition
 * alone is not enough.
 *
 * <p>Encryption keeps the property that matters. The key is derived from {@code
 * app.access-token.secret}, which lives in configuration rather than the database, so a dump on its
 * own is still useless. A share link therefore stays as durable as its owner expects while never
 * sitting in the database in the clear.
 *
 * <p>AES-256-GCM, so the ciphertext is authenticated: tampering fails to decrypt rather than
 * silently yielding a wrong token. The 12-byte IV is randomly generated per call and prefixed to
 * the ciphertext, which is why two encryptions of the same token differ -- and why this value can
 * never be used as a lookup key. That job stays with the SHA-256 hash.
 */
@Component
@Slf4j
public class TokenCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH_BYTES = 12;
  private static final int TAG_LENGTH_BITS = 128;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final SecretKeySpec key;

  TokenCipher(@Value("${app.access-token.secret}") String secret) {
    // SHA-256 the configured secret to get exactly 256 bits of key material, so the property can
    // be any length without AES rejecting it.
    this.key = new SecretKeySpec(sha256(secret), "AES");
  }

  /**
   * Encrypt a raw token for storage.
   *
   * @param rawToken the value to protect; null or blank returns null
   * @return base64url of {@code IV || ciphertext || tag}, or null
   */
  public String encrypt(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return null;
    }
    try {
      byte[] iv = new byte[IV_LENGTH_BYTES];
      SECURE_RANDOM.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] encrypted = cipher.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));

      byte[] combined = new byte[iv.length + encrypted.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
    } catch (Exception e) {
      throw new IllegalStateException("Token encryption failed", e);
    }
  }

  /**
   * Decrypt a stored token.
   *
   * <p>Returns null rather than throwing on any failure -- absent value, truncated input, wrong
   * key, tampered ciphertext. Callers treat that as "no recoverable link, offer a reset", which is
   * the same handling a pre-V58 row needs, so a rotated secret degrades to the same recoverable
   * state instead of breaking the owner's page.
   *
   * @param stored the value produced by {@link #encrypt}
   * @return the raw token, or null when it cannot be recovered
   */
  public String decrypt(String stored) {
    if (stored == null || stored.isBlank()) {
      return null;
    }
    try {
      byte[] combined = Base64.getUrlDecoder().decode(stored);
      if (combined.length <= IV_LENGTH_BYTES) {
        return null;
      }
      byte[] iv = new byte[IV_LENGTH_BYTES];
      System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
      byte[] encrypted = new byte[combined.length - IV_LENGTH_BYTES];
      System.arraycopy(combined, IV_LENGTH_BYTES, encrypted, 0, encrypted.length);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (Exception e) {
      // Deliberately not logging the value or the exception detail -- this is a bearer credential.
      log.warn("Could not decrypt a stored token; treating it as unrecoverable");
      return null;
    }
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
