package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.ContentBlockType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;

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
    private String camera;
    private String focalLength;
    private String location;

    // S3 URL fields
    @NotNull
    @Column(name = "image_url_web")
    private String imageUrlWeb;

    @Column(name = "image_url_full_size")
    private String imageUrlFullSize;
    
    
    // Additional fields that might be useful
    @Column(name = "create_date")
    private String createDate; // Consider updating to LocalDateTime in future

    // File identifier for duplicate detection (format: "YYYY-MM-DD/filename.jpg")
    @Column(name = "file_identifier", unique = true)
    private String fileIdentifier;

    // Many-to-many relationship with ContentTagEntity
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "image_content_block_tags",
            joinColumns = @JoinColumn(name = "image_block_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"),
            indexes = {
                    @Index(name = "idx_image_block_tags_image", columnList = "image_block_id"),
                    @Index(name = "idx_image_block_tags_tag", columnList = "tag_id")
            }
    )
    private Set<ContentTagEntity> tags = new HashSet<>();

    // Many-to-many relationship with ContentPersonEntity
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "image_content_block_people",
            joinColumns = @JoinColumn(name = "image_block_id"),
            inverseJoinColumns = @JoinColumn(name = "person_id"),
            indexes = {
                    @Index(name = "idx_image_block_people_image", columnList = "image_block_id"),
                    @Index(name = "idx_image_block_people_person", columnList = "person_id")
            }
    )
    private Set<ContentPersonEntity> people = new HashSet<>();

    @Override
    public ContentBlockType getBlockType() {
        return ContentBlockType.IMAGE;
    }
}