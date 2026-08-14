package edens.zac.portfolio.backend.model;

/**
 * The authenticated caller. Exactly one of {@code userId} and {@code shareId} is ever set: a
 * session principal owns data and has a {@code userId}; a share-link holder ("flyby") owns nothing
 * and has only a {@code shareId}.
 *
 * <p>That split is the safety property. A flyby has no {@code userId} by construction, so every
 * identity-bearing endpoint refuses it through the single {@code Principals.isRealUser} guard,
 * rather than each one having to know that shares exist.
 */
public record AuthPrincipal(
    Long userId, String email, boolean isAdmin, boolean mfaSatisfied, Long shareId) {

  /**
   * A session principal, carrying no share. Kept so the pre-share four-argument shape still
   * compiles at the 30 existing call sites; new code should prefer {@link #client} or the canonical
   * constructor.
   */
  public AuthPrincipal(Long userId, String email, boolean isAdmin, boolean mfaSatisfied) {
    this(userId, email, isAdmin, mfaSatisfied, null);
  }

  /**
   * Constructs a non-admin (client) principal. Prefer this over the full constructor at new call
   * sites: {@code isAdmin} and {@code mfaSatisfied} are adjacent, same-typed booleans with no
   * compiler-enforced positional check, so a transposed pair compiles cleanly and silently grants
   * or denies admin. This factory pins {@code isAdmin=false} by name instead of position.
   */
  public static AuthPrincipal client(Long userId, String email, boolean mfaSatisfied) {
    return new AuthPrincipal(userId, email, false, mfaSatisfied, null);
  }

  /**
   * Constructs a share-link holder. No {@code userId}, no email, never an admin, never MFA-
   * satisfied -- a guest carrying an allowlist, not a borrower of the sharer's grants.
   *
   * @param shareId the {@code share_link.id} whose scope bounds this principal
   */
  public static AuthPrincipal flyby(Long shareId) {
    return new AuthPrincipal(null, null, false, false, shareId);
  }
}
