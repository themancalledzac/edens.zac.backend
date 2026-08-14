package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.config.FlybyCookies;
import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.ShareLinkEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.ShareModels;
import edens.zac.portfolio.backend.services.ShareLinkService;
import edens.zac.portfolio.backend.services.UserPageAssembler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The recipient side of a share link: anonymous routes that trade a token for a view.
 *
 * <p>Both routes issue the {@code ezac_flyby} cookie, which is where the rolling 30-day window is
 * refreshed. {@link edens.zac.portfolio.backend.config.FlybySessionFilter} deliberately does not do
 * this, because a Set-Cookie in the filter would ride on every response including the cacheable
 * {@code /api/read/**} bodies CloudFront fronts. These two routes are absent from
 * CacheControlInterceptor's allow-list and so are stamped {@code no-store}, which is what makes it
 * safe to set a cookie here.
 */
@RestController
@RequestMapping("/api/read/share")
@RequiredArgsConstructor
@Slf4j
public class ShareControllerProd {

  private final ShareLinkService shareLinkService;
  private final UserPageAssembler userPageAssembler;
  private final AppUserRepository appUserRepository;

  @Value("${app.share.cookie-secure:true}")
  private boolean cookieSecure;

  /**
   * Exchange a share token for the recipient view, and start (or renew) the browsing session.
   *
   * <p>An unknown token and a rotated one both 404. They are genuinely indistinguishable -- a reset
   * leaves no record of the old secret -- and 404 is the right answer anyway: 401 would invite a
   * retry and would confirm that some share exists at that address.
   */
  @GetMapping("/{token}")
  public ResponseEntity<ShareModels.ShareView> exchange(@PathVariable String token) {
    ShareLinkEntity link =
        shareLinkService
            .resolveByRawToken(token)
            .orElseThrow(() -> new ResourceNotFoundException("No such share link"));

    shareLinkService.touchLastUsed(link.getId());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, FlybyCookies.build(token, cookieSecure).toString())
        .body(buildView(link));
  }

  /**
   * The current recipient's view, resolved from the cookie alone. This is the "way back": once a
   * recipient has walked off into a collection, this returns them to the shared page without
   * needing the original link.
   */
  @GetMapping("/view")
  public ResponseEntity<ShareModels.ShareView> currentView(
      @AuthenticationPrincipal AuthPrincipal principal, HttpServletRequest request) {
    if (principal == null || principal.shareId() == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    ShareLinkEntity link =
        shareLinkService
            .findById(principal.shareId())
            .orElseThrow(() -> new ResourceNotFoundException("No such share link"));

    // Re-issue the cookie so an active recipient's 30-day window keeps rolling forward. The raw
    // token comes back off the request; it is never reconstructible from the stored hash.
    String rawToken = FlybyCookies.read(request);
    ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
    if (rawToken != null) {
      builder.header(HttpHeaders.SET_COOKIE, FlybyCookies.build(rawToken, cookieSecure).toString());
    }
    return builder.body(buildView(link));
  }

  private ShareModels.ShareView buildView(ShareLinkEntity link) {
    String ownerName =
        appUserRepository.findById(link.getUserId()).map(AppUserEntity::getName).orElse(null);
    return new ShareModels.ShareView(
        ownerName, userPageAssembler.assembleForShare(link.getId(), link.getUserId()));
  }
}
