package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.UserSavedImageRepository;
import edens.zac.portfolio.backend.entity.UserSavedImageEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserSavesServiceTest {

  @Mock private UserSavedImageRepository userSavedImageRepository;
  @Mock private ContentRepository contentRepository;
  @Mock private ContentModelConverter contentModelConverter;

  @InjectMocks private UserSavesService service;

  @Test
  void addInsertsWhenImageIsVisibleToUser() {
    when(contentRepository.isImageVisibleToUser(42L, 7L)).thenReturn(true);

    service.add(7L, 42L);

    ArgumentCaptor<UserSavedImageEntity> captor =
        ArgumentCaptor.forClass(UserSavedImageEntity.class);
    verify(userSavedImageRepository).insert(captor.capture());
    UserSavedImageEntity inserted = captor.getValue();
    assertThat(inserted.getUserId()).isEqualTo(7L);
    assertThat(inserted.getImageId()).isEqualTo(42L);
  }

  @Test
  void addThrowsNotFoundWhenImageNotVisibleToUser() {
    when(contentRepository.isImageVisibleToUser(42L, 7L)).thenReturn(false);

    assertThatThrownBy(() -> service.add(7L, 42L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("42");

    // Not-visible (or nonexistent) image must never reach the insert.
    verify(userSavedImageRepository, never()).insert(any());
  }

  @Test
  void removeDeletesWithoutVisibilityCheck() {
    service.remove(7L, 42L);

    verify(userSavedImageRepository).deleteByUserIdAndImageId(7L, 42L);
    verify(contentRepository, never()).isImageVisibleToUser(any(), any());
  }
}
