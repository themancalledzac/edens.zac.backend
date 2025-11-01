package edens.zac.portfolio.backend.types;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum ContentType {
    IMAGE("Image"),
    TEXT("Text"),
    GIF("Gif"),
    CODE("Code"),
    COLLECTION("Collection");

    @NotNull
    @Getter
    private final String contentName;

    ContentType(String contentName) {
        this.contentName = contentName;
    }

    @JsonValue
    public String getValue() {
        return this.name();
    }

    @JsonCreator
    public static ContentType forValue(String value) {
        if (value == null) {
            log.warn("Null ContentType value, defaulting to 'Text'");
            return TEXT;
        }

        try {
            return ContentType.valueOf(value);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid ContentType value: {}, defaulting to TEXT", value);
            return TEXT;
        }
    }
}
