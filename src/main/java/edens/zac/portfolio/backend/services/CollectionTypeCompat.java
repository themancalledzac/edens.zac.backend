package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.types.CollectionType;

/**
 * Dual-compat mapping between the new {@code isClient}/{@code isBlog} booleans and the legacy
 * {@link CollectionType} column during the V49 transition window. The booleans are the storage
 * truth; the legacy {@code type} column (and API field) is kept in sync on every write so old
 * frontends keep working until the type column is dropped in a later release.
 *
 * <p>Resolution rules:
 *
 * <ul>
 *   <li>An explicit {@code true} flag wins over everything: {@code isClient} derives {@code
 *       CLIENT_GALLERY}, {@code isBlog} derives {@code BLOG}, and the other flag is cleared (mutual
 *       exclusion).
 *   <li>A {@code null} flag means "leave it untouched": it inherits its value from the effective
 *       base type (the requested legacy type when present, else the current type). Partial updates
 *       therefore never silently demote a client gallery or blog.
 *   <li>Only an explicit {@code false} clears a flag. When neither flag ends up true, a base type
 *       of {@code CLIENT_GALLERY}/{@code BLOG} (or absent) folds to {@code MISC}, otherwise the
 *       base type is preserved.
 *   <li>Request provides only a legacy type -&gt; booleans are derived from it ({@code
 *       CLIENT_GALLERY} -&gt; client, {@code BLOG} -&gt; blog, everything else false/false).
 *   <li>Request provides neither flags nor type -&gt; the current type is preserved (create without
 *       type -&gt; {@code MISC}).
 *   <li>Explicit {@code isClient && isBlog} is a nonsense combination and is rejected with {@link
 *       IllegalArgumentException} (400 via GlobalExceptionHandler).
 * </ul>
 */
public final class CollectionTypeCompat {

  private CollectionTypeCompat() {} // Prevent instantiation

  /** The resolved legacy type plus the boolean flags, always mutually consistent. */
  public record Resolved(CollectionType type, boolean isClient, boolean isBlog) {}

  /**
   * Resolve the stored type + flags from a create/update request.
   *
   * @param isClientRequested the request's isClient field (null when not provided)
   * @param isBlogRequested the request's isBlog field (null when not provided)
   * @param requestedType the request's legacy type field (null when not provided)
   * @param currentType the entity's current type (null on create)
   * @return the consistent (type, isClient, isBlog) triple to store
   * @throws IllegalArgumentException when both booleans are true (rejected as 400)
   */
  public static Resolved resolve(
      Boolean isClientRequested,
      Boolean isBlogRequested,
      CollectionType requestedType,
      CollectionType currentType) {
    boolean flagsProvided = isClientRequested != null || isBlogRequested != null;
    if (flagsProvided) {
      if (Boolean.TRUE.equals(isClientRequested) && Boolean.TRUE.equals(isBlogRequested)) {
        throw new IllegalArgumentException(
            "isClient and isBlog are mutually exclusive; a collection cannot be both");
      }
      // An explicit true is a category change: it wins over the legacy type and clears the
      // other flag (mutual exclusion), whether that flag was absent or explicitly false.
      if (Boolean.TRUE.equals(isClientRequested)) {
        return new Resolved(CollectionType.CLIENT_GALLERY, true, false);
      }
      if (Boolean.TRUE.equals(isBlogRequested)) {
        return new Resolved(CollectionType.BLOG, false, true);
      }
      // Remaining flags are explicit false (clear) or null (leave untouched). A null flag
      // inherits its value from the effective base type so a partial update like
      // {"isBlog": false} never silently demotes a client gallery.
      CollectionType base = requestedType != null ? requestedType : currentType;
      boolean client = isClientRequested == null && base != null && deriveIsClient(base);
      boolean blog = isBlogRequested == null && base != null && deriveIsBlog(base);
      if (client) {
        return new Resolved(CollectionType.CLIENT_GALLERY, true, false);
      }
      if (blog) {
        return new Resolved(CollectionType.BLOG, false, true);
      }
      // Neither flag survives: preserve the base type unless it encoded client/blog, which
      // was just explicitly disclaimed -- that folds to MISC.
      if (base == null || base == CollectionType.CLIENT_GALLERY || base == CollectionType.BLOG) {
        base = CollectionType.MISC;
      }
      return new Resolved(base, false, false);
    }

    // Legacy path: no booleans in the request. Type field (or current type) drives the flags.
    CollectionType type = requestedType != null ? requestedType : currentType;
    if (type == null) {
      type = CollectionType.MISC;
    }
    return new Resolved(type, deriveIsClient(type), deriveIsBlog(type));
  }

  /** True when the legacy type encodes a client gallery. */
  public static boolean deriveIsClient(CollectionType type) {
    return type == CollectionType.CLIENT_GALLERY;
  }

  /** True when the legacy type encodes a blog. */
  public static boolean deriveIsBlog(CollectionType type) {
    return type == CollectionType.BLOG;
  }
}
