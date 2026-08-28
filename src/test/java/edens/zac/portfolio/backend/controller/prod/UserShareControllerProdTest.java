package edens.zac.portfolio.backend.controller.prod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.config.ShareEmailLimiter;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.ShareLinkEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.ShareModels;
import edens.zac.portfolio.backend.services.CollectionAccessService;
import edens.zac.portfolio.backend.services.CollectionProcessingUtil;
import edens.zac.portfolio.backend.services.EmailService;
import edens.zac.portfolio.backend.services.ShareLinkService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class UserShareControllerProdTest {

  private final ShareLinkService shareLinkService = mock(ShareLinkService.class);
  private final CollectionAccessService collectionAccessService =
      mock(CollectionAccessService.class);
  private final CollectionRepository collectionRepository = mock(CollectionRepository.class);
  private final CollectionProcessingUtil collectionProcessingUtil =
      mock(CollectionProcessingUtil.class);

  private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
  private final EmailService emailService = mock(EmailService.class);
  private final ShareEmailLimiter shareEmailLimiter = mock(ShareEmailLimiter.class);

  private final UserShareControllerProd controller =
      new UserShareControllerProd(
          shareLinkService,
          collectionAccessService,
          collectionRepository,
          collectionProcessingUtil,
          appUserRepository,
          emailService,
          shareEmailLimiter,
          "https://zacedens.com/");

  /** Every test but the rate-limit ones assumes an unlimited sender, as before the limiter. */
  @BeforeEach
  void allowSendsByDefault() {
    when(shareEmailLimiter.allow(anyLong())).thenReturn(true);
  }

  private static final AuthPrincipal OWNER = AuthPrincipal.client(7L, "owner@example.com", true);
  private static final ShareLinkEntity LINK = ShareLinkEntity.builder().id(42L).userId(7L).build();

  @Test
  void theLiveLinkStaysReadableAfterTheRequestThatMintedIt() {
    when(shareLinkService.mintOrRotate(7L)).thenReturn("fresh-token");
    when(shareLinkService.revealToken(7L)).thenReturn(Optional.of("fresh-token"));
    when(shareLinkService.findForUser(7L)).thenReturn(Optional.of(LINK));
    when(shareLinkService.optInCollectionIds(42L)).thenReturn(List.of());
    when(collectionAccessService.memberCollectionIdsForUser(7L)).thenReturn(List.of());
    when(collectionRepository.findCollectionIdsByPersonId(7L)).thenReturn(List.of());

    ShareModels.ShareSettings afterRotate = controller.rotate(OWNER).getBody();
    ShareModels.ShareSettings afterRead = controller.settings(OWNER).getBody();

    assertThat(afterRotate).isNotNull();
    assertThat(afterRotate.token()).isEqualTo("fresh-token");
    assertThat(afterRotate.exists()).isTrue();
    // The whole point of V58: a later read still surfaces it, so sending the same link to a second
    // person is a copy rather than a reset that would cut off the first.
    assertThat(afterRead).isNotNull();
    assertThat(afterRead.token()).isEqualTo("fresh-token");
  }

  @Test
  void anUnrecoverableTokenReadsAsNullRatherThanFailing() {
    // Rows minted before V58 have no ciphertext. The page shows "reset to get a new link".
    when(shareLinkService.revealToken(7L)).thenReturn(Optional.empty());
    when(shareLinkService.findForUser(7L)).thenReturn(Optional.of(LINK));
    when(shareLinkService.optInCollectionIds(42L)).thenReturn(List.of());
    when(collectionAccessService.memberCollectionIdsForUser(7L)).thenReturn(List.of());
    when(collectionRepository.findCollectionIdsByPersonId(7L)).thenReturn(List.of());

    ShareModels.ShareSettings settings = controller.settings(OWNER).getBody();

    assertThat(settings).isNotNull();
    assertThat(settings.exists()).isTrue();
    assertThat(settings.token()).isNull();
  }

  @Test
  void emailSendsTheLinkAlreadyInCirculationRatherThanMintingAFreshOne() {
    when(shareLinkService.revealToken(7L)).thenReturn(Optional.of("live-token"));
    when(appUserRepository.findById(7L))
        .thenReturn(Optional.of(AppUserEntity.builder().id(7L).name("Ada").build()));
    when(emailService.sendShareLinkEmail(eq("mum@example.com"), eq("Ada"), anyString()))
        .thenReturn(new EmailService.SendResult(true, null));

    ResponseEntity<ShareModels.ShareEmailResult> response =
        controller.emailLink(OWNER, new ShareModels.SendShareLinkRequest("mum@example.com"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().sent()).isTrue();
    // Emailing a second person must not invalidate the first person's copy.
    verify(shareLinkService, never()).mintOrRotate(anyLong());
    // Trailing slash stripped, so the emailed link matches the copied one byte for byte.
    verify(emailService)
        .sendShareLinkEmail("mum@example.com", "Ada", "https://zacedens.com/s/live-token");
  }

  @Test
  void emailIsAConflictWhenTheLinkCannotBeRecovered() {
    when(shareLinkService.revealToken(7L)).thenReturn(Optional.empty());

    assertThat(
            controller
                .emailLink(OWNER, new ShareModels.SendShareLinkRequest("mum@example.com"))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);

    verifyNoInteractions(emailService);
  }

  @Test
  void addCollectionIsForbiddenWhenTheOwnerCannotViewIt() {
    when(collectionAccessService.canView(OWNER, 99L)).thenReturn(false);

    assertThat(controller.addCollection(OWNER, 99L).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    // The toggle must not be able to widen a share past what its owner can see.
    verify(shareLinkService, never()).addOptIn(anyLong(), anyLong());
  }

  @Test
  void addCollectionOptsInWhenTheOwnerHoldsAGrant() {
    when(collectionAccessService.canView(OWNER, 99L)).thenReturn(true);
    when(shareLinkService.findForUser(7L)).thenReturn(Optional.of(LINK));

    assertThat(controller.addCollection(OWNER, 99L).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    verify(shareLinkService).addOptIn(42L, 99L);
  }

  @Test
  void removeCollectionIsNotGatedOnACurrentGrant() {
    // If the owner's access was revoked they must still be able to take it out of their share.
    when(shareLinkService.findForUser(7L)).thenReturn(Optional.of(LINK));

    assertThat(controller.removeCollection(OWNER, 99L).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    verify(shareLinkService).removeOptIn(42L, 99L);
    verify(collectionAccessService, never()).canView(any(), anyLong());
  }

  @Test
  void candidateCollectionsExcludeTheOnesTheOwnerIsTaggedIn() {
    when(shareLinkService.findForUser(7L)).thenReturn(Optional.of(LINK));
    when(shareLinkService.optInCollectionIds(42L)).thenReturn(List.of());
    when(collectionAccessService.memberCollectionIdsForUser(7L)).thenReturn(List.of(1L, 2L));
    // Tagged-in collections are already in every share, so they are not offered as toggles.
    when(collectionRepository.findCollectionIdsByPersonId(7L)).thenReturn(List.of(1L));
    when(collectionRepository.findByIds(List.of(2L))).thenReturn(List.of());
    when(collectionProcessingUtil.batchConvertToBasicModels(List.of())).thenReturn(List.of());

    controller.settings(OWNER);

    verify(collectionRepository).findByIds(List.of(2L));
  }

  @Test
  @DisplayName("a rate-limited sender gets 429 and SES is never called")
  void emailIsRefusedWhenTheSenderIsRateLimited() {
    when(shareEmailLimiter.allow(7L)).thenReturn(false);

    assertThat(
            controller
                .emailLink(OWNER, new ShareModels.SendShareLinkRequest("mum@example.com"))
                .getStatusCode())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    verifyNoInteractions(emailService);
  }

  @Test
  @DisplayName(
      "the limit is checked before the token lookup, so a 429 leaks nothing about the link")
  void rateLimitIsCheckedAheadOfTheConflictPath() {
    when(shareEmailLimiter.allow(7L)).thenReturn(false);

    controller.emailLink(OWNER, new ShareModels.SendShareLinkRequest("mum@example.com"));

    // Not merely "returns 429" -- a limited caller must not be able to tell a missing link from a
    // present one, which means the lookup must not run at all.
    verify(shareLinkService, never()).revealToken(anyLong());
  }
}
