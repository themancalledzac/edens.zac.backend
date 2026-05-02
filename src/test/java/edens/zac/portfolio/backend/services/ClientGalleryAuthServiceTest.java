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
}
