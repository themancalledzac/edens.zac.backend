package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.ContentType;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;

/**
 * Entity representing GIF content.
 * Extends ContentEntity (JOINED inheritance - base table: content, child table: content_gif).
 * 
 * Database table: content_gif
 * Primary key: id (inherited from content table, FK to content.id)
 * 
 * Join tables:
 *   - content_gif_tags (gif_id, tag_id) - many-to-many with tags
 * 
 * Indexes on join table:
 *   - idx_gif_tags_gif (gif_id)
 *   - idx_gif_tags_tag (tag_id)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class ContentGifEntity extends ContentEntity {

    /** Column: title (VARCHAR) */
    private String title;
    
    /** Column: gif_url (VARCHAR, NOT NULL) - S3 URL for GIF */
    @NotNull
    private String gifUrl;
    
    /** Column: thumbnail_url (VARCHAR) - S3 URL for thumbnail */
    private String thumbnailUrl;
    
    /** Column: width (INTEGER) */
    private Integer width;
    
    /** Column: height (INTEGER) */
    private Integer height;
    
    /** Column: author (VARCHAR) */
    private String author;
    
    /** Column: create_date (VARCHAR) */
    private String createDate;

    /** Relationship: Many-to-many with ContentTagEntity (via content_gif_tags table) */
    @Builder.Default
    private Set<ContentTagEntity> tags = new HashSet<>();

    @Override
    public ContentType getContentType() {
        return ContentType.GIF;
    }
}