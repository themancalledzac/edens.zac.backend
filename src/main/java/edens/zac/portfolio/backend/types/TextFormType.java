package edens.zac.portfolio.backend.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Enum representing different text format types for text content. Used for categorizing the format
 * of text blocks (markdown, html, plain).
 */
@Slf4j
public enum TextFormType {
  MARKDOWN("markdown"),
  HTML("html"),
  PLAIN("plain"),
  JS("js"),
  PY("py"),
  SQL("sql"),
  JAVA("java"),
  TS("ts"),
  TF("tf"),
  YML("yml");

  @NotNull @Getter private final String displayName;

  TextFormType(String displayName) {
    this.displayName = displayName;
  }

  /**
   * Returns the display name for JSON serialization
   *
   * @return display name (e.g., "markdown")
   */
  @JsonValue
  public String getValue() {
    return this.displayName;
  }

  /**
   * Creates TextFormType from string value for JSON deserialization
   *
   * @param value the string value
   * @return TextFormType enum or null if invalid
   */
  @JsonCreator
  public static TextFormType forValue(String value) {
    if (value == null) {
      log.warn("Null TextFormType value provided");
      return null;
    }

    try {
      // Try to match by enum name first
      return TextFormType.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      // Try to match by display name
      for (TextFormType type : TextFormType.values()) {
        if (type.displayName.equalsIgnoreCase(value)) {
          return type;
        }
      }
      log.warn("Invalid TextFormType value: {}", value);
      return null;
    }
  }
}
