package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentCameraEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentFilmTypeEntity;
import edens.zac.portfolio.backend.entity.ContentGifEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.ContentLensEntity;
import edens.zac.portfolio.backend.entity.ContentTextEntity;
import edens.zac.portfolio.backend.types.ContentType;
import edens.zac.portfolio.backend.types.FilmFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for all content types: images, text, GIFs, and collection references. Consolidates
 * ContentDao, ContentGifDao, ContentTextDao, and ContentCollectionDao.
 */
@Component
@Slf4j
public class ContentRepository extends BaseDao {

  public ContentRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  // ============================================================
  // RowMappers
  // ============================================================

  private static final RowMapper<ContentImageEntity> CONTENT_IMAGE_ROW_MAPPER =
      (rs, rowNum) -> {
        ContentImageEntity entity =
            ContentImageEntity.builder()
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
                        ? FilmFormat.valueOf(rs.getString("film_format"))
                        : null)
                .shutterSpeed(getString(rs, "shutter_speed"))
                .focalLength(getString(rs, "focal_length"))
                .locationId(getLong(rs, "location_id"))
                .imageUrlWeb(rs.getString("image_url_web"))
                .imageUrlOriginal(getString(rs, "image_url_original"))
                .captureDate(
                    rs.getDate("capture_date") != null
                        ? rs.getDate("capture_date").toLocalDate()
                        : null)
                .lastExportDate(getLocalDateTime(rs, "last_export_date"))
                .originalFilename(getString(rs, "original_filename"))
                .createdAt(getLocalDateTime(rs, "created_at"))
                .updatedAt(getLocalDateTime(rs, "updated_at"))
                .tags(new HashSet<>())
                .people(new HashSet<>())
                .build();

        Long cameraId = getLong(rs, "camera_id");
        if (cameraId != null) {
          entity.setCamera(
              ContentCameraEntity.builder()
                  .id(cameraId)
                  .cameraName(getString(rs, "camera_name"))
                  .build());
        }

        Long lensId = getLong(rs, "lens_id");
        if (lensId != null) {
          entity.setLens(
              ContentLensEntity.builder().id(lensId).lensName(getString(rs, "lens_name")).build());
        }

        Long filmTypeId = getLong(rs, "film_type_id");
        if (filmTypeId != null) {
          entity.setFilmType(
              ContentFilmTypeEntity.builder()
                  .id(filmTypeId)
                  .filmTypeName(getString(rs, "film_type_name"))
                  .displayName(getString(rs, "film_type_display_name"))
                  .defaultIso(getInteger(rs, "default_iso"))
                  .build());
        }

        return entity;
      };

  private static final RowMapper<ContentTextEntity> CONTENT_TEXT_ROW_MAPPER =
      (rs, rowNum) ->
          ContentTextEntity.builder()
              .id(rs.getLong("id"))
              .contentType(ContentType.TEXT)
              .textContent(rs.getString("text_content"))
              .formatType(getString(rs, "format_type"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .updatedAt(getLocalDateTime(rs, "updated_at"))
              .build();

  private static final RowMapper<ContentGifEntity> CONTENT_GIF_ROW_MAPPER =
      (rs, rowNum) ->
          ContentGifEntity.builder()
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

  private static final RowMapper<ContentCollectionEntity> CONTENT_COLLECTION_ROW_MAPPER =
      (rs, rowNum) -> {
        CollectionEntity referencedCollection = new CollectionEntity();
        referencedCollection.setId(rs.getLong("referenced_collection_id"));
        return ContentCollectionEntity.builder()
            .id(rs.getLong("id"))
            .contentType(ContentType.COLLECTION)
            .referencedCollection(referencedCollection)
            .createdAt(getLocalDateTime(rs, "created_at"))
            .updatedAt(getLocalDateTime(rs, "updated_at"))
            .build();
      };

  // ============================================================
  // SELECT Fragments
  // ============================================================

  private static final String SELECT_CONTENT_IMAGE =
      """
      SELECT c.id, c.content_type, c.created_at, c.updated_at,
             ci.title, ci.image_width, ci.image_height, ci.iso, ci.author, ci.rating,
             ci.f_stop, ci.lens_id, ci.black_and_white, ci.is_film, ci.film_type_id,
             ci.film_format, ci.shutter_speed, ci.camera_id, ci.focal_length,
             ci.location_id,
             ci.image_url_web, ci.image_url_original,
             ci.capture_date, ci.last_export_date, ci.original_filename,
             cam.camera_name,
             lens.lens_name,
             ft.film_type_name, ft.display_name as film_type_display_name, ft.default_iso
      FROM content c
      JOIN content_image ci ON c.id = ci.id
      LEFT JOIN content_cameras cam ON ci.camera_id = cam.id
      LEFT JOIN content_lenses lens ON ci.lens_id = lens.id
      LEFT JOIN content_film_types ft ON ci.film_type_id = ft.id
      """;

  private static final String SELECT_CONTENT_TEXT =
      """
      SELECT c.id, c.content_type, c.created_at, c.updated_at,
             ct.text_content, ct.format_type
      FROM content c
      JOIN content_text ct ON c.id = ct.id
      """;

  private static final String SELECT_CONTENT_GIF =
      """
      SELECT c.id, c.content_type, c.created_at, c.updated_at,
             cg.title, cg.gif_url, cg.thumbnail_url, cg.width, cg.height,
             cg.author, cg.create_date
      FROM content c
      JOIN content_gif cg ON c.id = cg.id
      """;

  private static final String SELECT_CONTENT_COLLECTION =
      """
      SELECT c.id, c.content_type, c.created_at, c.updated_at,
             cc.referenced_collection_id
      FROM content c
      JOIN content_collection cc ON c.id = cc.id
      """;

  // ============================================================
  // Image Operations
  // ============================================================

  @Transactional(readOnly = true)
  public Optional<ContentImageEntity> findByOriginalFilenameAndCaptureDate(
      String originalFilename, LocalDate captureDate) {
    String sql =
        SELECT_CONTENT_IMAGE
            + " WHERE ci.original_filename = :originalFilename AND ci.capture_date = :captureDate";
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("originalFilename", originalFilename)
            .addValue("captureDate", captureDate);
    return queryForObject(sql, CONTENT_IMAGE_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentImageEntity> findByOriginalFilenames(List<String> filenames) {
    if (filenames == null || filenames.isEmpty()) {
      return List.of();
    }
    String sql = SELECT_CONTENT_IMAGE + " WHERE ci.original_filename IN (:filenames)";
    MapSqlParameterSource params = createParameterSource().addValue("filenames", filenames);
    return query(sql, CONTENT_IMAGE_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<ContentImageEntity> findImageById(Long id) {
    String sql = SELECT_CONTENT_IMAGE + " WHERE c.id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, CONTENT_IMAGE_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentImageEntity> findImagesByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    String sql = SELECT_CONTENT_IMAGE + " WHERE c.id IN (:ids)";
    MapSqlParameterSource params = createParameterSource().addValue("ids", ids);
    return query(sql, CONTENT_IMAGE_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentImageEntity> findAllImagesOrderByCreateDateDesc() {
    String sql =
        SELECT_CONTENT_IMAGE + " ORDER BY ci.capture_date DESC NULLS LAST, c.created_at DESC";
    return query(sql, CONTENT_IMAGE_ROW_MAPPER);
  }

  @Transactional(readOnly = true)
  public List<ContentImageEntity> findAllImagesOrderByCreateDateDesc(int limit, int offset) {
    String sql =
        SELECT_CONTENT_IMAGE
            + " ORDER BY ci.capture_date DESC NULLS LAST, c.created_at DESC LIMIT :limit OFFSET :offset";
    MapSqlParameterSource params =
        createParameterSource().addValue("limit", limit).addValue("offset", offset);
    return query(sql, CONTENT_IMAGE_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public int countImages() {
    String sql = "SELECT COUNT(*) FROM content WHERE content_type = 'IMAGE'";
    Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
    return count != null ? count : 0;
  }

  @Transactional(readOnly = true)
  public List<Long> findIdsByContentType(String contentType) {
    String sql = "SELECT id FROM content WHERE content_type = :contentType";
    MapSqlParameterSource params = createParameterSource().addValue("contentType", contentType);
    return namedParameterJdbcTemplate.queryForList(sql, params, Long.class);
  }

  @Transactional(readOnly = true)
  public List<ContentEntity> findAllByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }

    String typeSql = "SELECT id, content_type FROM content WHERE id IN (:ids)";
    MapSqlParameterSource typeParams = createParameterSource().addValue("ids", ids);
    List<Map<String, Object>> typeRows =
        namedParameterJdbcTemplate.queryForList(typeSql, typeParams);

    Map<ContentType, List<Long>> idsByType = new HashMap<>();
    for (Map<String, Object> row : typeRows) {
      Long id = ((Number) row.get("id")).longValue();
      ContentType type = ContentType.valueOf((String) row.get("content_type"));
      idsByType.computeIfAbsent(type, k -> new ArrayList<>()).add(id);
    }

    List<ContentEntity> results = new ArrayList<>();

    if (idsByType.containsKey(ContentType.IMAGE)) {
      String imageSql = SELECT_CONTENT_IMAGE + " WHERE c.id IN (:ids)";
      MapSqlParameterSource imageParams =
          createParameterSource().addValue("ids", idsByType.get(ContentType.IMAGE));
      results.addAll(query(imageSql, CONTENT_IMAGE_ROW_MAPPER, imageParams));
    }

    if (idsByType.containsKey(ContentType.TEXT)) {
      String sql = SELECT_CONTENT_TEXT + " WHERE c.id IN (:ids)";
      MapSqlParameterSource params =
          createParameterSource().addValue("ids", idsByType.get(ContentType.TEXT));
      results.addAll(query(sql, CONTENT_TEXT_ROW_MAPPER, params));
    }

    if (idsByType.containsKey(ContentType.GIF)) {
      String sql = SELECT_CONTENT_GIF + " WHERE c.id IN (:ids)";
      MapSqlParameterSource params =
          createParameterSource().addValue("ids", idsByType.get(ContentType.GIF));
      results.addAll(query(sql, CONTENT_GIF_ROW_MAPPER, params));
    }

    if (idsByType.containsKey(ContentType.COLLECTION)) {
      String sql = SELECT_CONTENT_COLLECTION + " WHERE c.id IN (:ids)";
      MapSqlParameterSource params =
          createParameterSource().addValue("ids", idsByType.get(ContentType.COLLECTION));
      results.addAll(query(sql, CONTENT_COLLECTION_ROW_MAPPER, params));
    }

    return results;
  }

  @Transactional
  public ContentImageEntity saveImage(ContentImageEntity entity) {
    LocalDateTime now = LocalDateTime.now();

    if (entity.getId() == null) {
      String contentSql =
          """
          INSERT INTO content (content_type, created_at, updated_at)
          VALUES (:contentType, :createdAt, :updatedAt)
          """;

      MapSqlParameterSource contentParams =
          createParameterSource()
              .addValue("contentType", ContentType.IMAGE.name())
              .addValue("createdAt", entity.getCreatedAt())
              .addValue("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt() : now);

      Long contentId = insertAndReturnId(contentSql, "id", contentParams);

      String imageSql =
          """
          INSERT INTO content_image (id, title, image_width, image_height, iso, author, rating,
                                    f_stop, lens_id, black_and_white, is_film, film_type_id,
                                    film_format, shutter_speed, camera_id, focal_length,
                                    location_id,
                                    image_url_web, image_url_original,
                                    capture_date, last_export_date, original_filename)
          VALUES (:id, :title, :imageWidth, :imageHeight, :iso, :author, :rating,
                  :fStop, :lensId, :blackAndWhite, :isFilm, :filmTypeId,
                  :filmFormat, :shutterSpeed, :cameraId, :focalLength,
                  :locationId,
                  :imageUrlWeb, :imageUrlOriginal,
                  :captureDate, :lastExportDate, :originalFilename)
          """;

      update(imageSql, buildImageParams(entity, contentId));

      entity.setId(contentId);
      if (entity.getCreatedAt() == null) {
        entity.setCreatedAt(now);
      }
      if (entity.getUpdatedAt() == null) {
        entity.setUpdatedAt(now);
      }

      return entity;
    } else {
      String contentSql =
          """
          UPDATE content
          SET updated_at = :updatedAt
          WHERE id = :id
          """;
      MapSqlParameterSource contentParams =
          createParameterSource().addValue("updatedAt", now).addValue("id", entity.getId());
      update(contentSql, contentParams);

      String imageSql =
          """
          UPDATE content_image
          SET title = :title, image_width = :imageWidth, image_height = :imageHeight, iso = :iso,
              author = :author, rating = :rating, f_stop = :fStop, lens_id = :lensId,
              black_and_white = :blackAndWhite, is_film = :isFilm, film_type_id = :filmTypeId,
              film_format = :filmFormat, shutter_speed = :shutterSpeed, camera_id = :cameraId,
              focal_length = :focalLength, location_id = :locationId,
              image_url_web = :imageUrlWeb, image_url_original = :imageUrlOriginal,
              capture_date = :captureDate, last_export_date = :lastExportDate,
              original_filename = :originalFilename
          WHERE id = :id
          """;

      update(imageSql, buildImageParams(entity, entity.getId()));

      entity.setUpdatedAt(now);
      return entity;
    }
  }

  private MapSqlParameterSource buildImageParams(ContentImageEntity entity, Long id) {
    return createParameterSource()
        .addValue("id", id)
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
        .addValue("filmTypeId", entity.getFilmType() != null ? entity.getFilmType().getId() : null)
        .addValue(
            "filmFormat", entity.getFilmFormat() != null ? entity.getFilmFormat().name() : null)
        .addValue("shutterSpeed", entity.getShutterSpeed())
        .addValue("cameraId", entity.getCamera() != null ? entity.getCamera().getId() : null)
        .addValue("focalLength", entity.getFocalLength())
        .addValue("locationId", entity.getLocationId())
        .addValue("imageUrlWeb", entity.getImageUrlWeb())
        .addValue("imageUrlOriginal", entity.getImageUrlOriginal())
        .addValue("captureDate", entity.getCaptureDate())
        .addValue("lastExportDate", entity.getLastExportDate())
        .addValue("originalFilename", entity.getOriginalFilename());
  }

  @Transactional
  public void saveImagePeople(Long imageId, List<Long> personIds) {
    String deleteSql = "DELETE FROM content_image_people WHERE image_id = :imageId";
    MapSqlParameterSource deleteParams = createParameterSource().addValue("imageId", imageId);
    update(deleteSql, deleteParams);

    if (personIds != null && !personIds.isEmpty()) {
      String insertSql =
          "INSERT INTO content_image_people (image_id, person_id) VALUES (:imageId, :personId)";
      MapSqlParameterSource[] batchParams =
          personIds.stream()
              .map(
                  personId ->
                      createParameterSource()
                          .addValue("imageId", imageId)
                          .addValue("personId", personId))
              .toArray(MapSqlParameterSource[]::new);
      batchUpdate(insertSql, batchParams);
    }
  }

  @Transactional(readOnly = true)
  public List<Long> findImagePersonIds(Long imageId) {
    String sql = "SELECT person_id FROM content_image_people WHERE image_id = :imageId";
    MapSqlParameterSource params = createParameterSource().addValue("imageId", imageId);
    return namedParameterJdbcTemplate.queryForList(sql, params, Long.class);
  }

  @Transactional(readOnly = true)
  public Map<Long, List<Long>> findPersonIdsByImageIds(List<Long> imageIds) {
    if (imageIds == null || imageIds.isEmpty()) {
      return Map.of();
    }

    String sql =
        "SELECT image_id, person_id FROM content_image_people WHERE image_id IN (:imageIds)";
    MapSqlParameterSource params = createParameterSource().addValue("imageIds", imageIds);

    Map<Long, List<Long>> result = new HashMap<>();
    namedParameterJdbcTemplate.query(
        sql,
        params,
        rs -> {
          Long imageId = rs.getLong("image_id");
          Long personId = rs.getLong("person_id");
          result.computeIfAbsent(imageId, k -> new ArrayList<>()).add(personId);
        });

    return result;
  }

  @Transactional
  public void deleteImageById(Long id) {
    MapSqlParameterSource params = createParameterSource().addValue("id", id);

    String clearCoverImageSql =
        "UPDATE collection SET cover_image_id = NULL WHERE cover_image_id = :id";
    update(clearCoverImageSql, params);

    String deleteCollectionContentSql = "DELETE FROM collection_content WHERE content_id = :id";
    update(deleteCollectionContentSql, params);

    String deleteTagsSql = "DELETE FROM content_tags WHERE content_id = :id";
    update(deleteTagsSql, params);

    String deletePeopleSql = "DELETE FROM content_image_people WHERE image_id = :id";
    update(deletePeopleSql, params);

    String deleteImageSql = "DELETE FROM content_image WHERE id = :id";
    update(deleteImageSql, params);

    String deleteContentSql = "DELETE FROM content WHERE id = :id";
    update(deleteContentSql, params);
  }

  // ============================================================
  // Text Operations
  // ============================================================

  @Transactional(readOnly = true)
  public Optional<ContentTextEntity> findTextById(Long id) {
    String sql = SELECT_CONTENT_TEXT + " WHERE c.id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, CONTENT_TEXT_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentTextEntity> findAllTextOrderByCreatedAtDesc() {
    String sql = SELECT_CONTENT_TEXT + " ORDER BY c.created_at DESC";
    return query(sql, CONTENT_TEXT_ROW_MAPPER);
  }

  @Transactional
  public ContentTextEntity saveText(ContentTextEntity entity) {
    LocalDateTime now = LocalDateTime.now();

    if (entity.getId() == null) {
      String contentSql =
          """
          INSERT INTO content (content_type, created_at, updated_at)
          VALUES (:contentType, :createdAt, :updatedAt)
          """;

      MapSqlParameterSource contentParams =
          createParameterSource()
              .addValue("contentType", ContentType.TEXT.name())
              .addValue("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt() : now)
              .addValue("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt() : now);

      Long contentId = insertAndReturnId(contentSql, "id", contentParams);

      String textSql =
          """
          INSERT INTO content_text (id, text_content, format_type)
          VALUES (:id, :textContent, :formatType)
          """;

      MapSqlParameterSource textParams =
          createParameterSource()
              .addValue("id", contentId)
              .addValue("textContent", entity.getTextContent())
              .addValue("formatType", entity.getFormatType());

      update(textSql, textParams);

      entity.setId(contentId);
      if (entity.getCreatedAt() == null) {
        entity.setCreatedAt(now);
      }
      if (entity.getUpdatedAt() == null) {
        entity.setUpdatedAt(now);
      }

      return entity;
    } else {
      String contentSql =
          """
          UPDATE content
          SET updated_at = :updatedAt
          WHERE id = :id
          """;
      MapSqlParameterSource contentParams =
          createParameterSource().addValue("updatedAt", now).addValue("id", entity.getId());
      update(contentSql, contentParams);

      String textSql =
          """
          UPDATE content_text
          SET text_content = :textContent, format_type = :formatType
          WHERE id = :id
          """;

      MapSqlParameterSource textParams =
          createParameterSource()
              .addValue("id", entity.getId())
              .addValue("textContent", entity.getTextContent())
              .addValue("formatType", entity.getFormatType());

      update(textSql, textParams);

      entity.setUpdatedAt(now);
      return entity;
    }
  }

  @Transactional
  public void deleteTextById(Long id) {
    String textSql = "DELETE FROM content_text WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    update(textSql, params);

    String contentSql = "DELETE FROM content WHERE id = :id";
    update(contentSql, params);
  }

  // ============================================================
  // GIF Operations
  // ============================================================

  @Transactional(readOnly = true)
  public Optional<ContentGifEntity> findGifById(Long id) {
    String sql = SELECT_CONTENT_GIF + " WHERE c.id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, CONTENT_GIF_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentGifEntity> findAllGifsOrderByCreateDateDesc() {
    String sql = SELECT_CONTENT_GIF + " ORDER BY cg.create_date DESC NULLS LAST, c.created_at DESC";
    return query(sql, CONTENT_GIF_ROW_MAPPER);
  }

  @Transactional
  public ContentGifEntity saveGif(ContentGifEntity entity) {
    LocalDateTime now = LocalDateTime.now();

    if (entity.getId() == null) {
      String contentSql =
          """
          INSERT INTO content (content_type, created_at, updated_at)
          VALUES (:contentType, :createdAt, :updatedAt)
          """;

      MapSqlParameterSource contentParams =
          createParameterSource()
              .addValue("contentType", ContentType.GIF.name())
              .addValue("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt() : now)
              .addValue("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt() : now);

      Long contentId = insertAndReturnId(contentSql, "id", contentParams);

      String gifSql =
          """
          INSERT INTO content_gif (id, title, gif_url, thumbnail_url, width, height, author, create_date)
          VALUES (:id, :title, :gifUrl, :thumbnailUrl, :width, :height, :author, :createDate)
          """;

      MapSqlParameterSource gifParams =
          createParameterSource()
              .addValue("id", contentId)
              .addValue("title", entity.getTitle())
              .addValue("gifUrl", entity.getGifUrl())
              .addValue("thumbnailUrl", entity.getThumbnailUrl())
              .addValue("width", entity.getWidth())
              .addValue("height", entity.getHeight())
              .addValue("author", entity.getAuthor())
              .addValue("createDate", entity.getCreateDate());

      update(gifSql, gifParams);

      entity.setId(contentId);
      if (entity.getCreatedAt() == null) {
        entity.setCreatedAt(now);
      }
      if (entity.getUpdatedAt() == null) {
        entity.setUpdatedAt(now);
      }

      return entity;
    } else {
      String contentSql =
          """
          UPDATE content
          SET updated_at = :updatedAt
          WHERE id = :id
          """;
      MapSqlParameterSource contentParams =
          createParameterSource().addValue("updatedAt", now).addValue("id", entity.getId());
      update(contentSql, contentParams);

      String gifSql =
          """
          UPDATE content_gif
          SET title = :title, gif_url = :gifUrl, thumbnail_url = :thumbnailUrl,
              width = :width, height = :height, author = :author, create_date = :createDate
          WHERE id = :id
          """;

      MapSqlParameterSource gifParams =
          createParameterSource()
              .addValue("id", entity.getId())
              .addValue("title", entity.getTitle())
              .addValue("gifUrl", entity.getGifUrl())
              .addValue("thumbnailUrl", entity.getThumbnailUrl())
              .addValue("width", entity.getWidth())
              .addValue("height", entity.getHeight())
              .addValue("author", entity.getAuthor())
              .addValue("createDate", entity.getCreateDate());

      update(gifSql, gifParams);

      entity.setUpdatedAt(now);
      return entity;
    }
  }

  @Transactional
  public void deleteGifById(Long id) {
    MapSqlParameterSource params = createParameterSource().addValue("id", id);

    String deleteTagsSql = "DELETE FROM content_tags WHERE content_id = :id";
    update(deleteTagsSql, params);

    String gifSql = "DELETE FROM content_gif WHERE id = :id";
    update(gifSql, params);

    String contentSql = "DELETE FROM content WHERE id = :id";
    update(contentSql, params);
  }

  // ============================================================
  // Collection Content Operations
  // ============================================================

  @Transactional(readOnly = true)
  public Optional<ContentCollectionEntity> findCollectionContentById(Long id) {
    String sql = SELECT_CONTENT_COLLECTION + " WHERE c.id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, CONTENT_COLLECTION_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<ContentCollectionEntity> findCollectionContentByReferencedCollectionId(
      Long referencedCollectionId) {
    String sql =
        SELECT_CONTENT_COLLECTION + " WHERE cc.referenced_collection_id = :referencedCollectionId";
    MapSqlParameterSource params =
        createParameterSource().addValue("referencedCollectionId", referencedCollectionId);
    return queryForObject(sql, CONTENT_COLLECTION_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentCollectionEntity> findAllCollectionContentOrderByCreatedAtDesc() {
    String sql = SELECT_CONTENT_COLLECTION + " ORDER BY c.created_at DESC";
    return query(sql, CONTENT_COLLECTION_ROW_MAPPER);
  }

  @Transactional
  public ContentCollectionEntity saveCollectionContent(ContentCollectionEntity entity) {
    LocalDateTime now = LocalDateTime.now();

    if (entity.getId() == null) {
      String contentSql =
          """
          INSERT INTO content (content_type, created_at, updated_at)
          VALUES (:contentType, :createdAt, :updatedAt)
          """;

      MapSqlParameterSource contentParams =
          createParameterSource()
              .addValue("contentType", ContentType.COLLECTION.name())
              .addValue("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt() : now)
              .addValue("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt() : now);

      Long contentId = insertAndReturnId(contentSql, "id", contentParams);

      String collectionSql =
          """
          INSERT INTO content_collection (id, referenced_collection_id)
          VALUES (:id, :referencedCollectionId)
          """;

      MapSqlParameterSource collectionParams =
          createParameterSource()
              .addValue("id", contentId)
              .addValue("referencedCollectionId", entity.getReferencedCollection().getId());

      update(collectionSql, collectionParams);

      entity.setId(contentId);
      if (entity.getCreatedAt() == null) {
        entity.setCreatedAt(now);
      }
      if (entity.getUpdatedAt() == null) {
        entity.setUpdatedAt(now);
      }

      return entity;
    } else {
      String contentSql =
          """
          UPDATE content
          SET updated_at = :updatedAt
          WHERE id = :id
          """;
      MapSqlParameterSource contentParams =
          createParameterSource().addValue("updatedAt", now).addValue("id", entity.getId());
      update(contentSql, contentParams);

      String collectionSql =
          """
          UPDATE content_collection
          SET referenced_collection_id = :referencedCollectionId
          WHERE id = :id
          """;

      MapSqlParameterSource collectionParams =
          createParameterSource()
              .addValue("id", entity.getId())
              .addValue("referencedCollectionId", entity.getReferencedCollection().getId());

      update(collectionSql, collectionParams);

      entity.setUpdatedAt(now);
      return entity;
    }
  }

  @Transactional
  public void deleteCollectionContentById(Long id) {
    String collectionSql = "DELETE FROM content_collection WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    update(collectionSql, params);

    String contentSql = "DELETE FROM content WHERE id = :id";
    update(contentSql, params);
  }
}
