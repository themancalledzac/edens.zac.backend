package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.UserRatingOverrideRepository;
import edens.zac.portfolio.backend.entity.UserRatingOverrideEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserRatingOverrideServiceTest {

  private static final Long USER = 1L;
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
    assertThat(saved.getUserId()).isEqualTo(USER);
    assertThat(saved.getContentId()).isEqualTo(CONTENT);
    assertThat(saved.getCollectionId()).isEqualTo(COLLECTION);
    assertThat(saved.getRating()).isEqualTo(4);
  }

  @Test
  void upsertRejectedWhenNoClientMembership() {
    when(collectionAccessService.isClient(USER, COLLECTION)).thenReturn(false);

    assertThatThrownBy(() -> service().upsert(USER, COLLECTION, CONTENT, 4))
        .isInstanceOf(SecurityException.class);

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
            .userId(USER)
            .contentId(CONTENT)
            .collectionId(COLLECTION)
            .rating(3)
            .build();
    when(overrideRepository.findByUserIdAndCollectionId(USER, COLLECTION)).thenReturn(List.of(row));

    assertThat(service().listForUserInCollection(USER, COLLECTION)).containsExactly(row);
  }
}
