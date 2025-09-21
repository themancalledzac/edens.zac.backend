package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.ContentBlockType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "image_content_block")
@PrimaryKeyJoinColumn(name = "content_block_id")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class ImageContentBlockEntity extends ContentBlockEntity {

    // Image metadata fields from existing ImageEntity
    private String title;
    private Integer imageWidth;
    private Integer imageHeight;
    private Integer iso;
    private String author;
    private Integer rating;
    private String fStop;
    private String lens;
    private Boolean blackAndWhite;
    private Boolean isFilm;
    private String shutterSpeed;
    private String rawFileName;
    private String camera;
    private String focalLength;
    private String location;
    
    // S3 URL fields
    @NotNull
    @Column(name = "image_url_web")
    private String imageUrlWeb;
    
    
    // Additional fields that might be useful
    @Column(name = "create_date")
    private String createDate; // Consider updating to LocalDateTime in future
    
    @Override
    public ContentBlockType getBlockType() {
        return ContentBlockType.IMAGE;
    }
}