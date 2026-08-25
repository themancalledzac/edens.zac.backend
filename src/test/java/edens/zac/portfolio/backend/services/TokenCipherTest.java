package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenCipherTest {

  private final TokenCipher cipher = new TokenCipher("a-test-secret");

  @Test
  void encryptThenDecryptRoundTrips() {
    String token = TokenUtil.generateRawToken();

    String stored = cipher.encrypt(token);

    assertThat(stored).isNotNull().isNotEqualTo(token);
    assertThat(cipher.decrypt(stored)).isEqualTo(token);
  }

  @Test
  void theSameTokenEncryptsDifferentlyEachTime() {
    String token = "the-same-token";

    // A random IV per call. This is exactly why the ciphertext can never serve as a lookup key --
    // that job stays with the SHA-256 hash, which is what token_hash is still for.
    assertThat(cipher.encrypt(token)).isNotEqualTo(cipher.encrypt(token));
  }

  @Test
  void aDifferentSecretCannotDecryptIt() {
    String stored = cipher.encrypt("secret-token");

    // The key lives in configuration, not the database, which is what makes a bare DB dump
    // useless -- the property hashing used to provide.
    assertThat(new TokenCipher("a-different-secret").decrypt(stored)).isNull();
  }

  @Test
  void tamperedCiphertextDecryptsToNullRatherThanGarbage() {
    String stored = cipher.encrypt("secret-token");
    String tampered = stored.substring(0, stored.length() - 2) + (stored.endsWith("A") ? "B" : "A");

    // GCM authenticates, so a modified value fails outright instead of yielding a wrong token.
    assertThat(cipher.decrypt(tampered)).isNull();
  }

  @Test
  void unrecoverableInputsAllReadAsNull() {
    // Every one of these means "no link to show, offer a reset" rather than an error.
    assertThat(cipher.decrypt(null)).isNull();
    assertThat(cipher.decrypt("")).isNull();
    assertThat(cipher.decrypt("not-base64!!")).isNull();
    assertThat(cipher.decrypt("c2hvcnQ")).isNull();
  }

  @Test
  void nullAndBlankEncryptToNull() {
    assertThat(cipher.encrypt(null)).isNull();
    assertThat(cipher.encrypt("  ")).isNull();
  }
}
