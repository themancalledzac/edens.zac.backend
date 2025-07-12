package edens.zac.portfolio.backend.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import edens.zac.portfolio.backend.types.ContentBlockType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "blockType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ImageContentBlockModel.class, name = "IMAGE"),
    @JsonSubTypes.Type(value = TextContentBlockModel.class, name = "TEXT"),
    @JsonSubTypes.Type(value = CodeContentBlockModel.class, name = "CODE"),
    @JsonSubTypes.Type(value = GifContentBlockModel.class, name = "GIF")
})
public class ContentBlockModel {
    private Long id;

    @NotNull
    private Long collectionId;

    @NotNull
    @Min(0)
    private Integer orderIndex;

    @NotNull
    private ContentBlockType blockType;

    @Size(max = 500)
    private String caption;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
