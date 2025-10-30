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
@Table(name = "content_code")
@PrimaryKeyJoinColumn(name = "content_code_id")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class ContentCodeEntity extends ContentEntity {

    private String title;
    
    @NotNull
    @Column(name = "language")
    private String language;

    @NotNull
    @Lob
    @Column(name = "code", columnDefinition = "TEXT")
    private String code;
    
    @Override
    public ContentType getContentType() {
        return ContentType.CODE;
    }
}