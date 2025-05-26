package edens.zac.portfolio.backend.types;

import com.drew.lang.annotations.NotNull;
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
            log.warn("Null CollectionType value, defaulting to 'Portfolio'");
            return PORTFOLIO;
        }

        try {
            return CollectionType.valueOf(value);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid CollectionType value: {}, defaulting to PORTFOLIO", value);
            return PORTFOLIO;
        }
    }
}