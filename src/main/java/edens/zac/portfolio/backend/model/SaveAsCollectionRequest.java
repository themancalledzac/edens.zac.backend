package edens.zac.portfolio.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import edens.zac.portfolio.backend.types.CollectionVisibility;

/**
 * Request body for promoting a tag-view into a real collection. All fields are optional; the
 * service defaults visibility to UNLISTED and leaves both flags unset.
 *
 * <p>{@code isClient && isBlog} is rejected as a 400 on create (see {@code CollectionFlags}).
 *
 * <p>{@code includeHidden} widens the member snapshot to also copy HIDDEN / password-gated members.
 * It defaults to false so a promote never silently copies dev-only or password-protected content
 * into a new collection; an admin must explicitly opt in.
 */
public record SaveAsCollectionRequest(
    CollectionVisibility visibility,
    Boolean includeHidden,
    @JsonProperty("isClient") Boolean isClient,
    @JsonProperty("isBlog") Boolean isBlog) {

  /** True only when the caller explicitly opted into copying HIDDEN / password-gated members. */
  public boolean includeHiddenMembers() {
    return Boolean.TRUE.equals(includeHidden);
  }
}
