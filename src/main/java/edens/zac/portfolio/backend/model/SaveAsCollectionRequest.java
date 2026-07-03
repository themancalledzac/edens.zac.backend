package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;

/**
 * Request body for promoting a tag-view into a real collection. All fields are optional; the
 * service defaults type to PORTFOLIO and visibility to UNLISTED.
 *
 * <p>{@code includeHidden} widens the member snapshot to also copy HIDDEN / password-gated members.
 * It defaults to false so a promote never silently copies dev-only or password-protected content
 * into a new collection; an admin must explicitly opt in.
 */
public record SaveAsCollectionRequest(
    CollectionType type, CollectionVisibility visibility, Boolean includeHidden) {

  /** True only when the caller explicitly opted into copying HIDDEN / password-gated members. */
  public boolean includeHiddenMembers() {
    return Boolean.TRUE.equals(includeHidden);
  }
}
