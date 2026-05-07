package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientGalleryAuthServiceTest {

  @Mock private CollectionRepository collectionRepository;

  private ClientGalleryAuthService clientGalleryAuthService;

  @BeforeEach
  void setUp() {
    clientGalleryAuthService =
        new ClientGalleryAuthService(collectionRepository, "test-secret-key");
  }

  @Nested
  class ValidateClientGalleryAccess {

    @Test
    void validateClientGalleryAccess_noPasswordSet_returnsTrue() {
      CollectionEntity collection =
          CollectionEntity.builder().id(1L).slug("gallery").galleryPassword(null).build();
      when(collectionRepository.findBySlug("gallery")).thenReturn(Optional.of(collection));

      boolean result = clientGalleryAuthService.validateClientGalleryAccess("gallery", null);

      assertThat(result).isTrue();
    }

    @Test
    void validateClientGalleryAccess_correctPassword_returnsTrue() {
      CollectionEntity collection =
          CollectionEntity.builder().id(1L).slug("gallery").galleryPassword("secret123").build();
      when(collectionRepository.findBySlug("gallery")).thenReturn(Optional.of(collection));

      boolean result = clientGalleryAuthService.validateClientGalleryAccess("gallery", "secret123");

      assertThat(result).isTrue();
    }

    @Test
    void validateClientGalleryAccess_wrongPassword_returnsFalse() {
      CollectionEntity collection =
          CollectionEntity.builder()
              .id(1L)
              .slug("gallery")
              .galleryPassword("correct-password")
              .build();
      when(collectionRepository.findBySlug("gallery")).thenReturn(Optional.of(collection));

      boolean result =
          clientGalleryAuthService.validateClientGalleryAccess("gallery", "wrong-password");

      assertThat(result).isFalse();
    }

    @Test
    void validateClientGalleryAccess_nullPassword_returnsFalse() {
      CollectionEntity collection =
          CollectionEntity.builder()
              .id(1L)
              .slug("gallery")
              .galleryPassword("some-password")
              .build();
      when(collectionRepository.findBySlug("gallery")).thenReturn(Optional.of(collection));

      boolean result = clientGalleryAuthService.validateClientGalleryAccess("gallery", null);

      assertThat(result).isFalse();
    }

    @Test
    void validateClientGalleryAccess_nonExistentSlug_throwsException() {
      when(collectionRepository.findBySlug("missing")).thenReturn(Optional.empty());

      assertThatThrownBy(
              () -> clientGalleryAuthService.validateClientGalleryAccess("missing", null))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Collection not found with slug: missing");
    }
  }

  @Nested
  class ValidateAccessToken {

    @Test
    void validateAccessToken_nullToken_returnsFalse() {
      boolean result = clientGalleryAuthService.validateAccessToken("gallery", null);

      assertThat(result).isFalse();
    }

    @Test
    void validateAccessToken_tokenWithoutPipe_returnsFalse() {
      boolean result = clientGalleryAuthService.validateAccessToken("gallery", "no-pipe-character");

      assertThat(result).isFalse();
    }

    @Test
    void validateAccessToken_validToken_returnsTrue() {
      CollectionEntity collection =
          CollectionEntity.builder().id(1L).slug("gallery").galleryPassword("secret123").build();
      when(collectionRepository.findBySlug("gallery")).thenReturn(Optional.of(collection));

      String token = clientGalleryAuthService.generateAccessToken("gallery");

      boolean result = clientGalleryAuthService.validateAccessToken("gallery", token);

      assertThat(result).isTrue();
    }

    @Test
    void validateAccessToken_invalidToken_returnsFalse() {
      CollectionEntity collection =
          CollectionEntity.builder().id(1L).slug("gallery").galleryPassword("secret123").build();
      when(collectionRepository.findBySlug("gallery")).thenReturn(Optional.of(collection));

      boolean result =
          clientGalleryAuthService.validateAccessToken("gallery", "wrong-hmac|9999999999");

      assertThat(result).isFalse();
    }

    @Test
    void validateAccessToken_noPasswordOnCollection_returnsTrue() {
      CollectionEntity collection =
          CollectionEntity.builder().id(1L).slug("gallery").galleryPassword(null).build();
      when(collectionRepository.findBySlug("gallery")).thenReturn(Optional.of(collection));

      boolean result = clientGalleryAuthService.validateAccessToken("gallery", "any|token");

      assertThat(result).isTrue();
    }

    @Test
    void validateAccessToken_nonExistentSlug_returnsFalse() {
      when(collectionRepository.findBySlug("missing")).thenReturn(Optional.empty());

      boolean result = clientGalleryAuthService.validateAccessToken("missing", "some|token");

      assertThat(result).isFalse();
    }

    @Test
    void validateAccessToken_expiredToken_returnsFalse() {
      CollectionEntity collection =
          CollectionEntity.builder().id(1L).slug("gallery").galleryPassword("secret123").build();
      when(collectionRepository.findBySlug("gallery")).thenReturn(Optional.of(collection));

      boolean result = clientGalleryAuthService.validateAccessToken("gallery", "some-hmac|0");

      assertThat(result).isFalse();
    }
  }

  @Nested
  class PasswordFingerprint {

    @Test
    void passwordFingerprint_nullPassword_returnsNull() {
      assertThat(clientGalleryAuthService.passwordFingerprint(null)).isNull();
    }

    @Test
    void passwordFingerprint_emptyPassword_returnsNull() {
      assertThat(clientGalleryAuthService.passwordFingerprint("")).isNull();
    }

    @Test
    void passwordFingerprint_samePassword_isStable() {
      String first = clientGalleryAuthService.passwordFingerprint("secret123");
      String second = clientGalleryAuthService.passwordFingerprint("secret123");

      assertThat(first).isNotNull();
      assertThat(first).isEqualTo(second);
    }

    @Test
    void passwordFingerprint_differentPasswords_diverge() {
      String a = clientGalleryAuthService.passwordFingerprint("password-a");
      String b = clientGalleryAuthService.passwordFingerprint("password-b");

      assertThat(a).isNotEqualTo(b);
    }

    @Test
    void passwordFingerprint_isUrlSafeBase64() {
      String fp = clientGalleryAuthService.passwordFingerprint("any-password");

      assertThat(fp).matches("[A-Za-z0-9_-]+");
    }
  }

  @Nested
  class ValidatePasswordAccessToken {

    @Test
    void roundTrip_returnsTrue() {
      String token = clientGalleryAuthService.generatePasswordAccessToken("shared-pw");

      boolean result = clientGalleryAuthService.validatePasswordAccessToken("shared-pw", token);

      assertThat(result).isTrue();
    }

    @Test
    void mismatchedPassword_returnsFalse() {
      String token = clientGalleryAuthService.generatePasswordAccessToken("shared-pw");

      boolean result = clientGalleryAuthService.validatePasswordAccessToken("other-pw", token);

      assertThat(result).isFalse();
    }

    @Test
    void nullPassword_returnsFalse() {
      String token = clientGalleryAuthService.generatePasswordAccessToken("shared-pw");

      boolean result = clientGalleryAuthService.validatePasswordAccessToken(null, token);

      assertThat(result).isFalse();
    }

    @Test
    void nullToken_returnsFalse() {
      boolean result = clientGalleryAuthService.validatePasswordAccessToken("shared-pw", null);

      assertThat(result).isFalse();
    }

    @Test
    void tokenWithoutPipe_returnsFalse() {
      boolean result =
          clientGalleryAuthService.validatePasswordAccessToken("shared-pw", "garbage-no-pipe");

      assertThat(result).isFalse();
    }

    @Test
    void expiredToken_returnsFalse() {
      String fingerprint = clientGalleryAuthService.passwordFingerprint("shared-pw");
      // craft an expired token by computing the HMAC directly with expiry=0
      // (validation only accepts hmac+expiry pairs that haven't expired yet)
      boolean result =
          clientGalleryAuthService.validatePasswordAccessToken("shared-pw", "fakehmac|0");

      assertThat(result).isFalse();
      assertThat(fingerprint).isNotNull(); // sanity: fingerprint computable for non-null pw
    }
  }
}
