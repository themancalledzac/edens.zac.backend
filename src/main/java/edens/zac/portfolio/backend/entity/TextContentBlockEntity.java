package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.ContentBlockType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "text_content_block")
@PrimaryKeyJoinColumn(name = "content_block_id")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class TextContentBlockEntity extends ContentBlockEntity {

    @NotNull
    @Lob
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "format_type")
    private String formatType; // Options: "markdown", "html", "plain"
    
    @Override
    public ContentBlockType getBlockType() {
        return ContentBlockType.TEXT;
    }
}