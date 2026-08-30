package edens.zac.portfolio.backend.config;

import edens.zac.portfolio.backend.model.AuthPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The acting user's id, read off the static security context.
 *
 * <p>Null means anonymous, and on the public read surface that is a legitimate caller: {@code
 * CollectionService.isGalleryAccessAuthorized} falls through to the gallery password cookie, so
 * tightening this to throw would 500 an anonymous gallery visit.
 *
 * <p>It can no longer be null behind {@code /api/admin/**}. That used to be the second reason for
 * the contract -- the dev path, where {@code app.admin.enforce-authz=false} let the gate fall
 * through to {@code permitAll} and the audit columns took a null. The toggle was removed on
 * 2026-08-30, so those call sites now always have a principal.
 *
 * <p>Not {@link AuthPrincipal#isRealUser}: that inspects an injected
 * {@code @AuthenticationPrincipal} argument, this reads the static holder.
 */
public final class CurrentUser {

  private CurrentUser() {}

  /**
   * @return the authenticated principal, or null when there is no authenticated principal
   */
  public static AuthPrincipal principal() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) ? p : null;
  }

  /**
   * @return the authenticated principal's user id, or null when there is no authenticated principal
   */
  public static Long userId() {
    AuthPrincipal p = principal();
    return p != null ? p.userId() : null;
  }
}
