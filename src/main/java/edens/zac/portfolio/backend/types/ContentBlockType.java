package edens.zac.portfolio.backend.types;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum ContentBlockType {
    IMAGE("Image"),
    TEXT("Text"),
    GIF("Gif"),
    CODE("Code");

    @NotNull
    @Getter
    private final String contentName;

    ContentBlockType(String contentName) {
        this.contentName = contentName;
    }

    @JsonValue
    public String getValue() {
        return this.name();
    }

    @JsonCreator
    public static ContentBlockType forValue(String value) {
        if (value == null) {
            log.warn("Null ContentBlockType value, defaulting to 'Text'");
            return TEXT;
        }

        try {
            return ContentBlockType.valueOf(value);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid ContentBlockType value: {}, defaulting to TEXT", value);
            return TEXT;
        }
    }
}
