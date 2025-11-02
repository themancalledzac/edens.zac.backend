package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.ContentType;
import jakarta.persistence.Column;
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
@Table(name = "content_text")
@PrimaryKeyJoinColumn(name = "content_id")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class ContentTextEntity extends ContentEntity {

    @NotNull
    @Lob
    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;
    
    @Column(name = "format_type")
    private String formatType; // Options: "markdown", "html", "plain", "js", "py", "sql", "java", "ts", "tf", "yml"
    
    @Override
    public ContentType getContentType() {
        return ContentType.TEXT;
    }
}