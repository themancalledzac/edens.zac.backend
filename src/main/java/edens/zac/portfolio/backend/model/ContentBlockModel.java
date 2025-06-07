package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.ContentBlockType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
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
