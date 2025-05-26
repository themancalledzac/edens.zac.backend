package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.ContentBlockType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "gif_content_block")
@DiscriminatorValue("GIF")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class GifContentBlockEntity extends ContentBlockEntity {

    private String title;
    
    // S3 URL fields
    @NotNull
    @Column(name = "gif_url")
    private String gifUrl;
    
    @Column(name = "thumbnail_url")
    private String thumbnailUrl;
    
    // Optional metadata
    @Column(name = "width")
    private Integer width;
    
    @Column(name = "height")
    private Integer height;
    
    @Column(name = "author")
    private String author;
    
    @Column(name = "create_date")
    private String createDate;
    
    @Override
    public ContentBlockType getBlockType() {
        return ContentBlockType.GIF;
    }
}