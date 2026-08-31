package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.entity.ShareLinkEntity;
import edens.zac.portfolio.backend.types.AccessLevel;
import edens.zac.portfolio.backend.types.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Exercises {@link ShareLinkService} against a real schema. The properties under test are the ones
 * the design leans on and that a mocked repository could not demonstrate: that a reset really does
 * orphan the old secret while keeping the owner's opt-ins, that scope is resolved live rather than
 * frozen at mint time, and -- most importantly -- that a link never reaches past its own allowlist
 * or above GENERAL.
 *
 * <p>The base class truncates only auth tables, so each test seeds uniquely named rows and scopes
 * its assertions to those ids.
 */
class ShareLinkServiceIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private ShareLinkService shareLinkService;
  @Autowired private RoleRepository roleRepository;
  @Autowired private JdbcTemplate jdbc;

  private Long seedUser() {
    String email = "share-" + UUID.randomUUID() + "@example.com";
    return jdbc.queryForObject(
        "INSERT INTO users (name, email, webauthn_user_handle, status) "
            + "VALUES (?, ?, gen_random_uuid(), 'ACTIVE') RETURNING id",
        Long.class,
        email,
        email);
  }

  private Long seedCollection() {
    return jdbc.queryForObject(
        "INSERT INTO collection (title, slug, visibility) "
            + "VALUES ('Gallery', ?, 'UNLISTED') RETURNING id",
        Long.class,
        "share-g-" + UUID.randomUUID());
  }

  private void tagCollection(Long collectionId, Long personId) {
    jdbc.update(
        "INSERT INTO collection_people (collection_id, person_id) VALUES (?, ?)",
        collectionId,
        personId);
  }

  private void setStatus(Long userId, UserStatus status) {
    jdbc.update("UPDATE users SET status = ? WHERE id = ?", status.name(), userId);
  }

  private int shareLinkRowCount(Long userId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM share_link WHERE user_id = ?", Integer.class, userId);
  }

  /**
   * S-16: disabling an account suspends its link. The row survives and the same URL works again on
   * re-enable, which is what separates this from revoking.
   */
  @Test
  void disablingTheOwnerSuspendsTheLinkAndReEnablingRestoresIt() {
    Long userId = seedUser();
    String raw = shareLinkService.mintOrRotate(userId);
    Long linkId = shareLinkService.resolveByRawToken(raw).orElseThrow().getId();

    setStatus(userId, UserStatus.DISABLED);

    assertThat(shareLinkService.resolveByRawToken(raw)).isEmpty();
    assertThat(shareLinkRowCount(userId)).isEqualTo(1);

    setStatus(userId, UserStatus.ACTIVE);

    assertThat(shareLinkService.resolveByRawToken(raw))
        .get()
        .extracting(ShareLinkEntity::getId)
        .isEqualTo(linkId);
  }

  /** Every status the owner's account can hold that is not ACTIVE stops the link resolving. */
  @ParameterizedTest
  @EnumSource(
      value = UserStatus.class,
      names = {"INVITED", "DISABLED", "PERSON"})
  void aNonActiveOwnerStopsTheLinkResolving(UserStatus status) {
    Long userId = seedUser();
    String raw = shareLinkService.mintOrRotate(userId);
    assertThat(shareLinkService.resolveByRawToken(raw)).isPresent();

    setStatus(userId, status);

    assertThat(shareLinkService.resolveByRawToken(raw)).isEmpty();
  }

  private void grant(Long userId, Long collectionId, AccessLevel level) {
    Long roleId = roleRepository.createRole("share-grant-" + UUID.randomUUID(), null);
    roleRepository.addMember(roleId, userId, null);
    roleRepository.setCollectionGrant(roleId, collectionId, level, null);
  }

  private Long shareIdFor(String rawToken) {
    return shareLinkService.resolveByRawToken(rawToken).orElseThrow().getId();
  }

  @Test
  void mintOrRotateThenResolveByRawTokenRoundTrips() {
    Long userId = seedUser();

    String raw = shareLinkService.mintOrRotate(userId);

    ShareLinkEntity link = shareLinkService.resolveByRawToken(raw).orElseThrow();
    assertThat(link.getUserId()).isEqualTo(userId);
    assertThat(link.getLevel()).isEqualTo(AccessLevel.GENERAL);
    assertThat(link.getCreatedAt()).isNotNull();
    // The raw token is the only carrier of the secret; the stored form must not be it.
    assertThat(link.getTokenHash()).isNotEqualTo(raw);
  }

  @Test
  void resolveByRawTokenIsEmptyForUnknownBlankAndNullTokens() {
    assertThat(shareLinkService.resolveByRawToken("no-such-token")).isEmpty();
    assertThat(shareLinkService.resolveByRawToken("")).isEmpty();
    assertThat(shareLinkService.resolveByRawToken(null)).isEmpty();
  }

  @Test
  void mintOrRotateKillsTheOldTokenAndKeepsTheSameRow() {
    Long userId = seedUser();
    String first = shareLinkService.mintOrRotate(userId);
    Long firstId = shareIdFor(first);

    String second = shareLinkService.mintOrRotate(userId);

    assertThat(second).isNotEqualTo(first);
    // The old link is dead -- this is the whole of "reset link".
    assertThat(shareLinkService.resolveByRawToken(first)).isEmpty();
    // ...but it is the same row, which is what lets opt-ins survive a reset.
    assertThat(shareIdFor(second)).isEqualTo(firstId);
    assertThat(shareLinkService.resolveByRawToken(second).orElseThrow().getRotatedAt()).isNotNull();
  }

  @Test
  void revealTokenReturnsTheSameLinkThatIsAlreadyInCirculation() {
    Long userId = seedUser();
    String raw = shareLinkService.mintOrRotate(userId);

    // The owner can send the same link to a second person weeks later without a reset, which is
    // what a hash-only design made impossible.
    assertThat(shareLinkService.revealToken(userId)).contains(raw);
    // And it still resolves -- reveal is a read, not a re-issue.
    assertThat(shareLinkService.resolveByRawToken(raw)).isPresent();
  }

  @Test
  void revealTokenFollowsARotationAndIsEmptyForAUserWithNoLink() {
    Long userId = seedUser();
    shareLinkService.mintOrRotate(userId);
    String second = shareLinkService.mintOrRotate(userId);

    assertThat(shareLinkService.revealToken(userId)).contains(second);
    assertThat(shareLinkService.revealToken(seedUser())).isEmpty();
  }

  @Test
  void resetPreservesTheOwnersOptInCollections() {
    Long userId = seedUser();
    Long granted = seedCollection();
    grant(userId, granted, AccessLevel.CLIENT);
    String first = shareLinkService.mintOrRotate(userId);
    Long shareId = shareIdFor(first);
    shareLinkService.addOptIn(shareId, granted);

    String second = shareLinkService.mintOrRotate(userId);

    assertThat(shareLinkService.optInCollectionIds(shareIdFor(second))).containsExactly(granted);
  }

  @Test
  void scopeIncludesTaggedInCollectionsWithoutAnyOptIn() {
    Long userId = seedUser();
    Long tagged = seedCollection();
    tagCollection(tagged, userId);
    Long shareId = shareIdFor(shareLinkService.mintOrRotate(userId));

    assertThat(shareLinkService.scopeCollectionIds(shareId)).contains(tagged);
  }

  @Test
  void scopeExcludesRoleGrantedCollectionsUntilExplicitlyOptedIn() {
    Long userId = seedUser();
    Long granted = seedCollection();
    grant(userId, granted, AccessLevel.CLIENT);
    Long shareId = shareIdFor(shareLinkService.mintOrRotate(userId));

    // The load-bearing default: holding a grant on someone else's gallery must not re-share it.
    assertThat(shareLinkService.scopeCollectionIds(shareId)).doesNotContain(granted);

    shareLinkService.addOptIn(shareId, granted);
    assertThat(shareLinkService.scopeCollectionIds(shareId)).contains(granted);

    shareLinkService.removeOptIn(shareId, granted);
    assertThat(shareLinkService.scopeCollectionIds(shareId)).doesNotContain(granted);
  }

  @Test
  void scopePicksUpACollectionTaggedAfterTheLinkWasMinted() {
    Long userId = seedUser();
    Long shareId = shareIdFor(shareLinkService.mintOrRotate(userId));
    assertThat(shareLinkService.scopeCollectionIds(shareId)).isEmpty();

    Long taggedLater = seedCollection();
    tagCollection(taggedLater, userId);

    // The anti-snapshot property: an already-sent link reflects new work with no re-mint.
    assertThat(shareLinkService.scopeCollectionIds(shareId)).contains(taggedLater);
  }

  @Test
  void levelForIsGeneralInsideScopeAndEmptyOutsideIt() {
    Long userId = seedUser();
    Long tagged = seedCollection();
    Long unrelated = seedCollection();
    tagCollection(tagged, userId);
    Long shareId = shareIdFor(shareLinkService.mintOrRotate(userId));

    assertThat(shareLinkService.levelFor(shareId, tagged)).contains(AccessLevel.GENERAL);
    assertThat(shareLinkService.levelFor(shareId, unrelated)).isEmpty();
  }

  @Test
  void levelForNeverExceedsGeneralEvenForAClientGrantedCollection() {
    Long userId = seedUser();
    Long granted = seedCollection();
    grant(userId, granted, AccessLevel.CLIENT);
    Long shareId = shareIdFor(shareLinkService.mintOrRotate(userId));
    shareLinkService.addOptIn(shareId, granted);

    // The owner is a CLIENT here, but a link holder is a guest with an allowlist, not a borrower.
    assertThat(shareLinkService.levelFor(shareId, granted)).contains(AccessLevel.GENERAL);
  }

  @Test
  void oneUsersOptInDoesNotLeakIntoAnotherUsersShare() {
    Long owner = seedUser();
    Long other = seedUser();
    Long ownerTagged = seedCollection();
    tagCollection(ownerTagged, owner);
    Long ownerShare = shareIdFor(shareLinkService.mintOrRotate(owner));
    Long otherShare = shareIdFor(shareLinkService.mintOrRotate(other));

    assertThat(shareLinkService.scopeCollectionIds(ownerShare)).contains(ownerTagged);
    assertThat(shareLinkService.scopeCollectionIds(otherShare)).doesNotContain(ownerTagged);
    assertThat(shareLinkService.levelFor(otherShare, ownerTagged)).isEmpty();
  }
}
