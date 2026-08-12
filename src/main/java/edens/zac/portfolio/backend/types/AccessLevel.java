package edens.zac.portfolio.backend.types;

/**
 * Per-collection access, as an ordered ladder. GENERAL = view-only; CLIENT adds download/tag/star;
 * COLLABORATOR adds curation edits (the /api/edit surface); ADMIN is a computed sentinel for global
 * admins (users.is_admin) -- it is never stored in role_collection (the V55 CHECK refuses it) and
 * never grantable.
 */
public enum AccessLevel {
  GENERAL(0),
  CLIENT(1),
  COLLABORATOR(2),
  ADMIN(3);

  private final int rank;

  AccessLevel(int rank) {
    this.rank = rank;
  }

  /** Explicit rank, not ordinal(): reordering constants cannot silently re-map permissions. */
  public int rank() {
    return rank;
  }

  /** True when this level grants at least {@code other}'s capabilities. */
  public boolean atLeast(AccessLevel other) {
    return rank >= other.rank;
  }
}
