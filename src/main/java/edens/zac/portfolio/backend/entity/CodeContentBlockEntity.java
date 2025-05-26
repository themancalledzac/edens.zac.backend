package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.ContentBlockType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "code_content_block")
@DiscriminatorValue("CODE")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class CodeContentBlockEntity extends ContentBlockEntity {

    private String title;
    
    @NotNull
    @Column(name = "language")
    private String language;
    
    @NotNull
    @Lob
    @Column(name = "code", columnDefinition = "TEXT")
    private String code;
    
    @Override
    public ContentBlockType getBlockType() {
        return ContentBlockType.CODE;
    }
}