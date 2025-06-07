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
@Table(name = "image_content_block")
@DiscriminatorValue("IMAGE")
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
    
    @Column(name = "image_url_raw") // TODO: move from RAW to FullSize
    private String imageUrlRaw;
    
    // Additional fields that might be useful
    @Column(name = "create_date")
    private String createDate; // Consider updating to LocalDateTime in future
    
    @Override
    public ContentBlockType getBlockType() {
        return ContentBlockType.IMAGE;
    }
}