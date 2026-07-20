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
 *   <li>Request provides booleans (either field non-null) -&gt; booleans win. {@code isClient}
 *       derives {@code CLIENT_GALLERY}, {@code isBlog} derives {@code BLOG}; neither true -&gt; if
 *       the effective base type is {@code CLIENT_GALLERY}/{@code BLOG} (or absent) it becomes
 *       {@code MISC}, otherwise the base type is preserved.
 *   <li>Request provides only a legacy type -&gt; booleans are derived from it ({@code
 *       CLIENT_GALLERY} -&gt; client, {@code BLOG} -&gt; blog, everything else false/false).
 *   <li>Request provides neither -&gt; the current type is preserved (create without type -&gt;
 *       {@code MISC}).
 *   <li>{@code isClient && isBlog} is a nonsense combination and is rejected with {@link
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
      boolean client = Boolean.TRUE.equals(isClientRequested);
      boolean blog = Boolean.TRUE.equals(isBlogRequested);
      if (client && blog) {
        throw new IllegalArgumentException(
            "isClient and isBlog are mutually exclusive; a collection cannot be both");
      }
      if (client) {
        return new Resolved(CollectionType.CLIENT_GALLERY, true, false);
      }
      if (blog) {
        return new Resolved(CollectionType.BLOG, false, true);
      }
      // Neither flag set: preserve the effective base type unless it encoded client/blog,
      // which the booleans just disclaimed -- that folds to MISC.
      CollectionType base = requestedType != null ? requestedType : currentType;
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
