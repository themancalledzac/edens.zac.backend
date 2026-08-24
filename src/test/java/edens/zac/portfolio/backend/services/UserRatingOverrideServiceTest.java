package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.UserRatingOverrideRepository;
import edens.zac.portfolio.backend.entity.UserRatingOverrideEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class UserRatingOverrideServiceTest {

  private static final Long USER_ID = 1L;
  private static final AuthPrincipal USER = AuthPrincipal.client(USER_ID, "u@example.com", true);
  private static final Long COLLECTION = 7L;
  private static final Long CONTENT = 42L;

  @Mock private UserRatingOverrideRepository overrideRepository;
  @Mock private CollectionAccessService collectionAccessService;

  private UserRatingOverrideService service() {
    return new UserRatingOverrideService(overrideRepository, collectionAccessService);
  }

  @Test
  void upsertPersistsWhenClientMembership() {
    when(collectionAccessService.isClient(USER, COLLECTION)).thenReturn(true);

    service().upsert(USER, COLLECTION, CONTENT, 4);

    ArgumentCaptor<UserRatingOverrideEntity> captor =
        ArgumentCaptor.forClass(UserRatingOverrideEntity.class);
    verify(overrideRepository).upsert(captor.capture());
    UserRatingOverrideEntity saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getContentId()).isEqualTo(CONTENT);
    assertThat(saved.getCollectionId()).isEqualTo(COLLECTION);
    assertThat(saved.getRating()).isEqualTo(4);
  }

  @Test
  void upsertRejectedWhenNoClientMembership() {
    when(collectionAccessService.isClient(USER, COLLECTION)).thenReturn(false);

    // AccessDeniedException, not SecurityException: GlobalExceptionHandler maps this one to 403,
    // which is what let the controller drop its try-catch.
    assertThatThrownBy(() -> service().upsert(USER, COLLECTION, CONTENT, 4))
        .isInstanceOf(AccessDeniedException.class);

    verify(overrideRepository, never()).upsert(any());
  }

  @Test
  void upsertRejectedWhenRatingOutOfRange() {
    assertThatThrownBy(() -> service().upsert(USER, COLLECTION, CONTENT, 6))
        .isInstanceOf(IllegalArgumentException.class);

    verify(overrideRepository, never()).upsert(any());
  }

  @Test
  void listReturnsScopedOverrides() {
    UserRatingOverrideEntity row =
        UserRatingOverrideEntity.builder()
            .userId(USER_ID)
            .contentId(CONTENT)
            .collectionId(COLLECTION)
            .rating(3)
            .build();
    when(overrideRepository.findByUserIdAndCollectionId(USER_ID, COLLECTION))
        .thenReturn(List.of(row));

    assertThat(service().listForUserInCollection(USER_ID, COLLECTION)).containsExactly(row);
  }
}
