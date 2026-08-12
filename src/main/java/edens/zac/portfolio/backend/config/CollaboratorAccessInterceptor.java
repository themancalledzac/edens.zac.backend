package edens.zac.portfolio.backend.config;

import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.services.CollectionAccessService;
import edens.zac.portfolio.backend.types.AccessLevel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Route-level gate for the collaborator tier: every /api/edit/** route must carry a {collectionId}
 * path variable, and the caller must hold COLLABORATOR-or-above on that collection (global admins
 * outrank via the computed ADMIN sentinel). Fail-closed: a route without the variable, an
 * unparseable id, or a missing principal is denied. An interceptor (not a Filter) so the throw is
 * routed through GlobalExceptionHandler as a quiet 403, and because only a post-mapping component
 * can see HandlerMapping's URI template variables.
 *
 * <p>Hard boundary: this only authorizes the {collectionId} path variable itself. It cannot see
 * whether other ids referenced elsewhere in the path or request body (e.g. a child/related
 * collection id) belong to the same collection -- that cross-collection consistency check is an
 * endpoint-level concern, owned by the individual controller (see the Task 10 guard).
 */
class CollaboratorAccessInterceptor implements HandlerInterceptor {

  private static final String COLLECTION_ID_VARIABLE = "collectionId";

  private final CollectionAccessService collectionAccessService;

  CollaboratorAccessInterceptor(CollectionAccessService collectionAccessService) {
    this.collectionAccessService = collectionAccessService;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    Long collectionId = pathCollectionId(request);
    if (collectionId == null) {
      throw new AccessDeniedException("No collection scope on this route");
    }
    if (!collectionAccessService.hasAtLeast(principal(), collectionId, AccessLevel.COLLABORATOR)) {
      throw new AccessDeniedException(
          "Collaborator access required for collection " + collectionId);
    }
    return true;
  }

  private static AuthPrincipal principal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) ? p : null;
  }

  private static Long pathCollectionId(HttpServletRequest request) {
    Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (!(attribute instanceof Map<?, ?> variables)) {
      return null;
    }
    Object raw = variables.get(COLLECTION_ID_VARIABLE);
    if (raw == null) {
      return null;
    }
    try {
      return Long.valueOf(raw.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
