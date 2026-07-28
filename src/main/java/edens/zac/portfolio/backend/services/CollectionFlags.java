package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CollectionEntity;

/**
 * Resolution of the two stored discriminators, {@code isClient} and {@code isBlog}. They are
 * mutually exclusive: a collection is a client gallery, a blog, or neither.
 *
 * <p>Tri-state update semantics:
 *
 * <ul>
 *   <li>An explicit {@code true} wins and clears the other flag.
 *   <li>An explicit {@code false} clears that flag.
 *   <li>A {@code null} leaves that flag untouched -- on update it inherits the entity's current
 *       value, so a partial update never silently demotes a client gallery or a blog.
 * </ul>
 *
 * <p>Replaces {@code CollectionTypeCompat}, which additionally rejected an explicit flag against a
 * PARENT/HOME base type. That rule is gone with the type column: parent-ness is derived from the
 * content graph and no longer conflicts with either flag.
 */
public final class CollectionFlags {

  private CollectionFlags() {} // Prevent instantiation

  private static final Resolved CLIENT = new Resolved(true, false);
  private static final Resolved BLOG = new Resolved(false, true);
  private static final Resolved NEITHER = new Resolved(false, false);

  /**
   * The resolved flag pair. The compact constructor enforces mutual exclusion so no caller (or
   * future rule branch) can produce a both-true pair.
   */
  public record Resolved(boolean isClient, boolean isBlog) {

    /** Rejects a both-true pair, so mutual exclusion cannot be bypassed by direct construction. */
    public Resolved {
      if (isClient && isBlog) {
        throw new IllegalArgumentException(
            "isClient and isBlog are mutually exclusive; a collection cannot be both");
      }
    }

    /** Apply both flags to an entity, so no call site can half-apply the pair. */
    public void applyTo(CollectionEntity entity) {
      entity.setClient(isClient);
      entity.setBlog(isBlog);
    }
  }

  /** Resolve the flags for a create request. There is no current state, so null means false. */
  public static Resolved forCreate(Boolean isClientRequested, Boolean isBlogRequested) {
    return resolve(isClientRequested, isBlogRequested, false, false);
  }

  /**
   * Resolve the flags for an update. Untouched (null) flags inherit the entity's current values.
   */
  public static Resolved forUpdate(
      Boolean isClientRequested, Boolean isBlogRequested, CollectionEntity current) {
    return resolve(isClientRequested, isBlogRequested, current.isClient(), current.isBlog());
  }

  private static Resolved resolve(
      Boolean isClientRequested,
      Boolean isBlogRequested,
      boolean currentIsClient,
      boolean currentIsBlog) {
    if (Boolean.TRUE.equals(isClientRequested) && Boolean.TRUE.equals(isBlogRequested)) {
      throw new IllegalArgumentException(
          "isClient and isBlog are mutually exclusive; a collection cannot be both");
    }
    if (Boolean.TRUE.equals(isClientRequested)) {
      return CLIENT;
    }
    if (Boolean.TRUE.equals(isBlogRequested)) {
      return BLOG;
    }
    // Remaining flags are explicit false (clear) or null (inherit).
    boolean resolvedIsClient = isClientRequested == null && currentIsClient;
    boolean resolvedIsBlog = isBlogRequested == null && currentIsBlog;
    if (resolvedIsClient) {
      return CLIENT;
    }
    if (resolvedIsBlog) {
      return BLOG;
    }
    return NEITHER;
  }
}
