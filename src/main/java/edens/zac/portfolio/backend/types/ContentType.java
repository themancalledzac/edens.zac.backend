package edens.zac.portfolio.backend.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ContentType {
  IMAGE,
  TEXT,
  GIF,
  COLLECTION;

  @JsonValue
  public String getValue() {
    return this.name();
  }

  /**
   * Jackson factory for every request carrying a contentType.
   *
   * <p>Throws rather than defaulting. This used to coerce anything unrecognised to TEXT, so a
   * typo'd discriminator produced a valid TEXT block instead of a 400 and the caller never learned
   * the value was wrong. Matches {@code CollectionVisibility.forValue}, including its tolerance of
   * lowercase input -- the serialized form is uppercase, so case variance is a client quirk while
   * an unknown name is a real error.
   */
  @JsonCreator
  public static ContentType forValue(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("ContentType value cannot be blank");
    }
    try {
      return ContentType.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Invalid ContentType: " + value + ". Valid values: IMAGE, TEXT, GIF, COLLECTION.");
    }
  }
}
