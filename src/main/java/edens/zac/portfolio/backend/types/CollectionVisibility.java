package edens.zac.portfolio.backend.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;

/**
 * 3-state visibility for collections. Replaces the legacy {@code visible} boolean as of V20.
 *
 * <p>LISTED - appears in list endpoints, searchable, allowed as visible child collection.
 *
 * <p>UNLISTED - direct slug access only (still requires password if password_hash set).
 *
 * <p>HIDDEN - dev environment only; returns 404 for any non-local request.
 */
public enum CollectionVisibility {
  LISTED,
  UNLISTED,
  HIDDEN;

  private static final List<CollectionVisibility> PROD_SCOPE = List.of(LISTED);
  private static final List<CollectionVisibility> LOCAL_SCOPE = List.of(LISTED, UNLISTED, HIDDEN);

  /**
   * The visibility scope a viewer may browse: prod sees LISTED only; a local/dev environment
   * additionally sees UNLISTED and HIDDEN. Single source of truth for the {@code
   * isLocalEnvironment} ternary previously duplicated across the resolvers and {@code TagService}.
   */
  public static List<CollectionVisibility> visibleScope(boolean isLocalEnvironment) {
    return isLocalEnvironment ? LOCAL_SCOPE : PROD_SCOPE;
  }

  public boolean appearsInLists() {
    return this == LISTED;
  }

  public boolean requiresLocalEnv() {
    return this == HIDDEN;
  }

  @JsonValue
  public String getValue() {
    return name();
  }

  @JsonCreator
  public static CollectionVisibility forValue(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("CollectionVisibility value cannot be blank");
    }
    try {
      return CollectionVisibility.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Invalid CollectionVisibility: " + value + ". Valid values: LISTED, UNLISTED, HIDDEN.");
    }
  }
}
