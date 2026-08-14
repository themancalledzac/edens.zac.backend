package edens.zac.portfolio.backend.controller.prod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import edens.zac.portfolio.backend.controller.user.UserRatingOverrideControllerProd;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.UserRatingOverrideRequest;
import edens.zac.portfolio.backend.services.UserFollowsService;
import edens.zac.portfolio.backend.services.UserPageAssembler;
import edens.zac.portfolio.backend.services.UserRatingOverrideService;
import edens.zac.portfolio.backend.services.UserSavesService;
import edens.zac.portfolio.backend.services.UserSelectsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * A share-link holder must be refused by every endpoint that reads or writes somebody's own data.
 *
 * <p>The old {@code principal == null} guards did not do this: a flyby is a non-null principal, so
 * it passed them and then handed a null userId to the service beneath. These assertions pin the
 * replacement guard at each site, and check the service is never reached -- returning 401 while
 * still calling through would leave the door open.
 *
 * <p>{@code myPage} is in here for the same reason as the write endpoints: it assembles the
 * sharer's OWN page, which includes the collections they hold role grants on, and that is exactly
 * the set a share link is designed not to expose.
 */
class FlybyWriteLockoutTest {

  private final UserSelectsService selectsService = mock(UserSelectsService.class);
  private final UserSavesService savesService = mock(UserSavesService.class);
  private final UserFollowsService followsService = mock(UserFollowsService.class);
  private final UserPageAssembler pageAssembler = mock(UserPageAssembler.class);
  private final UserRatingOverrideService ratingService = mock(UserRatingOverrideService.class);

  private final UserSelectsControllerProd selects = new UserSelectsControllerProd(selectsService);
  private final UserSavesControllerProd saves = new UserSavesControllerProd(savesService);
  private final UserFollowsControllerProd follows = new UserFollowsControllerProd(followsService);
  private final UserControllerProd user = new UserControllerProd(pageAssembler);
  private final UserRatingOverrideControllerProd ratings =
      new UserRatingOverrideControllerProd(ratingService);

  private static final AuthPrincipal FLYBY = AuthPrincipal.flyby(3L);

  @Test
  void flybyIsRejectedFromSelects() {
    assertThat(selects.remove(FLYBY, 1L).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(selects.list(FLYBY, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(selectsService);
  }

  @Test
  void flybyIsRejectedFromSaves() {
    assertThat(saves.remove(FLYBY, 1L).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(saves.list(FLYBY).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(saves.listImages(FLYBY).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(savesService);
  }

  @Test
  void flybyIsRejectedFromFollows() {
    assertThat(follows.remove(FLYBY, 1L).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(follows.list(FLYBY).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(followsService);
  }

  @Test
  void flybyIsRejectedFromTheOwnersOwnUserPage() {
    assertThat(user.myPage(FLYBY).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(pageAssembler);
  }

  @Test
  void flybyIsRejectedFromRatingOverrides() {
    assertThat(ratings.list(FLYBY, 1L).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(ratings.upsert(FLYBY, new UserRatingOverrideRequest(1L, 2L, 3)).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(ratingService);
  }

  @Test
  void anonymousIsStillRejectedEverywhere() {
    // The guard widened from "null" to "no userId"; the original anonymous case must still hold.
    assertThat(saves.list(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(follows.list(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(user.myPage(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
