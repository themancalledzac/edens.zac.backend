package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.ContentType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "gif_content_block")
@PrimaryKeyJoinColumn(name = "content_block_id")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class GifContentEntity extends ContentEntity {

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

    // Many-to-many relationship with ContentTagEntity
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "gif_content_block_tags",
            joinColumns = @JoinColumn(name = "gif_block_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"),
            indexes = {
                    @Index(name = "idx_gif_block_tags_gif", columnList = "gif_block_id"),
                    @Index(name = "idx_gif_block_tags_tag", columnList = "tag_id")
            }
    )
    @Builder.Default
    private Set<ContentTagEntity> tags = new HashSet<>();

    @Override
    public ContentType getContentType() {
        return ContentType.GIF;
    }
}