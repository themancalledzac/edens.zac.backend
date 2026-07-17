package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.dao.RoleRepository.EffectiveGrant;
import edens.zac.portfolio.backend.types.AccessLevel;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CollectionAccessServiceTest {

  @Mock private RoleRepository roleRepository;
  @InjectMocks private CollectionAccessService service;

  @Test
  void canViewDelegatesToRepository() {
    when(roleRepository.canView(1L, 9L)).thenReturn(true);
    assertThat(service.canView(1L, 9L)).isTrue();
  }

  @Test
  void isClientDelegatesToRepository() {
    when(roleRepository.isClient(1L, 9L)).thenReturn(false);
    assertThat(service.isClient(1L, 9L)).isFalse();
  }

  @Test
  void memberCollectionIdsForUserDelegatesToRepository() {
    when(roleRepository.memberCollectionIdsForUser(1L)).thenReturn(List.of(9L, 10L));
    assertThat(service.memberCollectionIdsForUser(1L)).containsExactly(9L, 10L);
  }

  @Test
  void effectiveGrantsDelegatesToRepository() {
    when(roleRepository.effectiveGrants(1L))
        .thenReturn(List.of(new EffectiveGrant(9L, AccessLevel.CLIENT)));
    assertThat(service.effectiveGrants(1L))
        .singleElement()
        .satisfies(g -> assertThat(g.level()).isEqualTo(AccessLevel.CLIENT));
  }
}
