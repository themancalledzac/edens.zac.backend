package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.types.ContentType;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DAO for ContentEntity and its subclasses using raw SQL queries. Handles
 * JOINED inheritance
 * pattern (content + content_image, content_text, etc.). Replaces
 * ContentRepository.
 */
@Component
@Slf4j
public class ContentDao extends BaseDao {

  public ContentDao(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  /**
   * RowMapper for ContentImageEntity (JOINED inheritance). Joins content and
   * content_image tables.
   */
  private static final RowMapper<ContentImageEntity> CONTENT_IMAGE_ROW_MAPPER = (rs, rowNum) -> {
    ContentImageEntity entity = ContentImageEntity.builder()
        .id(rs.getLong("id"))
        .contentType(ContentType.IMAGE)
        .title(getString(rs, "title"))
        .imageWidth(getInteger(rs, "image_width"))
        .imageHeight(getInteger(rs, "image_height"))
        .iso(getInteger(rs, "iso"))
        .author(getString(rs, "author"))
        .rating(getInteger(rs, "rating"))
        .fStop(getString(rs, "f_stop"))
        .blackAndWhite(getBoolean(rs, "black_and_white"))
        .isFilm(getBoolean(rs, "is_film"))
        .filmFormat(
            rs.getString("film_format") != null
                ? edens.zac.portfolio.backend.types.FilmFormat.valueOf(
                    rs.getString("film_format"))
                : null)
        .shutterSpeed(getString(rs, "shutter_speed"))
        .focalLength(getString(rs, "focal_length"))
        .locationId(getLong(rs, "location_id"))
        .imageUrlWeb(rs.getString("image_url_web"))
        .imageUrlOriginal(getString(rs, "image_url_original"))
        .createDate(getString(rs, "create_date"))
        .fileIdentifier(getString(rs, "file_identifier"))
        .createdAt(getLocalDateTime(rs, "created_at"))
        .updatedAt(getLocalDateTime(rs, "updated_at"))
        .tags(new HashSet<>())
        .people(new HashSet<>())
        .build();

    // Load camera, lens, and filmType with names from LEFT JOINs
    Long cameraId = getLong(rs, "camera_id");
    if (cameraId != null) {
      entity.setCamera(
          edens.zac.portfolio.backend.entity.ContentCameraEntity.builder()
              .id(cameraId)
              .cameraName(getString(rs, "camera_name"))
              .build());
    }

    Long lensId = getLong(rs, "lens_id");
    if (lensId != null) {
      entity.setLens(
          edens.zac.portfolio.backend.entity.ContentLensEntity.builder()
              .id(lensId)
              .lensName(getString(rs, "lens_name"))
              .build());
    }

    Long filmTypeId = getLong(rs, "film_type_id");
    if (filmTypeId != null) {
      entity.setFilmType(
          edens.zac.portfolio.backend.entity.ContentFilmTypeEntity.builder()
              .id(filmTypeId)
              .filmTypeName(getString(rs, "film_type_name"))
              .displayName(getString(rs, "film_type_display_name"))
              .defaultIso(getInteger(rs, "default_iso"))
              .build());
    }

    return entity;
  };

  private static final RowMapper<edens.zac.portfolio.backend.entity.ContentTextEntity> CONTENT_TEXT_ROW_MAPPER = (rs,
      rowNum) -> edens.zac.portfolio.backend.entity.ContentTextEntity.builder()
          .id(rs.getLong("id"))
          .contentType(ContentType.TEXT)
          .textContent(rs.getString("text_content"))
          .formatType(getString(rs, "format_type"))
          .createdAt(getLocalDateTime(rs, "created_at"))
          .updatedAt(getLocalDateTime(rs, "updated_at"))
          .build();

  private static final RowMapper<edens.zac.portfolio.backend.entity.ContentGifEntity> CONTENT_GIF_ROW_MAPPER = (rs,
      rowNum) -> edens.zac.portfolio.backend.entity.ContentGifEntity.builder()
          .id(rs.getLong("id"))
          .contentType(ContentType.GIF)
          .title(getString(rs, "title"))
          .gifUrl(rs.getString("gif_url"))
          .thumbnailUrl(getString(rs, "thumbnail_url"))
          .width(getInteger(rs, "width"))
          .height(getInteger(rs, "height"))
          .author(getString(rs, "author"))
          .createDate(getString(rs, "create_date"))
          .createdAt(getLocalDateTime(rs, "created_at"))
          .updatedAt(getLocalDateTime(rs, "updated_at"))
          .tags(new HashSet<>())
          .build();

  private static final RowMapper<ContentCollectionEntity> CONTENT_COLLECTION_ROW_MAPPER = (rs, rowNum) -> {
    edens.zac.portfolio.backend.entity.CollectionEntity referencedCollection = new edens.zac.portfolio.backend.entity.CollectionEntity();
    referencedCollection.setId(rs.getLong("referenced_collection_id"));
    return ContentCollectionEntity.builder()
        .id(rs.getLong("id"))
        .contentType(ContentType.COLLECTION)
        .referencedCollection(referencedCollection)
        .createdAt(getLocalDateTime(rs, "created_at"))
        .updatedAt(getLocalDateTime(rs, "updated_at"))
        .build();
  };

  private static final String SELECT_CONTENT_IMAGE = """
      SELECT c.id, c.content_type, c.created_at, c.updated_at,
             ci.title, ci.image_width, ci.image_height, ci.iso, ci.author, ci.rating,
             ci.f_stop, ci.lens_id, ci.black_and_white, ci.is_film, ci.film_type_id,
             ci.film_format, ci.shutter_speed, ci.camera_id, ci.focal_length,
             ci.location_id,
             ci.image_url_web, ci.image_url_original, ci.create_date, ci.file_identifier,
             cam.camera_name,
             lens.lens_name,
             ft.film_type_name, ft.display_name as film_type_display_name, ft.default_iso
      FROM content c
      JOIN content_image ci ON c.id = ci.id
      LEFT JOIN content_cameras cam ON ci.camera_id = cam.id
      LEFT JOIN content_lenses lens ON ci.lens_id = lens.id
      LEFT JOIN content_film_types ft ON ci.film_type_id = ft.id
      """;

  private static final String SELECT_CONTENT_TEXT = """
      SELECT c.id, c.content_type, c.created_at, c.updated_at,
             ct.text_content, ct.format_type
      FROM content c
      JOIN content_text ct ON c.id = ct.id
      """;

  private static final String SELECT_CONTENT_GIF = """
      SELECT c.id, c.content_type, c.created_at, c.updated_at,
             cg.title, cg.gif_url, cg.thumbnail_url, cg.width, cg.height,
             cg.author, cg.create_date
      FROM content c
      JOIN content_gif cg ON c.id = cg.id
      """;

  private static final String SELECT_CONTENT_COLLECTION = """
      SELECT c.id, c.content_type, c.created_at, c.updated_at,
             cc.referenced_collection_id
      FROM content c
      JOIN content_collection cc ON c.id = cc.id
      """;

  /** Check if image with fileIdentifier exists. */
  @Transactional(readOnly = true)
  public boolean existsByFileIdentifier(String fileIdentifier) {
    String sql = "SELECT COUNT(*) > 0 FROM content_image WHERE file_identifier = :fileIdentifier";
    MapSqlParameterSource params = createParameterSource().addValue("fileIdentifier", fileIdentifier);
    Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
    return result != null && result;
  }

  /** Find all ContentImageEntity by fileIdentifier. */
  @Transactional(readOnly = true)
  public List<ContentImageEntity> findAllByFileIdentifier(String fileIdentifier) {
    String sql = SELECT_CONTENT_IMAGE + " WHERE ci.file_identifier = :fileIdentifier";
    MapSqlParameterSource params = createParameterSource().addValue("fileIdentifier", fileIdentifier);
    return query(sql, CONTENT_IMAGE_ROW_MAPPER, params);
  }

  /** Find ContentImageEntity by ID. */
  @Transactional(readOnly = true)
  public Optional<ContentImageEntity> findImageById(Long id) {
    String sql = SELECT_CONTENT_IMAGE + " WHERE c.id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, CONTENT_IMAGE_ROW_MAPPER, params);
  }

  /**
   * Batch fetch multiple images by IDs in a single query. More efficient than
   * calling findImageById
   * in a loop (avoids N+1).
   */
  @Transactional(readOnly = true)
  public List<ContentImageEntity> findImagesByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    String sql = SELECT_CONTENT_IMAGE + " WHERE c.id IN (:ids)";
    MapSqlParameterSource params = createParameterSource().addValue("ids", ids);
    return query(sql, CONTENT_IMAGE_ROW_MAPPER, params);
  }

  /**
   * Find all images ordered by createDate DESC. Note: Relationships (tags,
   * people, camera, lens,
   * filmType) loaded separately.
   */
  @Transactional(readOnly = true)
  public List<ContentImageEntity> findAllImagesOrderByCreateDateDesc() {
    String sql = SELECT_CONTENT_IMAGE + " ORDER BY ci.create_date DESC NULLS LAST, c.created_at DESC";
    return query(sql, CONTENT_IMAGE_ROW_MAPPER);
  }

  /**
   * Find images with pagination, ordered by createDate DESC. Uses database-level
   * LIMIT and OFFSET
   * for efficient pagination.
   */
  @Transactional(readOnly = true)
  public List<ContentImageEntity> findAllImagesOrderByCreateDateDesc(int limit, int offset) {
    String sql = SELECT_CONTENT_IMAGE
        + " ORDER BY ci.create_date DESC NULLS LAST, c.created_at DESC LIMIT :limit OFFSET :offset";
    MapSqlParameterSource params = createParameterSource().addValue("limit", limit).addValue("offset", offset);
    return query(sql, CONTENT_IMAGE_ROW_MAPPER, params);
  }

  /** Count total number of images. */
  @Transactional(readOnly = true)
  public int countImages() {
    String sql = "SELECT COUNT(*) FROM content WHERE content_type = 'IMAGE'";
    Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
    return count != null ? count : 0;
  }

  /** Find all content IDs by contentType. */
  @Transactional(readOnly = true)
  public List<Long> findIdsByContentType(String contentType) {
    String sql = "SELECT id FROM content WHERE content_type = :contentType";
    MapSqlParameterSource params = createParameterSource().addValue("contentType", contentType);
    return namedParameterJdbcTemplate.queryForList(sql, params, Long.class);
  }

  /**
   * Find all ContentEntity by IDs, loading the fully-typed entities. Returns
   * ContentImageEntity,
   * ContentTextEntity, ContentGifEntity, or ContentCollectionEntity based on each
   * entity's
   * content_type.
   *
   * <p>
   * IMPORTANT: This performs type-specific JOINs to load complete entity data.
   */
  @Transactional(readOnly = true)
  public List<ContentEntity> findAllByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }

    // First, get the content_type for each ID to determine which tables to query
    String typeSql = "SELECT id, content_type FROM content WHERE id IN (:ids)";
    MapSqlParameterSource typeParams = createParameterSource().addValue("ids", ids);
    List<java.util.Map<String, Object>> typeRows = namedParameterJdbcTemplate.queryForList(typeSql, typeParams);

    // Group IDs by content type
    java.util.Map<ContentType, List<Long>> idsByType = new java.util.HashMap<>();
    for (java.util.Map<String, Object> row : typeRows) {
      Long id = ((Number) row.get("id")).longValue();
      ContentType type = ContentType.valueOf((String) row.get("content_type"));
      idsByType.computeIfAbsent(type, k -> new java.util.ArrayList<>()).add(id);
    }

    List<ContentEntity> results = new java.util.ArrayList<>();

    // Load each type from its specific table
    if (idsByType.containsKey(ContentType.IMAGE)) {
      String imageSql = SELECT_CONTENT_IMAGE + " WHERE c.id IN (:ids)";
      MapSqlParameterSource imageParams = createParameterSource().addValue("ids", idsByType.get(ContentType.IMAGE));
      results.addAll(query(imageSql, CONTENT_IMAGE_ROW_MAPPER, imageParams));
    }

    if (idsByType.containsKey(ContentType.TEXT)) {
      String sql = SELECT_CONTENT_TEXT + " WHERE c.id IN (:ids)";
      MapSqlParameterSource params = createParameterSource().addValue("ids", idsByType.get(ContentType.TEXT));
      results.addAll(query(sql, CONTENT_TEXT_ROW_MAPPER, params));
    }

    if (idsByType.containsKey(ContentType.GIF)) {
      String sql = SELECT_CONTENT_GIF + " WHERE c.id IN (:ids)";
      MapSqlParameterSource params = createParameterSource().addValue("ids", idsByType.get(ContentType.GIF));
      results.addAll(query(sql, CONTENT_GIF_ROW_MAPPER, params));
    }

    if (idsByType.containsKey(ContentType.COLLECTION)) {
      String sql = SELECT_CONTENT_COLLECTION + " WHERE c.id IN (:ids)";
      MapSqlParameterSource params = createParameterSource().addValue("ids", idsByType.get(ContentType.COLLECTION));
      results.addAll(query(sql, CONTENT_COLLECTION_ROW_MAPPER, params));
    }

    return results;
  }

  /** Find ContentCollectionEntity by referenced collection ID. */
  @Transactional(readOnly = true)
  public Optional<ContentCollectionEntity> findContentCollectionByReferencedCollectionId(
      Long referencedCollectionId) {
    String sql = """
        SELECT c.id, c.content_type, c.created_at, c.updated_at, cc.referenced_collection_id
        FROM content c
        JOIN content_collection cc ON c.id = cc.id
        WHERE cc.referenced_collection_id = :referencedCollectionId
        """;

    MapSqlParameterSource params = createParameterSource().addValue("referencedCollectionId", referencedCollectionId);
    return queryForObject(
        sql,
        (rs, rowNum) -> {
          ContentCollectionEntity entity = ContentCollectionEntity.builder()
              .id(rs.getLong("id"))
              .contentType(ContentType.COLLECTION)
              .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
              .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
              .referencedCollection(
                  new edens.zac.portfolio.backend.entity.CollectionEntity() {
                    {
                      setId(rs.getLong("referenced_collection_id"));
                    }
                  })
              .build();
          return entity;
        },
        params);
  }

  /**
   * Save ContentImageEntity. IMPORTANT: Must be wrapped in @Transactional.
   * Inserts into content
   * first, then content_image using the same ID.
   */
  @Transactional
  public ContentImageEntity saveImage(ContentImageEntity entity) {
    LocalDateTime now = LocalDateTime.now();

    if (entity.getId() == null) {
      // Step 1: Insert into content table
      String contentSql = """
          INSERT INTO content (content_type, created_at, updated_at)
          VALUES (:contentType, :createdAt, :updatedAt)
          """;

      MapSqlParameterSource contentParams = createParameterSource()
          .addValue("contentType", ContentType.IMAGE.name())
          .addValue("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt() : now)
          .addValue("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt() : now);

      Long contentId = insertAndReturnId(contentSql, "id", contentParams);

      // Step 2: Insert into content_image using the same ID
      String imageSql = """
          INSERT INTO content_image (id, title, image_width, image_height, iso, author, rating,
                                    f_stop, lens_id, black_and_white, is_film, film_type_id,
                                    film_format, shutter_speed, camera_id, focal_length,
                                    location_id,
                                    image_url_web, image_url_original, create_date, file_identifier)
          VALUES (:id, :title, :imageWidth, :imageHeight, :iso, :author, :rating,
                  :fStop, :lensId, :blackAndWhite, :isFilm, :filmTypeId,
                  :filmFormat, :shutterSpeed, :cameraId, :focalLength,
                  :locationId,
                  :imageUrlWeb, :imageUrlOriginal, :createDate, :fileIdentifier)
          """;

      MapSqlParameterSource imageParams = createParameterSource()
          .addValue("id", contentId)
          .addValue("title", entity.getTitle())
          .addValue("imageWidth", entity.getImageWidth())
          .addValue("imageHeight", entity.getImageHeight())
          .addValue("iso", entity.getIso())
          .addValue("author", entity.getAuthor())
          .addValue("rating", entity.getRating())
          .addValue("fStop", entity.getFStop())
          .addValue("lensId", entity.getLens() != null ? entity.getLens().getId() : null)
          .addValue("blackAndWhite", entity.getBlackAndWhite())
          .addValue("isFilm", entity.getIsFilm())
          .addValue(
              "filmTypeId", entity.getFilmType() != null ? entity.getFilmType().getId() : null)
          .addValue(
              "filmFormat",
              entity.getFilmFormat() != null ? entity.getFilmFormat().name() : null)
          .addValue("shutterSpeed", entity.getShutterSpeed())
          .addValue("cameraId", entity.getCamera() != null ? entity.getCamera().getId() : null)
          .addValue("focalLength", entity.getFocalLength())
          .addValue("locationId", entity.getLocationId())
          .addValue("imageUrlWeb", entity.getImageUrlWeb())
          .addValue("imageUrlOriginal", entity.getImageUrlOriginal())
          .addValue("createDate", entity.getCreateDate())
          .addValue("fileIdentifier", entity.getFileIdentifier());

      update(imageSql, imageParams);

      entity.setId(contentId);
      if (entity.getCreatedAt() == null) {
        entity.setCreatedAt(now);
      }
      if (entity.getUpdatedAt() == null) {
        entity.setUpdatedAt(now);
      }

      return entity;
    } else {
      // Update existing
      String contentSql = """
          UPDATE content
          SET updated_at = :updatedAt
          WHERE id = :id
          """;
      MapSqlParameterSource contentParams = createParameterSource().addValue("updatedAt", now).addValue("id",
          entity.getId());
      update(contentSql, contentParams);

      String imageSql = """
          UPDATE content_image
          SET title = :title, image_width = :imageWidth, image_height = :imageHeight, iso = :iso,
              author = :author, rating = :rating, f_stop = :fStop, lens_id = :lensId,
              black_and_white = :blackAndWhite, is_film = :isFilm, film_type_id = :filmTypeId,
              film_format = :filmFormat, shutter_speed = :shutterSpeed, camera_id = :cameraId,
              focal_length = :focalLength, location_id = :locationId,
              image_url_web = :imageUrlWeb, image_url_original = :imageUrlOriginal,
              create_date = :createDate, file_identifier = :fileIdentifier
          WHERE id = :id
          """;

      MapSqlParameterSource imageParams = createParameterSource()
          .addValue("id", entity.getId())
          .addValue("title", entity.getTitle())
          .addValue("imageWidth", entity.getImageWidth())
          .addValue("imageHeight", entity.getImageHeight())
          .addValue("iso", entity.getIso())
          .addValue("author", entity.getAuthor())
          .addValue("rating", entity.getRating())
          .addValue("fStop", entity.getFStop())
          .addValue("lensId", entity.getLens() != null ? entity.getLens().getId() : null)
          .addValue("blackAndWhite", entity.getBlackAndWhite())
          .addValue("isFilm", entity.getIsFilm())
          .addValue(
              "filmTypeId", entity.getFilmType() != null ? entity.getFilmType().getId() : null)
          .addValue(
              "filmFormat",
              entity.getFilmFormat() != null ? entity.getFilmFormat().name() : null)
          .addValue("shutterSpeed", entity.getShutterSpeed())
          .addValue("cameraId", entity.getCamera() != null ? entity.getCamera().getId() : null)
          .addValue("focalLength", entity.getFocalLength())
          .addValue("locationId", entity.getLocationId())
          .addValue("imageUrlWeb", entity.getImageUrlWeb())
          .addValue("imageUrlOriginal", entity.getImageUrlOriginal())
          .addValue("createDate", entity.getCreateDate())
          .addValue("fileIdentifier", entity.getFileIdentifier());

      update(imageSql, imageParams);

      entity.setUpdatedAt(now);
      return entity;
    }
  }

  /**
   * Save image people (many-to-many relationship). Deletes existing people and
   * inserts new ones.
   */
  @Transactional
  public void saveImagePeople(Long imageId, List<Long> personIds) {
    // Delete existing people
    String deleteSql = "DELETE FROM content_image_people WHERE image_id = :imageId";
    MapSqlParameterSource deleteParams = createParameterSource().addValue("imageId", imageId);
    update(deleteSql, deleteParams);

    // Insert new people
    if (personIds != null && !personIds.isEmpty()) {
      String insertSql = "INSERT INTO content_image_people (image_id, person_id) VALUES (:imageId, :personId)";
      MapSqlParameterSource[] batchParams = personIds.stream()
          .map(
              personId -> createParameterSource()
                  .addValue("imageId", imageId)
                  .addValue("personId", personId))
          .toArray(MapSqlParameterSource[]::new);
      batchUpdate(insertSql, batchParams);
    }
  }

  /** Load people for an image. */
  @Transactional(readOnly = true)
  public List<Long> findImagePersonIds(Long imageId) {
    String sql = "SELECT person_id FROM content_image_people WHERE image_id = :imageId";
    MapSqlParameterSource params = createParameterSource().addValue("imageId", imageId);
    return namedParameterJdbcTemplate.queryForList(sql, params, Long.class);
  }

  /**
   * Batch fetch person IDs for multiple images. Returns a map of imageId -> list
   * of person IDs.
   * More efficient than calling findImagePersonIds in a loop (avoids N+1).
   *
   * @param imageIds List of image IDs
   * @return Map of image ID to list of person IDs
   */
  @Transactional(readOnly = true)
  public java.util.Map<Long, List<Long>> findPersonIdsByImageIds(List<Long> imageIds) {
    if (imageIds == null || imageIds.isEmpty()) {
      return java.util.Map.of();
    }

    String sql = "SELECT image_id, person_id FROM content_image_people WHERE image_id IN (:imageIds)";
    MapSqlParameterSource params = createParameterSource().addValue("imageIds", imageIds);

    java.util.Map<Long, List<Long>> result = new java.util.HashMap<>();
    namedParameterJdbcTemplate.query(
        sql,
        params,
        rs -> {
          Long imageId = rs.getLong("image_id");
          Long personId = rs.getLong("person_id");
          result.computeIfAbsent(imageId, k -> new java.util.ArrayList<>()).add(personId);
        });

    return result;
  }

  /**
   * Delete ContentImageEntity by ID. Deletes from content_image first (child
   * table), then content
   * (parent table). Also deletes related tags and people associations. Note: Tags
   * are deleted via
   * content_tags table (handled by TagDao or cascade).
   */
  @Transactional
  public void deleteImageById(Long id) {
    MapSqlParameterSource params = createParameterSource().addValue("id", id);

    // Delete from many-to-many join tables first
    // Tags are deleted via content_tags (content.id = image.id for images)
    String deleteTagsSql = "DELETE FROM content_tags WHERE content_id = :id";
    update(deleteTagsSql, params);

    String deletePeopleSql = "DELETE FROM content_image_people WHERE image_id = :id";
    update(deletePeopleSql, params);

    // Delete from content_image (child table)
    String deleteImageSql = "DELETE FROM content_image WHERE id = :id";
    update(deleteImageSql, params);

    // Delete from content (parent table)
    String deleteContentSql = "DELETE FROM content WHERE id = :id";
    update(deleteContentSql, params);
  }
}
