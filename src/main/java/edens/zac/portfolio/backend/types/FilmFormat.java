package edens.zac.portfolio.backend.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Enum representing different film format types (35mm, 120, etc.)
 * Used for categorizing the physical format of film photography.
 */
@Slf4j
public enum FilmFormat {
    MM_35("35mm"),
    MM_120("120");

    @NotNull
    @Getter
    private final String displayName;

    FilmFormat(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the enum name for JSON serialization
     * @return enum name (e.g., "MM_35")
     */
    @JsonValue
    public String getValue() {
        return this.name();
    }

    /**
     * Creates FilmFormat from string value for JSON deserialization
     * @param value the string value
     * @return FilmFormat enum or null if invalid
     */
    @JsonCreator
    public static FilmFormat forValue(String value) {
        if (value == null) {
            log.warn("Null FilmFormat value provided");
            return null;
        }

        try {
            return FilmFormat.valueOf(value);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid FilmFormat value: {}", value);
            return null;
        }
    }
}
