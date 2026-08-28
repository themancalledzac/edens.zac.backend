package edens.zac.portfolio.backend.config;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.controller.prod.UserControllerProd;
import edens.zac.portfolio.backend.controller.prod.UserFollowsControllerProd;
import edens.zac.portfolio.backend.controller.prod.UserSavesControllerProd;
import edens.zac.portfolio.backend.controller.prod.UserSelectsControllerProd;
import edens.zac.portfolio.backend.controller.prod.UserShareControllerProd;
import edens.zac.portfolio.backend.controller.user.UserRatingOverrideControllerProd;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.entity.ShareLinkEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.services.CollectionAccessService;
import edens.zac.portfolio.backend.services.CollectionProcessingUtil;
import edens.zac.portfolio.backend.services.EmailService;
import edens.zac.portfolio.backend.services.SessionService;
import edens.zac.portfolio.backend.services.ShareLinkService;
import edens.zac.portfolio.backend.services.UserFollowsService;
import edens.zac.portfolio.backend.services.UserPageAssembler;
import edens.zac.portfolio.backend.services.UserRatingOverrideService;
import edens.zac.portfolio.backend.services.UserSavesService;
import edens.zac.portfolio.backend.services.UserSelectsService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The {@code /api/read/user/**} matcher, on the real chain, against the real controllers.
 *
 * <p>Replaces the per-method {@code isRealUser} guards these six controllers used to open with.
 * Those were pinned by standalone-MockMvc tests, which build no security chain at all -- so once
 * the check moved into {@code SecurityConfig} there was nowhere left for those assertions to live.
 * The controllers are registered as real beans rather than stubs deliberately: the risk this MR
 * introduces is a route sitting outside the matcher's path pattern, and only the real
 * {@code @RequestMapping} values can catch that.
 *
 * <p>Both denial statuses are asserted because they mean different things. Anonymous is 401 (no
 * principal resolved); a share-link holder is 403 (a principal resolved, it just holds no
 * authority). That is the same split {@link FlybyAccessWebMvcTest} pins for {@code /api/admin/**}
 * and {@code /api/edit/**}, and it is a deliberate change here -- the guards returned 401 for both.
 *
 * <p>Declares its own nested {@code @Configuration} for the same reason every other slice in this
 * package does; see {@link EditAuthorizationDisabledWebMvcTest} for why omitting it fails context
 * load.
 */
@WebMvcTest
@Import({
  SecurityConfig.class,
  SessionAuthenticationFilter.class,
  FlybySessionFilter.class,
  UserRoutesAuthorizationWebMvcTest.UserControllers.class
})
class UserRoutesAuthorizationWebMvcTest {

  /** One route per controller, so a missed {@code @RequestMapping} prefix shows up as a failure. */
  private static final List<String> EVERY_USER_ROUTE =
      List.of(
          "/api/read/user/me/page",
          "/api/read/user/follows",
          "/api/read/user/saves",
          "/api/read/user/selects",
          "/api/read/user/share",
          "/api/read/user/ratings?collectionId=1");

  private static final Cookie FLYBY_COOKIE = new Cookie(FlybyCookies.COOKIE_NAME, "raw-token");

  @Autowired private MockMvc mockMvc;

  @MockBean private SessionService sessionService;
  @MockBean private ShareLinkService shareLinkService;
  @MockBean private UserPageAssembler userPageAssembler;
  @MockBean private UserFollowsService userFollowsService;
  @MockBean private UserSavesService userSavesService;
  @MockBean private UserSelectsService userSelectsService;
  @MockBean private UserRatingOverrideService userRatingOverrideService;
  @MockBean private CollectionAccessService collectionAccessService;
  @MockBean private CollectionRepository collectionRepository;
  @MockBean private CollectionProcessingUtil collectionProcessingUtil;
  @MockBean private AppUserRepository appUserRepository;
  @MockBean private EmailService emailService;

  @Test
  void anonymousIsRefusedFromEveryUserRoute() throws Exception {
    for (String route : EVERY_USER_ROUTE) {
      mockMvc.perform(get(route)).andExpect(status().isUnauthorized());
    }
    verifyNoServiceWasReached();
  }

  @Test
  void aShareLinkHolderIsRefusedFromEveryUserRoute() throws Exception {
    when(shareLinkService.resolveByRawToken("raw-token"))
        .thenReturn(Optional.of(ShareLinkEntity.builder().id(42L).userId(7L).build()));

    for (String route : EVERY_USER_ROUTE) {
      mockMvc.perform(get(route).cookie(FLYBY_COOKIE)).andExpect(status().isForbidden());
    }
    verifyNoServiceWasReached();
  }

  @Test
  void theNestedWriteRoutesAreCoveredToo() throws Exception {
    // The matcher carries no HttpMethod and the write routes sit a segment or two deeper
    // (/saves/{id}, /share/collections/{id}), so this pins that "/**" really does span them.
    mockMvc.perform(delete("/api/read/user/saves/42")).andExpect(status().isUnauthorized());
    mockMvc.perform(delete("/api/read/user/selects/42")).andExpect(status().isUnauthorized());
    mockMvc.perform(delete("/api/read/user/follows/42")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/read/user/share/rotate")).andExpect(status().isUnauthorized());
    mockMvc.perform(put("/api/read/user/share/collections/5")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/read/user/share/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toEmail\":\"mum@example.com\"}"))
        .andExpect(status().isUnauthorized());
    verifyNoServiceWasReached();
  }

  @Test
  void aSignedInUserReachesTheUserRoutes() throws Exception {
    // The other direction: a matcher that refused everyone would pass both tests above.
    when(sessionService.resolve("session-token"))
        .thenReturn(Optional.of(new AuthPrincipal(7L, "user@example.com", false, true)));
    when(userFollowsService.listFollowedCollectionIds(7L)).thenReturn(List.of(1L));

    mockMvc
        .perform(get("/api/read/user/follows").cookie(new Cookie("ezac_session", "session-token")))
        .andExpect(status().isOk());
  }

  /**
   * Refusing while still calling through would leave the door open, which is the property the old
   * {@code FlybyWriteLockoutTest} checked with {@code verifyNoInteractions}. At the chain the
   * matcher rejects before dispatch, so this holds for every route at once.
   */
  private void verifyNoServiceWasReached() {
    verifyNoInteractions(
        userPageAssembler,
        userFollowsService,
        userSavesService,
        userSelectsService,
        userRatingOverrideService,
        collectionAccessService,
        emailService);
  }

  @Configuration
  static class UserControllers {
    @Bean
    UserControllerProd userControllerProd(UserPageAssembler assembler) {
      return new UserControllerProd(assembler);
    }

    @Bean
    UserFollowsControllerProd userFollowsControllerProd(UserFollowsService service) {
      return new UserFollowsControllerProd(service);
    }

    @Bean
    UserSavesControllerProd userSavesControllerProd(UserSavesService service) {
      return new UserSavesControllerProd(service);
    }

    @Bean
    UserSelectsControllerProd userSelectsControllerProd(UserSelectsService service) {
      return new UserSelectsControllerProd(service);
    }

    @Bean
    UserRatingOverrideControllerProd userRatingOverrideControllerProd(
        UserRatingOverrideService service) {
      return new UserRatingOverrideControllerProd(service);
    }

    @Bean
    UserShareControllerProd userShareControllerProd(
        ShareLinkService shareLinkService,
        CollectionAccessService collectionAccessService,
        CollectionRepository collectionRepository,
        CollectionProcessingUtil collectionProcessingUtil,
        AppUserRepository appUserRepository,
        EmailService emailService) {
      return new UserShareControllerProd(
          shareLinkService,
          collectionAccessService,
          collectionRepository,
          collectionProcessingUtil,
          appUserRepository,
          emailService,
          // A real limiter with a budget nothing here can exhaust. This test asks who the security
          // chain lets through, and a mock returning false would answer a different question.
          new ShareEmailLimiter(10_000, 1_000_000),
          "https://zacedens.com");
    }
  }
}
