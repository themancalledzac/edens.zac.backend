package edens.zac.portfolio.backend.model;

public record AuthPrincipal(Long userId, String email, boolean isAdmin, boolean mfaSatisfied) {

  /**
   * Constructs a non-admin (client) principal. Prefer this over the full constructor at new call
   * sites: {@code isAdmin} and {@code mfaSatisfied} are adjacent, same-typed booleans with no
   * compiler-enforced positional check, so a transposed pair compiles cleanly and silently grants
   * or denies admin. This factory pins {@code isAdmin=false} by name instead of position.
   */
  public static AuthPrincipal client(Long userId, String email, boolean mfaSatisfied) {
    return new AuthPrincipal(userId, email, false, mfaSatisfied);
  }
}
