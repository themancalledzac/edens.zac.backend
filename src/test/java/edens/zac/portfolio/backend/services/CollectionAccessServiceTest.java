package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.dao.RoleRepository.EffectiveGrant;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.types.AccessLevel;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CollectionAccessServiceTest {

  @Mock private RoleRepository roleRepository;
  @Mock private ShareLinkService shareLinkService;
  @InjectMocks private CollectionAccessService service;

  @Test
  void canViewResolvesThroughEffectiveLevel() {
    // S-6. This used to call roleRepository.canView directly, which is why neither the admin
    // sentinel nor the share branch reached it. GENERAL is the floor of the ladder, so for a
    // session principal "at least GENERAL" and "holds any grant" are the same question.
    AuthPrincipal user = AuthPrincipal.client(1L, "c@b.com", true);
    when(roleRepository.highestLevel(1L, 9L)).thenReturn(Optional.of(AccessLevel.GENERAL));
    assertThat(service.canView(user, 9L)).isTrue();

    when(roleRepository.highestLevel(1L, 10L)).thenReturn(Optional.empty());
    assertThat(service.canView(user, 10L)).isFalse();
  }

  @Test
  void isClientResolvesThroughEffectiveLevelAndNeedsClientOrHigher() {
    AuthPrincipal user = AuthPrincipal.client(1L, "c@b.com", true);
    when(roleRepository.highestLevel(1L, 9L)).thenReturn(Optional.of(AccessLevel.GENERAL));
    assertThat(service.isClient(user, 9L)).isFalse();

    when(roleRepository.highestLevel(1L, 10L)).thenReturn(Optional.of(AccessLevel.CLIENT));
    assertThat(service.isClient(user, 10L)).isTrue();
  }

  @Test
  void adminSatisfiesCanViewAndIsClientWithNoGrantAtAll() {
    // Working rule 20: an admin is the owner. Before S-6 both of these were false for an admin
    // holding no role membership, which is what sent them to a password prompt and a 401.
    AuthPrincipal admin = new AuthPrincipal(1L, "a@b.com", true, true);
    assertThat(service.canView(admin, 9L)).isTrue();
    assertThat(service.isClient(admin, 9L)).isTrue();
    verifyNoInteractions(roleRepository);
  }

  @Test
  void shareHolderCanViewButNeverCountsAsClient() {
    // The GENERAL ceiling is what makes routing isClient through effectiveLevel safe: a link
    // holder gains nothing from it. canView does change for a share, which is why the two
    // gallery-password gates screen with AuthPrincipal.isRealUser before asking.
    AuthPrincipal flyby = AuthPrincipal.flyby(3L);
    when(shareLinkService.levelFor(3L, 9L)).thenReturn(Optional.of(AccessLevel.GENERAL));
    assertThat(service.canView(flyby, 9L)).isTrue();
    assertThat(service.isClient(flyby, 9L)).isFalse();
    verifyNoInteractions(roleRepository);
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

  @Test
  void effectiveLevelReturnsAdminSentinelForAdminRegardlessOfGrants() {
    AuthPrincipal admin = new AuthPrincipal(1L, "a@b.com", true, true);
    assertThat(service.effectiveLevel(admin, 5L)).contains(AccessLevel.ADMIN);
    verifyNoInteractions(roleRepository);
  }

  @Test
  void effectiveLevelReturnsHighestStoredLevelForNonAdmin() {
    AuthPrincipal user = AuthPrincipal.client(7L, "c@b.com", true);
    when(roleRepository.highestLevel(7L, 5L)).thenReturn(Optional.of(AccessLevel.COLLABORATOR));
    assertThat(service.effectiveLevel(user, 5L)).contains(AccessLevel.COLLABORATOR);
  }

  @Test
  void effectiveLevelIsEmptyForNoGrantAndForNullPrincipal() {
    AuthPrincipal user = AuthPrincipal.client(7L, "c@b.com", true);
    when(roleRepository.highestLevel(7L, 5L)).thenReturn(Optional.empty());
    assertThat(service.effectiveLevel(user, 5L)).isEmpty();
    assertThat(service.effectiveLevel(null, 5L)).isEmpty();
  }

  @Test
  void effectiveLevelForShareLinkIsGeneralInScopeAndEmptyOutside() {
    AuthPrincipal flyby = AuthPrincipal.flyby(3L);
    when(shareLinkService.levelFor(3L, 5L)).thenReturn(Optional.of(AccessLevel.GENERAL));
    when(shareLinkService.levelFor(3L, 6L)).thenReturn(Optional.empty());

    assertThat(service.effectiveLevel(flyby, 5L)).contains(AccessLevel.GENERAL);
    assertThat(service.effectiveLevel(flyby, 6L)).isEmpty();
    // Resolution never reaches the role tables: a link holder borrows no grants.
    verifyNoInteractions(roleRepository);
  }

  @Test
  void shareLinkPrincipalCanNeverReachClientOrAbove() {
    AuthPrincipal flyby = AuthPrincipal.flyby(3L);
    when(shareLinkService.levelFor(3L, 5L)).thenReturn(Optional.of(AccessLevel.GENERAL));

    assertThat(service.hasAtLeast(flyby, 5L, AccessLevel.GENERAL)).isTrue();
    // Downloads, tagging and starring gate on CLIENT; /api/edit gates on COLLABORATOR.
    assertThat(service.hasAtLeast(flyby, 5L, AccessLevel.CLIENT)).isFalse();
    assertThat(service.hasAtLeast(flyby, 5L, AccessLevel.COLLABORATOR)).isFalse();
    assertThat(service.hasAtLeast(flyby, 5L, AccessLevel.ADMIN)).isFalse();
  }

  @Test
  void shareBranchIsEvaluatedAheadOfTheAdminSentinel() {
    // Defence in depth: AuthPrincipal.flyby pins isAdmin=false, but resolution must not depend on
    // that. A principal carrying a shareId resolves as a share even if isAdmin were somehow true.
    AuthPrincipal shareWithAdminFlag = new AuthPrincipal(null, null, true, false, 3L);
    when(shareLinkService.levelFor(3L, 5L)).thenReturn(Optional.empty());

    assertThat(service.effectiveLevel(shareWithAdminFlag, 5L)).isEmpty();
    assertThat(service.hasAtLeast(shareWithAdminFlag, 5L, AccessLevel.GENERAL)).isFalse();
  }

  @Test
  void hasAtLeastComparesRanksAndTreatsAbsenceAsDenial() {
    AuthPrincipal user = AuthPrincipal.client(7L, "c@b.com", true);
    when(roleRepository.highestLevel(7L, 5L)).thenReturn(Optional.of(AccessLevel.CLIENT));
    assertThat(service.hasAtLeast(user, 5L, AccessLevel.GENERAL)).isTrue();
    assertThat(service.hasAtLeast(user, 5L, AccessLevel.CLIENT)).isTrue();
    assertThat(service.hasAtLeast(user, 5L, AccessLevel.COLLABORATOR)).isFalse();

    when(roleRepository.highestLevel(7L, 6L)).thenReturn(Optional.empty());
    assertThat(service.hasAtLeast(user, 6L, AccessLevel.GENERAL)).isFalse();

    AuthPrincipal admin = new AuthPrincipal(1L, "a@b.com", true, true);
    assertThat(service.hasAtLeast(admin, 6L, AccessLevel.COLLABORATOR)).isTrue();
  }
}
