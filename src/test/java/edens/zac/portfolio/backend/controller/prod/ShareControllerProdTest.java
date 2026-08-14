package edens.zac.portfolio.backend.controller.prod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.config.FlybyCookies;
import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.ShareLinkEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ShareModels;
import edens.zac.portfolio.backend.services.ShareLinkService;
import edens.zac.portfolio.backend.services.UserPageAssembler;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

class ShareControllerProdTest {

  private final ShareLinkService shareLinkService = mock(ShareLinkService.class);
  private final UserPageAssembler userPageAssembler = mock(UserPageAssembler.class);
  private final AppUserRepository appUserRepository = mock(AppUserRepository.class);

  private final ShareControllerProd controller =
      new ShareControllerProd(shareLinkService, userPageAssembler, appUserRepository);

  private static final ShareLinkEntity LINK = ShareLinkEntity.builder().id(42L).userId(7L).build();

  @BeforeEach
  void setSecureFlag() {
    ReflectionTestUtils.setField(controller, "cookieSecure", true);
  }

  private void stubView() {
    when(appUserRepository.findById(7L))
        .thenReturn(Optional.of(AppUserEntity.builder().id(7L).name("Ada").build()));
    when(userPageAssembler.assembleForShare(42L, 7L))
        .thenReturn(CollectionModel.builder().slug("user").build());
  }

  @Test
  void exchangeReturnsTheRecipientViewAndNamesTheOwner() {
    when(shareLinkService.resolveByRawToken("raw")).thenReturn(Optional.of(LINK));
    stubView();

    ResponseEntity<ShareModels.ShareView> response = controller.exchange("raw");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().ownerName()).isEqualTo("Ada");
    assertThat(response.getBody().page().getSlug()).isEqualTo("user");
    verify(shareLinkService).touchLastUsed(42L);
  }

  @Test
  void exchangeSetsAnHttpOnlyLaxCookieCarryingTheRawToken() {
    when(shareLinkService.resolveByRawToken("raw")).thenReturn(Optional.of(LINK));
    stubView();

    String cookie = controller.exchange("raw").getHeaders().getFirst(HttpHeaders.SET_COOKIE);

    assertThat(cookie).isNotNull();
    assertThat(cookie).contains(FlybyCookies.COOKIE_NAME + "=raw");
    assertThat(cookie).contains("HttpOnly").contains("Secure").contains("Path=/");
    // Lax, not Strict: a link opened from a text message is a cross-site navigation.
    assertThat(cookie).contains("SameSite=Lax");
    assertThat(cookie).contains("Max-Age=" + FlybyCookies.MAX_AGE.getSeconds());
  }

  @Test
  void exchangeWithAnUnknownOrRotatedTokenIsNotFoundAndSetsNoCookie() {
    when(shareLinkService.resolveByRawToken("stale")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> controller.exchange("stale"))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(shareLinkService, never()).touchLastUsed(org.mockito.ArgumentMatchers.anyLong());
    verifyNoInteractions(userPageAssembler);
  }

  @Test
  void currentViewRefreshesTheRollingCookieForAnActiveRecipient() {
    when(shareLinkService.findById(42L)).thenReturn(Optional.of(LINK));
    stubView();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(FlybyCookies.COOKIE_NAME, "raw"));

    ResponseEntity<ShareModels.ShareView> response =
        controller.currentView(AuthPrincipal.flyby(42L), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
        .contains(FlybyCookies.COOKIE_NAME + "=raw");
  }

  @Test
  void currentViewRejectsAnonymousAndSignedInPrincipalsAlike() {
    MockHttpServletRequest request = new MockHttpServletRequest();

    assertThat(controller.currentView(null, request).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    // A signed-in user has no share view of their own; /user/me/page is their page.
    assertThat(
            controller
                .currentView(AuthPrincipal.client(7L, "u@example.com", true), request)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(shareLinkService, userPageAssembler);
  }
}
