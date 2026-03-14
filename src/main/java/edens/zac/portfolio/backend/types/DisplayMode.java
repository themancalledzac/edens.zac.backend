package edens.zac.portfolio.backend.types;

/** Display mode for ordering content in a collection. */
public enum DisplayMode {
  /** Content ordered by capture/creation time (oldest to newest). */
  CHRONOLOGICAL,
  /** Content ordered by explicit orderIndex in the join table. */
  ORDERED
}
