package edens.zac.portfolio.backend.types;

/** How a role came to exist. PERSONAL = auto-migrated per-user default; SHARED = admin-curated. */
public enum RoleKind {
  PERSONAL,
  SHARED
}
