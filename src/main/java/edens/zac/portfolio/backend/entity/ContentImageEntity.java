package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.ContentType;
import edens.zac.portfolio.backend.types.FilmFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;

/**
 * Entity representing image content.
 * Extends ContentEntity (JOINED inheritance - base table: content, child table: content_image).
 * 
 * Database table: content_image
 * Primary key: id (inherited from content table, FK to content.id)
 * 
 * Foreign keys:
 *   - lens_id -> content_lenses.id
 *   - film_type_id -> content_film_types.id
 *   - camera_id -> content_cameras.id
 * 
 * Join tables:
 *   - content_image_tags (image_id, tag_id) - many-to-many with tags
 *   - content_image_people (image_id, person_id) - many-to-many with people
 * 
 * Indexes on join tables:
 *   - idx_image_tags_image (image_id)
 *   - idx_image_tags_tag (tag_id)
 *   - idx_image_people_image (image_id)
 *   - idx_image_people_person (person_id)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class ContentImageEntity extends ContentEntity {

    /** Column: title (VARCHAR) */
    private String title;

    /** Column: image_width (INTEGER) */
    private Integer imageWidth;

    /** Column: image_height (INTEGER) */
    private Integer imageHeight;

    /** Column: iso (INTEGER) */
    private Integer iso;

    /** Column: author (VARCHAR) */
    private String author;

    /** Column: rating (INTEGER) */
    private Integer rating;

    /** Column: f_stop (VARCHAR) */
    private String fStop;

    /** Column: lens_id (BIGINT, FK to content_lenses.id) */
    private ContentLensEntity lens;

    /** Column: black_and_white (BOOLEAN) */
    private Boolean blackAndWhite;

    /** Column: is_film (BOOLEAN) */
    private Boolean isFilm;

    /** Column: film_type_id (BIGINT, FK to content_film_types.id) */
    private ContentFilmTypeEntity filmType;

    /** Column: film_format (VARCHAR) - enum: 35MM, 120, 4X5, etc. */
    private FilmFormat filmFormat;

    /** Column: shutter_speed (VARCHAR) */
    private String shutterSpeed;

    /** Column: camera_id (BIGINT, FK to content_cameras.id) */
    private ContentCameraEntity camera;

    /** Column: focal_length (VARCHAR) */
    private String focalLength;

    /** Column: location (VARCHAR) */
    private String location;

    /** Column: image_url_web (VARCHAR, NOT NULL) - S3 URL for web-optimized image */
    @NotNull
    private String imageUrlWeb;

    /** Column: image_url_original (VARCHAR) - S3 URL for original full-size image */
    private String imageUrlOriginal;

    /** Column: create_date (VARCHAR) - EXIF date string */
    private String createDate;

    /** Column: file_identifier (VARCHAR, UNIQUE) - Format: "YYYY-MM-DD/filename.jpg" */
    private String fileIdentifier;

    /** Relationship: Many-to-many with ContentTagEntity (via content_image_tags table) */
    @Builder.Default
    private Set<ContentTagEntity> tags = new HashSet<>();

    /** Relationship: Many-to-many with ContentPersonEntity (via content_image_people table) */
    @Builder.Default
    private Set<ContentPersonEntity> people = new HashSet<>();

    @Override
    public ContentType getContentType() {
        return ContentType.IMAGE;
    }
}