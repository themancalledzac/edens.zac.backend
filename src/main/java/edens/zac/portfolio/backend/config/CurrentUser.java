package edens.zac.portfolio.backend.config;

import edens.zac.portfolio.backend.model.AuthPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The acting user's id, read off the static security context.
 *
 * <p>Null is a deliberate contract, for two different reasons. On {@code /api/admin/**} it is the
 * dev path ({@code app.admin.enforce-authz=false}) and only feeds audit columns. On the public read
 * surface it means anonymous, a legitimate caller: {@code
 * CollectionService.isGalleryAccessAuthorized} falls through to the gallery password cookie.
 * Tightening this to throw would break local admin writes and 500 an anonymous gallery visit.
 *
 * <p>Not {@link AuthPrincipal#isRealUser}: that inspects an injected
 * {@code @AuthenticationPrincipal} argument, this reads the static holder.
 */
public final class CurrentUser {

  private CurrentUser() {}

  /**
   * @return the authenticated principal's user id, or null when there is no authenticated principal
   */
  public static Long userId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) ? p.userId() : null;
  }
}
