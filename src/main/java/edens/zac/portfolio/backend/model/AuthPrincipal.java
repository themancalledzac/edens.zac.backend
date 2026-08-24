package edens.zac.portfolio.backend.model;

/**
 * The authenticated caller. Exactly one of {@code userId} and {@code shareId} is ever set: a
 * session principal owns data and has a {@code userId}; a share-link holder ("flyby") owns nothing
 * and has only a {@code shareId}.
 *
 * <p>That split is the safety property. A flyby has no {@code userId} by construction and is
 * granted no authorities, so {@code SecurityConfig}'s {@code hasRole("USER")} matchers refuse it
 * from the whole identity-bearing surface without any endpoint having to know that shares exist.
 */
public record AuthPrincipal(
    Long userId, String email, boolean isAdmin, boolean mfaSatisfied, Long shareId) {

  /**
   * A session principal, carrying no share. This is the shape {@code SessionService} builds and the
   * one nearly every test uses; new code should prefer {@link #client} or the canonical
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

  /**
   * Whether this principal owns data. False for anonymous (null) and false for a share-link holder,
   * who has no {@code userId} to read or write anything against. This is the in-code equivalent of
   * the {@code hasRole("USER")} matchers; the self-scoped routes are gated at the chain, so what is
   * left here are the {@code /api/auth/**} handlers that branch on identity rather than reject on
   * it.
   *
   * <p>Written as a null-tolerant static rather than an instance method so the anonymous and flyby
   * cases collapse into one check at the call site. Endpoints that key on {@code userId()} must use
   * this instead of a bare {@code principal == null}: a flyby passes that older check and would
   * then hand a null id to a service, which for the write paths means inserting rows against no
   * user at all.
   */
  public static boolean isRealUser(AuthPrincipal principal) {
    return principal != null && principal.userId() != null;
  }
}
