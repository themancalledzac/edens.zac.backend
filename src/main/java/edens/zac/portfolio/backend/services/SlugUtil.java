package edens.zac.portfolio.backend.services;

/** Utility for generating URL-friendly slugs from display names. */
public final class SlugUtil {

  private SlugUtil() {}

  /**
   * Generate a slug from a display name.
   *
   * <p>Examples: "Dolomites, Italy" -> "dolomites-italy", "Seattle, Washington" ->
   * "seattle-washington", "John Doe" -> "john-doe"
   *
   * @param name The display name to generate a slug from
   * @return The generated slug, or empty string if name is null/empty
   */
  public static String generateSlug(String name) {
    if (name == null || name.isEmpty()) {
      return "";
    }

    return name.toLowerCase()
        .replaceAll("[^a-z0-9\\s-]", "")
        .replaceAll("\\s+", "-")
        .replaceAll("-+", "-")
        .replaceAll("^-|-$", "");
  }
}
