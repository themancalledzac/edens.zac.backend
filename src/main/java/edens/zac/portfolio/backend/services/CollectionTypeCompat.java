package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.types.CollectionType;

/**
 * Dual-compat mapping between the new {@code isClient}/{@code isBlog} booleans and the legacy
 * {@link CollectionType} column during the V50 transition window. The booleans are the storage
 * truth; the legacy {@code type} column (and API field) is kept in sync on every write so old
 * frontends keep working until the type column is dropped in a later release.
 *
 * <p>Callers use the named entry points {@link #forCreate} and {@link #forUpdate} rather than a
 * single positional method: they carry different amounts of current state, and the named forms
 * remove both the {@code null} current-state literal at the create call site and the
 * two-adjacent-Booleans transposition hazard.
 *
 * <p>Resolution rules:
 *
 * <ul>
 *   <li>An explicit {@code true} flag wins over everything: {@code isClient} derives {@code
 *       CLIENT_GALLERY}, {@code isBlog} derives {@code BLOG}, and the other flag is cleared (mutual
 *       exclusion).
 *   <li>A {@code null} flag means "leave it untouched": on update it inherits the entity's current
 *       boolean; when a legacy {@code type} is also present, that requested type wins and the flag
 *       is derived from it. Partial updates therefore never silently demote a client gallery or
 *       blog, even on a row whose {@code type} has drifted away from its flags.
 *   <li>Only an explicit {@code false} clears a flag. When neither flag ends up true, a base type
 *       of {@code CLIENT_GALLERY}/{@code BLOG} (or absent) folds to {@code MISC}, otherwise the
 *       base type is preserved.
 *   <li>Request provides only a legacy type -&gt; booleans are derived from it ({@code
 *       CLIENT_GALLERY} -&gt; client, {@code BLOG} -&gt; blog, everything else false/false).
 *   <li>Request provides neither flags nor type -&gt; the current type is preserved (create without
 *       type -&gt; {@code MISC}).
 *   <li>Explicit {@code isClient && isBlog} is a nonsense combination and is rejected with {@link
 *       IllegalArgumentException} (400 via GlobalExceptionHandler).
 *   <li>An explicit {@code true} flag against a structural base type ({@code PARENT}/{@code HOME})
 *       is rejected the same way: one checkbox must not silently destroy the structural type that
 *       {@code isParentType()} gates across the service layer. Retyping a parent stays deliberate
 *       via the legacy {@code type} field.
 * </ul>
 */
public final class CollectionTypeCompat {

  private CollectionTypeCompat() {} // Prevent instantiation

  private static final Resolved CLIENT = new Resolved(CollectionType.CLIENT_GALLERY, true, false);
  private static final Resolved BLOG = new Resolved(CollectionType.BLOG, false, true);

  /**
   * The resolved legacy type plus the boolean flags. The compact constructor enforces the
   * consistency the rest of the codebase assumes, so no caller can construct (or a future rule
   * branch return) a triple such as {@code (BLOG, true, false)}.
   */
  public record Resolved(CollectionType type, boolean isClient, boolean isBlog) {

    /** Apply the whole triple to an entity, so no call site can half-apply it. */
    public void applyTo(CollectionEntity entity) {
      entity.setType(type);
      entity.setClient(isClient);
      entity.setBlog(isBlog);
    }

    /** Rejects any triple whose flags disagree with the type. */
    public Resolved {
      if (type == null) {
        throw new IllegalArgumentException("Resolved type cannot be null");
      }
      if (isClient != deriveIsClient(type) || isBlog != deriveIsBlog(type)) {
        throw new IllegalArgumentException(
            "Resolved flags are inconsistent with type "
                + type
                + " (isClient="
                + isClient
                + ", isBlog="
                + isBlog
                + ")");
      }
    }
  }

  /**
   * Resolve the stored type + flags for a create request. There is no current state, so a request
   * carrying neither flags nor a legacy type lands on {@code MISC}.
   *
   * @param isClientRequested the request's isClient field (null when not provided)
   * @param isBlogRequested the request's isBlog field (null when not provided)
   * @param requestedType the request's legacy type field (null when not provided)
   * @return the consistent (type, isClient, isBlog) triple to store
   * @throws IllegalArgumentException when both booleans are true
   */
  public static Resolved forCreate(
      Boolean isClientRequested, Boolean isBlogRequested, CollectionType requestedType) {
    return resolve(isClientRequested, isBlogRequested, requestedType, null, false, false);
  }

  /**
   * Resolve the stored type + flags for an update against an existing entity. Untouched (null)
   * flags inherit the entity's CURRENT booleans -- not the legacy type column -- so an update on a
   * row whose type and flags have drifted apart never silently clears a flag, and the resolution
   * keeps working unchanged once phase 2 nulls the type column.
   *
   * @param isClientRequested the request's isClient field (null when not provided)
   * @param isBlogRequested the request's isBlog field (null when not provided)
   * @param requestedType the request's legacy type field (null when not provided)
   * @param current the entity being updated
   * @return the consistent (type, isClient, isBlog) triple to store
   * @throws IllegalArgumentException when both booleans are true, or an explicit true flag targets
   *     a parent-type base
   */
  public static Resolved forUpdate(
      Boolean isClientRequested,
      Boolean isBlogRequested,
      CollectionType requestedType,
      CollectionEntity current) {
    return resolve(
        isClientRequested,
        isBlogRequested,
        requestedType,
        current.getType(),
        current.isClient(),
        current.isBlog());
  }

  private static Resolved resolve(
      Boolean isClientRequested,
      Boolean isBlogRequested,
      CollectionType requestedType,
      CollectionType currentType,
      boolean currentIsClient,
      boolean currentIsBlog) {
    boolean flagsProvided = isClientRequested != null || isBlogRequested != null;
    CollectionType base = requestedType != null ? requestedType : currentType;
    if (flagsProvided) {
      if (Boolean.TRUE.equals(isClientRequested) && Boolean.TRUE.equals(isBlogRequested)) {
        throw new IllegalArgumentException(
            "isClient and isBlog are mutually exclusive; a collection cannot be both");
      }
      boolean explicitTrue =
          Boolean.TRUE.equals(isClientRequested) || Boolean.TRUE.equals(isBlogRequested);
      if (explicitTrue && base != null && base.isParentType()) {
        throw new IllegalArgumentException(
            "isClient/isBlog cannot be set on a "
                + base
                + " collection; change its type explicitly instead");
      }
      // An explicit true is a category change: it wins over the legacy type and clears the
      // other flag (mutual exclusion), whether that flag was absent or explicitly false.
      if (Boolean.TRUE.equals(isClientRequested)) {
        return CLIENT;
      }
      if (Boolean.TRUE.equals(isBlogRequested)) {
        return BLOG;
      }
      // Remaining flags are explicit false (clear) or null (leave untouched). A null flag
      // inherits the entity's current boolean, unless the request also names a legacy type --
      // that requested type is an explicit signal and derives the untouched flag instead.
      boolean baseIsClient =
          requestedType != null ? deriveIsClient(requestedType) : currentIsClient;
      boolean baseIsBlog = requestedType != null ? deriveIsBlog(requestedType) : currentIsBlog;
      if (isClientRequested == null && baseIsClient) {
        return CLIENT;
      }
      if (isBlogRequested == null && baseIsBlog) {
        return BLOG;
      }
      // Neither flag survives: preserve the base type unless it encoded client/blog, which
      // was just explicitly disclaimed -- that folds to MISC.
      if (base == null || deriveIsClient(base) || deriveIsBlog(base)) {
        base = CollectionType.MISC;
      }
      return new Resolved(base, false, false);
    }

    // Legacy path: no booleans in the request. Type field (or current type) drives the flags.
    CollectionType type = base != null ? base : CollectionType.MISC;
    return switch (type) {
      case CLIENT_GALLERY -> CLIENT;
      case BLOG -> BLOG;
      case PORTFOLIO, ART_GALLERY, HOME, PARENT, MISC -> new Resolved(type, false, false);
    };
  }

  /** True when the legacy type encodes a client gallery. */
  private static boolean deriveIsClient(CollectionType type) {
    return type == CollectionType.CLIENT_GALLERY;
  }

  /** True when the legacy type encodes a blog. */
  private static boolean deriveIsBlog(CollectionType type) {
    return type == CollectionType.BLOG;
  }
}
