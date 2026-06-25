package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.UserSelectRepository;
import edens.zac.portfolio.backend.entity.UserSelectEntity;
import edens.zac.portfolio.backend.model.UserSelectGroup;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class UserSelectsServiceTest {

  @Mock private UserSelectRepository userSelectRepository;
  @Mock private GalleryAccessService galleryAccessService;

  @InjectMocks private UserSelectsService service;

  @Test
  void addInsertsWhenUserHoldsAGrant() {
    when(galleryAccessService.hasGrant(7L, 3L)).thenReturn(true);

    service.add(7L, false, 3L, 42L);

    ArgumentCaptor<UserSelectEntity> captor = ArgumentCaptor.forClass(UserSelectEntity.class);
    verify(userSelectRepository).insert(captor.capture());
    UserSelectEntity inserted = captor.getValue();
    assertThat(inserted.getUserId()).isEqualTo(7L);
    assertThat(inserted.getContentId()).isEqualTo(42L);
    assertThat(inserted.getCollectionId()).isEqualTo(3L);
  }

  @Test
  void addInsertsForAdminWithoutAGrant() {
    service.add(7L, true, 3L, 42L);

    verify(galleryAccessService, never())
        .hasGrant(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    ArgumentCaptor<UserSelectEntity> captor = ArgumentCaptor.forClass(UserSelectEntity.class);
    verify(userSelectRepository).insert(captor.capture());
    assertThat(captor.getValue().getContentId()).isEqualTo(42L);
  }

  @Test
  void addDeniedWhenNonAdminLacksGrant() {
    when(galleryAccessService.hasGrant(7L, 3L)).thenReturn(false);

    assertThatThrownBy(() -> service.add(7L, false, 3L, 42L))
        .isInstanceOf(AccessDeniedException.class);

    verify(userSelectRepository, never()).insert(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void removeDeletesRegardlessOfGrant() {
    service.remove(7L, 42L);

    verify(userSelectRepository).deleteByUserIdAndContentId(7L, 42L);
  }

  @Test
  void listIdsRequiresGrantForNonAdmin() {
    when(galleryAccessService.hasGrant(7L, 3L)).thenReturn(false);

    assertThatThrownBy(() -> service.listSelectIds(7L, false, 3L))
        .isInstanceOf(AccessDeniedException.class);

    verify(userSelectRepository, never())
        .findContentIdsByUserIdAndCollectionId(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void listIdsReturnsForGrantedUser() {
    when(galleryAccessService.hasGrant(7L, 3L)).thenReturn(true);
    when(userSelectRepository.findContentIdsByUserIdAndCollectionId(7L, 3L))
        .thenReturn(List.of(42L, 43L));

    assertThat(service.listSelectIds(7L, false, 3L)).containsExactly(42L, 43L);
  }

  @Test
  void listAllGroupsByCollectionPreservingOrder() {
    when(userSelectRepository.findByUserId(7L))
        .thenReturn(
            List.of(
                UserSelectEntity.builder().userId(7L).collectionId(3L).contentId(42L).build(),
                UserSelectEntity.builder().userId(7L).collectionId(3L).contentId(43L).build(),
                UserSelectEntity.builder().userId(7L).collectionId(9L).contentId(50L).build()));

    List<UserSelectGroup> groups = service.listAll(7L);

    assertThat(groups).hasSize(2);
    assertThat(groups.get(0).getCollectionId()).isEqualTo(3L);
    assertThat(groups.get(0).getContentIds()).containsExactly(42L, 43L);
    assertThat(groups.get(1).getCollectionId()).isEqualTo(9L);
    assertThat(groups.get(1).getContentIds()).containsExactly(50L);
  }
}
