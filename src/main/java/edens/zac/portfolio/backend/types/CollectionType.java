package edens.zac.portfolio.backend.types;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum CollectionType {
    BLOG("Blog"),
    ART_GALLERY("Art Gallery"),
    CLIENT_GALLERY("Client Gallery"),
    PORTFOLIO("Portfolio");

    @NotNull
    @Getter
    private final String displayName;

    CollectionType(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getValue() {
        return this.name();
    }

    @JsonCreator
    public static CollectionType forValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("CollectionType value cannot be null");
        }

        // First try exact match (for enum names like "BLOG")
        try {
            return CollectionType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Then try by display name (for values like "Blog")
            for (CollectionType type : CollectionType.values()) {
                if (type.getDisplayName().equalsIgnoreCase(value)) {
                    return type;
                }
            }

            // If no match found, throw exception instead of defaulting
            throw new IllegalArgumentException("Invalid CollectionType value: " + value +
                ". Valid values are: BLOG, ART_GALLERY, CLIENT_GALLERY, PORTFOLIO or their display names.");
        }
    }
}