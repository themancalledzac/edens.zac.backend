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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClientGalleryAuthServiceTest {

  @Mock private CollectionRepository collectionRepository;

  @InjectMocks private ClientGalleryAuthService clientGalleryAuthService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(clientGalleryAuthService, "accessTokenSecret", "test-secret-key");
  }

  @Nested
  class ValidateClientGalleryAccess {

    @Test
    void validateClientGalleryAccess_noPasswordSet_returnsTrue() {
      // Arrange
      CollectionEntity collection =
          CollectionEntity.builder().id(1L).slug("gallery").passwordHash(null).build();
      when(collectionRepository.findBySlug("gallery")).thenReturn(Optional.of(collection));

      // Act
      boolean result = clientGalleryAuthService.validateClientGalleryAccess("gallery", null);

      // Assert
      assertThat(result).isTrue();
    }

    @Test
    void validateClientGalleryAccess_correctPassword_returnsTrue() {
      // Arrange
      String password = "secret123";
      String hash = CollectionProcessingUtil.hashPassword(password);
      CollectionEntity collection =
          CollectionEntity.builder().id(1L).slug("gallery").passwordHash(hash).build();
      when(collectionRepository.findBySlug("gallery")).thenReturn(Optional.of(collection));

      // Act
      boolean result = clientGalleryAuthService.validateClientGalleryAccess("gallery", password);

      // Assert
      assertThat(result).isTrue();
    }

    @Test
    void validateClientGalleryAccess_wrongPassword_returnsFalse() {
      // Arrange
      String hash = CollectionProcessingUtil.hashPassword("correct-password");
      CollectionEntity collection =
          CollectionEntity.builder().id(1L).slug("gallery").passwordHash(hash).build();
      when(collectionRepository.findBySlug("gallery")).thenReturn(Optional.of(collection));

      // Act
      boolean result =
          clientGalleryAuthService.validateClientGalleryAccess("gallery", "wrong-password");

      // Assert
      assertThat(result).isFalse();
    }

    @Test
    void validateClientGalleryAccess_nullPassword_returnsFalse() {
      // Arrange
      String hash = CollectionProcessingUtil.hashPassword("some-password");
      CollectionEntity collection =
          CollectionEntity.builder().id(1L).slug("gallery").passwordHash(hash).build();
      when(collectionRepository.findBySlug("gallery")).thenReturn(Optional.of(collection));

      // Act
      boolean result = clientGalleryAuthService.validateClientGalleryAccess("gallery", null);

      // Assert
      assertThat(result).isFalse();
    }

    @Test
    void validateClientGalleryAccess_nonExistentSlug_throwsException() {
      // Arrange
      when(collectionRepository.findBySlug("missing")).thenReturn(Optional.empty());

      // Act & Assert
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
      // Act
      boolean result = clientGalleryAuthService.validateAccessToken("gallery", null);

      // Assert
      assertThat(result).isFalse();
    }

    @Test
    void validateAccessToken_tokenWithoutPipe_returnsFalse() {
      // Act
      boolean result = clientGalleryAuthService.validateAccessToken("gallery", "no-pipe-character");

      // Assert
      assertThat(result).isFalse();
    }

    @Test
    void validateAccessToken_validToken_returnsTrue() {
      // Arrange
      String hash = CollectionProcessingUtil.hashPassword("secret123");
      CollectionEntity collection =
          CollectionEntity.builder().id(1L).slug("gallery").passwordHash(hash).build();
      when(collectionRepository.findBySlug("gallery")).thenReturn(Optional.of(collection));

      // Generate a real token via the service
      String token = clientGalleryAuthService.generateAccessToken("gallery");

      // Act
      boolean result = clientGalleryAuthService.validateAccessToken("gallery", token);

      // Assert
      assertThat(result).isTrue();
    }

    @Test
    void validateAccessToken_invalidToken_returnsFalse() {
      // Arrange
      String hash = CollectionProcessingUtil.hashPassword("secret123");
      CollectionEntity collection =
          CollectionEntity.builder().id(1L).slug("gallery").passwordHash(hash).build();
      when(collectionRepository.findBySlug("gallery")).thenReturn(Optional.of(collection));

      // Act
      boolean result =
          clientGalleryAuthService.validateAccessToken("gallery", "wrong-hmac|9999999999");

      // Assert
      assertThat(result).isFalse();
    }

    @Test
    void validateAccessToken_noPasswordOnCollection_returnsTrue() {
      // Arrange
      CollectionEntity collection =
          CollectionEntity.builder().id(1L).slug("gallery").passwordHash(null).build();
      when(collectionRepository.findBySlug("gallery")).thenReturn(Optional.of(collection));

      // Act - any token with a pipe is accepted for non-protected collections
      boolean result = clientGalleryAuthService.validateAccessToken("gallery", "any|token");

      // Assert
      assertThat(result).isTrue();
    }

    @Test
    void validateAccessToken_nonExistentSlug_returnsFalse() {
      // Arrange
      when(collectionRepository.findBySlug("missing")).thenReturn(Optional.empty());

      // Act
      boolean result = clientGalleryAuthService.validateAccessToken("missing", "some|token");

      // Assert
      assertThat(result).isFalse();
    }

    @Test
    void validateAccessToken_expiredToken_returnsFalse() {
      // Arrange
      String hash = CollectionProcessingUtil.hashPassword("secret123");
      CollectionEntity collection =
          CollectionEntity.builder().id(1L).slug("gallery").passwordHash(hash).build();
      when(collectionRepository.findBySlug("gallery")).thenReturn(Optional.of(collection));

      // Act - use an expired timestamp (epoch 0)
      boolean result = clientGalleryAuthService.validateAccessToken("gallery", "some-hmac|0");

      // Assert
      assertThat(result).isFalse();
    }
  }
}
