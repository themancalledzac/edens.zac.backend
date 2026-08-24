package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import edens.zac.portfolio.backend.types.DisplayMode;
import java.sql.Array;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for CollectionEntity and CollectionContentEntity. Consolidates CollectionDao and
 * CollectionContentDao.
 */
@Component
@Slf4j
public class CollectionRepository extends BaseDao {

  public CollectionRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  // ============================================================
  // Collection RowMapper & SQL
  // ============================================================

  /**
   * The single canonical column list for a full {@code collection} row. Every query that feeds
   * {@link #COLLECTION_ROW_MAPPER} must project exactly these, so adding a column is a one-line
   * change here instead of a hand-edit of six copies (including the one in {@link TagRepository}).
   */
  private static final List<String> COLLECTION_COLUMN_NAMES =
      List.of(
          "id",
          "is_client",
          "is_blog",
          "title",
          "slug",
          "description",
          "collection_date",
          "collection_end_date",
          "visibility",
          "display_mode",
          "cover_image_id",
          "content_per_page",
          "total_content",
          "rows_wide",
          "gallery_password",
          "recipient_emails",
          "rating",
          "created_at",
          "updated_at");

  /**
   * The canonical column list, each column prefixed with {@code alias.} when an alias is given (for
   * joined queries) and unprefixed otherwise.
   */
  static String collectionColumns(String alias) {
    String prefix = alias == null || alias.isBlank() ? "" : alias + ".";
    return COLLECTION_COLUMN_NAMES.stream()
        .map(column -> prefix + column)
        .collect(Collectors.joining(", "));
  }

  private static final String SELECT_COLLECTION =
      "SELECT " + collectionColumns(null) + " FROM collection ";

  static final RowMapper<CollectionEntity> COLLECTION_ROW_MAPPER =
      (rs, rowNum) -> {
        CollectionEntity entity = new CollectionEntity();
        entity.setId(rs.getLong("id"));
        entity.setClient(rs.getBoolean("is_client"));
        entity.setBlog(rs.getBoolean("is_blog"));
        entity.setTitle(rs.getString("title"));
        entity.setSlug(rs.getString("slug"));
        entity.setDescription(rs.getString("description"));
        entity.setCollectionDate(getLocalDate(rs, "collection_date"));
        entity.setCollectionEndDate(getLocalDate(rs, "collection_end_date"));
        entity.setVisibility(CollectionVisibility.valueOf(rs.getString("visibility")));

        String displayMode = rs.getString("display_mode");
        if (displayMode != null) {
          try {
            entity.setDisplayMode(DisplayMode.valueOf(displayMode));
          } catch (IllegalArgumentException e) {
            log.warn("Invalid display_mode value: {}", displayMode);
          }
        }

        Long coverImageId = getLong(rs, "cover_image_id");
        if (coverImageId != null) {
          entity.setCoverImageId(coverImageId);
        }

        entity.setContentPerPage(getInteger(rs, "content_per_page"));
        entity.setTotalContent(getInteger(rs, "total_content"));
        entity.setRowsWide(getInteger(rs, "rows_wide"));
        entity.setGalleryPassword(rs.getString("gallery_password"));
        Array emailsArray = rs.getArray("recipient_emails");
        entity.setRecipientEmails(
            emailsArray != null
                ? new ArrayList<>(Arrays.asList((String[]) emailsArray.getArray()))
                : new ArrayList<>());
        entity.setRating(getInteger(rs, "rating"));
        entity.setCreatedAt(getLocalDateTime(rs, "created_at"));
        entity.setUpdatedAt(getLocalDateTime(rs, "updated_at"));

        return entity;
      };

  // ============================================================
  // CollectionContent RowMapper & SQL
  // ============================================================

  private static final String SELECT_COLLECTION_CONTENT =
      """
      SELECT id, collection_id, content_id, order_index, visible, created_at, updated_at
      FROM collection_content
      """;

  private static final RowMapper<CollectionContentEntity> COLLECTION_CONTENT_ROW_MAPPER =
      (rs, rowNum) ->
          CollectionContentEntity.builder()
              .id(rs.getLong("id"))
              .collectionId(rs.getLong("collection_id"))
              .contentId(rs.getLong("content_id"))
              .orderIndex(getInteger(rs, "order_index"))
              .visible(getBoolean(rs, "visible"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .updatedAt(getLocalDateTime(rs, "updated_at"))
              .build();

  // ============================================================
  // Collection CRUD Operations
  // ============================================================

  @Transactional(readOnly = true)
  public Optional<CollectionEntity> findBySlug(String slug) {
    String sql = SELECT_COLLECTION + " WHERE slug = :slug";
    MapSqlParameterSource params = createParameterSource().addValue("slug", slug);
    return queryForObject(sql, COLLECTION_ROW_MAPPER, params);
  }

  /** Find LISTED blog collections ({@code is_blog = true}), rating-first then newest. */
  @Transactional(readOnly = true)
  public List<CollectionEntity> findListedBlogsOrdered() {
    String sql =
        SELECT_COLLECTION
            + " WHERE is_blog = true AND visibility = 'LISTED' "
            + "ORDER BY rating DESC NULLS LAST, collection_date DESC NULLS LAST";
    return query(sql, COLLECTION_ROW_MAPPER);
  }

  /**
   * Find blog collections ({@code is_blog = true}) on an exact collection_date, oldest first. Used
   * by the tag-first ingest flow to get-or-create the per-day blog collection keyed on {@code
   * (is_blog=true, collection_date=day)}. Ordering by {@code created_at ASC} means callers can take
   * the first result as the canonical (oldest) collection when duplicates unexpectedly exist.
   */
  @Transactional(readOnly = true)
  public List<CollectionEntity> findBlogsByCollectionDate(LocalDate collectionDate) {
    String sql =
        SELECT_COLLECTION
            + " WHERE is_blog = true AND collection_date = :collectionDate "
            + "ORDER BY created_at ASC";
    MapSqlParameterSource params =
        createParameterSource().addValue("collectionDate", collectionDate);
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  /**
   * Find visible child collections referenced by a parent collection. Walks the join chain: parent
   * -> collection_content -> content_collection -> referenced collection. Filters out hidden
   * link-table rows and hidden child collections so callers only see what is publicly visible.
   */
  @Transactional(readOnly = true)
  public List<CollectionEntity> findReferencedCollectionsByParentId(Long parentId) {
    String sql =
        "SELECT "
            + collectionColumns("c")
            + "\n"
            + """
        FROM collection c
        JOIN content_collection cct ON cct.referenced_collection_id = c.id
        JOIN collection_content cc ON cc.content_id = cct.id
        WHERE cc.collection_id = :parentId
          AND cc.visible = true
          AND c.visibility = 'LISTED'
        ORDER BY cc.order_index ASC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("parentId", parentId);
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  /**
   * Same join chain as {@link #findReferencedCollectionsByParentId} but returns every referenced
   * child regardless of either {@code c.visibility} or per-membership {@code cc.visible}. Used by
   * admin-context flows (e.g. PARENT password propagation) where the admin must reach every linked
   * child — UI/membership visibility does not gate admin operations.
   */
  @Transactional(readOnly = true)
  public List<CollectionEntity> findAllReferencedCollectionsByParentId(Long parentId) {
    String sql =
        "SELECT "
            + collectionColumns("c")
            + "\n"
            + """
        FROM collection c
        JOIN content_collection cct ON cct.referenced_collection_id = c.id
        JOIN collection_content cc ON cc.content_id = cct.id
        WHERE cc.collection_id = :parentId
        ORDER BY cc.order_index ASC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("parentId", parentId);
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  /**
   * True when the collection references at least one child collection with {@code is_client =
   * true}. This is the derived successor to the {@code type == PARENT} gate on gallery-access
   * eligibility and password propagation: parent-ness is a property of the content graph, not a
   * stored label. Admin-context, so it gates neither the child's own {@code visibility} nor the
   * per-membership {@code cc.visible} -- exactly like {@link
   * #findAllReferencedCollectionsByParentId}, whose result set it summarises.
   */
  @Transactional(readOnly = true)
  public boolean hasClientGalleryChildren(Long parentId) {
    String sql =
        """
        SELECT EXISTS (
          SELECT 1
          FROM collection_content cc
          JOIN content_collection cct ON cct.id = cc.content_id
          JOIN collection c ON c.id = cct.referenced_collection_id
          WHERE cc.collection_id = :parentId
            AND c.is_client = true
        )
        """;
    MapSqlParameterSource params = createParameterSource().addValue("parentId", parentId);
    return queryForObject(sql, (rs, rowNum) -> rs.getBoolean(1), params).orElse(false);
  }

  /**
   * Every child collection id linked under this parent, ordered by {@code cc.order_index}, ignoring
   * both the membership {@code visible} flag and the child's own visibility. The admin manage
   * payload needs the COMPLETE list: the frontend otherwise derives it from the paginated content
   * array, which is bounded by the 500-item page window, and a truncated list reads as an
   * intentional child removal on the next save.
   */
  @Transactional(readOnly = true)
  public List<Long> findAllReferencedCollectionIdsByParentId(Long parentId) {
    if (parentId == null) {
      return List.of();
    }
    String sql =
        """
        SELECT cct.referenced_collection_id
        FROM collection_content cc
        JOIN content_collection cct ON cct.id = cc.content_id
        WHERE cc.collection_id = :parentId
          AND cct.referenced_collection_id IS NOT NULL
        ORDER BY cc.order_index ASC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("parentId", parentId);
    return query(sql, (rs, n) -> rs.getLong("referenced_collection_id"), params);
  }

  /**
   * Ids of children linked under a parent through a VISIBLE membership row ({@code cc.visible =
   * true}), regardless of the child collection's own {@code visibility}. Role-grant propagation
   * follows only visible links (mirroring the V47 backfill gate); removal walks use the
   * visibility-agnostic {@link #findAllReferencedCollectionsByParentId} instead so stale copies are
   * always stripped.
   */
  @Transactional(readOnly = true)
  public List<Long> findVisibleReferencedCollectionIdsByParentId(Long parentId) {
    String sql =
        """
        SELECT cct.referenced_collection_id
        FROM collection_content cc
        JOIN content_collection cct ON cct.id = cc.content_id
        WHERE cc.collection_id = :parentId
          AND cc.visible = true
          AND cct.referenced_collection_id IS NOT NULL
        ORDER BY cc.order_index ASC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("parentId", parentId);
    return query(sql, (rs, n) -> rs.getLong("referenced_collection_id"), params);
  }

  /**
   * Inverse of {@link #findVisibleReferencedCollectionIdsByParentId}: ids of parents linked to a
   * child through a VISIBLE membership row. Used by grant re-materialization to find the ancestors
   * whose grants can actually waterfall down to the child (a hidden link on the path blocks
   * inheritance).
   */
  @Transactional(readOnly = true)
  public List<Long> findVisibleParentCollectionIdsByChildId(Long childId) {
    String sql =
        """
        SELECT cc.collection_id
        FROM collection_content cc
        JOIN content_collection cct ON cct.id = cc.content_id
        WHERE cct.referenced_collection_id = :childId
          AND cc.visible = true
        ORDER BY cc.collection_id ASC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("childId", childId);
    return query(sql, (rs, n) -> rs.getLong("collection_id"), params);
  }

  /**
   * Inverse of {@link #findAllReferencedCollectionsByParentId}: given a child collection, find
   * every parent collection that references it. Walks child -> content_collection ->
   * collection_content -> parent. Admin-context query: filters neither {@code c.visibility} nor
   * per-membership {@code cc.visible}, so the admin sees every parent relationship -- including
   * ones where the child is linked but hidden. Mirrors the both-gates-dropped symmetry of {@link
   * #findAllReferencedCollectionsByParentId}.
   */
  @Transactional(readOnly = true)
  public List<CollectionEntity> findAllParentCollectionsByChildId(Long childId) {
    String sql =
        "SELECT "
            + collectionColumns("c")
            + "\n"
            + """
        FROM collection c
        JOIN collection_content cc ON cc.collection_id = c.id
        JOIN content_collection cct ON cct.id = cc.content_id
        WHERE cct.referenced_collection_id = :childId
        ORDER BY c.title ASC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("childId", childId);
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  /** Find every listed collection that has a cover image, ordered by rating then date. */
  @Transactional(readOnly = true)
  public List<CollectionEntity> findAllListedWithCovers() {
    String sql =
        SELECT_COLLECTION
            + " WHERE visibility = 'LISTED' AND cover_image_id IS NOT NULL "
            + "ORDER BY rating DESC NULLS LAST, collection_date DESC NULLS LAST";
    return query(sql, COLLECTION_ROW_MAPPER);
  }

  @Transactional(readOnly = true)
  public List<CollectionEntity> findListedByLocationName(
      String locationName, int limit, int offset) {
    String sql =
        "SELECT "
            + collectionColumns("c")
            + "\n"
            + """
        FROM collection c
        JOIN collection_locations cl ON c.id = cl.collection_id
        JOIN location l ON cl.location_id = l.id
        WHERE l.location_name = :locationName AND c.visibility = 'LISTED'
        ORDER BY c.rating DESC NULLS LAST, c.collection_date DESC NULLS LAST
        LIMIT :limit OFFSET :offset
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("locationName", locationName)
            .addValue("limit", limit)
            .addValue("offset", offset);
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<Long> findListedIdsByLocationName(String locationName) {
    String sql =
        "SELECT c.id FROM collection c "
            + "JOIN collection_locations cl ON c.id = cl.collection_id "
            + "JOIN location l ON cl.location_id = l.id "
            + "WHERE l.location_name = :locationName AND c.visibility = 'LISTED'";
    MapSqlParameterSource params = createParameterSource().addValue("locationName", locationName);
    return namedParameterJdbcTemplate.queryForList(sql, params, Long.class);
  }

  @Transactional(readOnly = true)
  public long countListedByLocationName(String locationName) {
    String sql =
        """
        SELECT COUNT(*) FROM collection c
        JOIN collection_locations cl ON c.id = cl.collection_id
        JOIN location l ON cl.location_id = l.id
        WHERE l.location_name = :locationName AND c.visibility = 'LISTED'
        """;
    MapSqlParameterSource params = createParameterSource().addValue("locationName", locationName);
    Long count = namedParameterJdbcTemplate.queryForObject(sql, params, Long.class);
    return count != null ? count : 0L;
  }

  @Transactional(readOnly = true)
  public List<CollectionEntity> findAllByOrderByCollectionDateDesc(int limit, int offset) {
    String sql =
        SELECT_COLLECTION + " ORDER BY collection_date DESC NULLS LAST LIMIT :limit OFFSET :offset";
    MapSqlParameterSource params =
        createParameterSource().addValue("limit", limit).addValue("offset", offset);
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<CollectionEntity> findAllListedOrdered(int limit, int offset) {
    String sql =
        SELECT_COLLECTION
            + " WHERE visibility = 'LISTED' "
            + "ORDER BY rating DESC NULLS LAST, collection_date DESC NULLS LAST "
            + "LIMIT :limit OFFSET :offset";
    MapSqlParameterSource params =
        createParameterSource().addValue("limit", limit).addValue("offset", offset);
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  /**
   * Find client-gallery collections ({@code is_client = true}) whose visibility is in the supplied
   * set, ordered by rating then collection_date. Includes empty collections — used by admin-cover
   * candidate selection where content is irrelevant.
   */
  @Transactional(readOnly = true)
  public List<CollectionEntity> findClientGalleriesByVisibilityIn(
      List<CollectionVisibility> allowed) {
    String sql =
        SELECT_COLLECTION
            + " WHERE visibility IN (:visibilities) AND is_client = true "
            + "ORDER BY rating DESC NULLS LAST, collection_date DESC NULLS LAST";
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("visibilities", allowed.stream().map(CollectionVisibility::name).toList());
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  /**
   * Find collections whose visibility is in the supplied set, dropping collections that have zero
   * non-soft-removed entries in {@code collection_content}. Used by synthetic-list endpoints (e.g.
   * {@code /all-collections}, {@code /all-blogs}) so the listing never renders empty tiles.
   * Admin-only flows that need empty collections (e.g. cover-image picking) should use {@link
   * #findClientGalleriesByVisibilityIn}.
   *
   * @param blogsOnly when true, restrict to {@code is_blog = true} -- the single definition of
   *     "blog", shared with the admin blogs tile
   */
  @Transactional(readOnly = true)
  public List<CollectionEntity> findNonEmptyOrderedByVisibilityIn(
      List<CollectionVisibility> allowed, boolean blogsOnly) {
    StringBuilder sql =
        new StringBuilder(SELECT_COLLECTION).append(" WHERE visibility IN (:visibilities) ");
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("visibilities", allowed.stream().map(CollectionVisibility::name).toList());
    if (blogsOnly) {
      sql.append(" AND is_blog = true ");
    }
    sql.append(
        """
         AND EXISTS (
           SELECT 1 FROM collection_content cc
           WHERE cc.collection_id = collection.id
             AND cc.visible = true
         )
         ORDER BY rating DESC NULLS LAST, collection_date DESC NULLS LAST
        """);
    return query(sql.toString(), COLLECTION_ROW_MAPPER, params);
  }

  /**
   * Same visibility scope and non-empty guard as {@link #findNonEmptyOrderedByVisibilityIn}, but
   * ordered chronologically ({@code collection_date DESC, id DESC}) instead of rating-first, and
   * with no {@code blogsOnly} restriction. Backs the {@code all-collections} synthetic list, whose
   * first paint is newest-collections-first; the {@code id DESC} tiebreaker gives a deterministic
   * total order. The other synthetic lists keep {@link #findNonEmptyOrderedByVisibilityIn}
   * (rating-first), so this is an additive sibling rather than a change to shared ordering.
   */
  @Transactional(readOnly = true)
  public List<CollectionEntity> findNonEmptyByVisibilityInOrderByDate(
      List<CollectionVisibility> allowed) {
    String sql =
        SELECT_COLLECTION
            + """
             WHERE visibility IN (:visibilities)
               AND EXISTS (
                 SELECT 1 FROM collection_content cc
                 WHERE cc.collection_id = collection.id
                   AND cc.visible = true
               )
             ORDER BY collection_date DESC NULLS LAST, id DESC
             """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("visibilities", allowed.stream().map(CollectionVisibility::name).toList());
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  /**
   * Permission-scoped variant of {@link #findNonEmptyByVisibilityInOrderByDate}: rows whose
   * visibility is in {@code allowed} OR whose id is one of the caller's role-granted collection ids
   * ({@code ownedIds}), so a signed-in client sees their UNLISTED/HIDDEN galleries alongside the
   * LISTED set. Empty {@code ownedIds} degrades to the scope-only query (Postgres rejects {@code IN
   * ()}). Same non-empty guard and chronological order as the base query. Scope widening is decided
   * by the caller from the server-verified principal only.
   */
  @Transactional(readOnly = true)
  public List<CollectionEntity> findNonEmptyListedOrOwnedOrderByDate(
      List<CollectionVisibility> allowed, List<Long> ownedIds) {
    if (ownedIds == null || ownedIds.isEmpty()) {
      return findNonEmptyByVisibilityInOrderByDate(allowed);
    }
    String sql =
        SELECT_COLLECTION
            + """
             WHERE (visibility IN (:visibilities) OR id IN (:ownedIds))
               AND EXISTS (
                 SELECT 1 FROM collection_content cc
                 WHERE cc.collection_id = collection.id
                   AND cc.visible = true
               )
             ORDER BY collection_date DESC NULLS LAST, id DESC
             """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("visibilities", allowed.stream().map(CollectionVisibility::name).toList())
            .addValue("ownedIds", ownedIds);
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  /**
   * Find every client gallery ({@code is_client = true}) plus every collection that has at least
   * one visible client-gallery child (a "derived parent" — no discriminator on the parent), all
   * within the supplied visibility set, ordered by rating then collection_date. The parent branch
   * walks the same join chain as {@link #findReferencedCollectionsByParentId} (parent ->
   * collection_content -> content_collection -> child) and applies the same visibility scope to the
   * child rows so parents whose only matching child is HIDDEN do not appear. Used by the
   * "all-client-galleries" synthetic listing where parents-of-galleries (e.g. wedding wrappers)
   * should appear alongside standalone client galleries.
   */
  @Transactional(readOnly = true)
  public List<CollectionEntity> findClientGalleriesAndQualifyingParents(
      List<CollectionVisibility> allowed) {
    String sql =
        SELECT_COLLECTION
            + """
             WHERE visibility IN (:visibilities)
               AND (
                 is_client = true
                 OR id IN (
                   SELECT cc.collection_id
                   FROM collection_content cc
                   JOIN content_collection cct ON cct.id = cc.content_id
                   JOIN collection child ON child.id = cct.referenced_collection_id
                   WHERE child.is_client = true
                     AND child.visibility IN (:visibilities)
                     AND cc.visible = true
                 )
               )
             ORDER BY rating DESC NULLS LAST, collection_date DESC NULLS LAST
             """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("visibilities", allowed.stream().map(CollectionVisibility::name).toList());
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public long countAllCollections() {
    String sql = "SELECT COUNT(*) FROM collection";
    Long count = jdbcTemplate.queryForObject(sql, Long.class);
    return count != null ? count : 0L;
  }

  @Transactional(readOnly = true)
  public long countVisibleCollections() {
    String sql = "SELECT COUNT(*) FROM collection WHERE visibility = 'LISTED'";
    Long count = jdbcTemplate.queryForObject(sql, Long.class);
    return count != null ? count : 0L;
  }

  /**
   * Every collection as a list entry: id, title, slug, date, cover image URL and the two kind
   * flags. Ordered by title.
   *
   * <p>The date and cover URL were hard-coded null here until 2026-08-15, though {@link
   * Records.CollectionList} has always declared both. That silently disabled the blog date ordering
   * in the frontend's collection selector, which sorts on a date it never received and fell through
   * to its alphabetical fallback on every row.
   *
   * <p>The cover join is a LEFT JOIN because a collection need not have a cover image, and one
   * missing a cover must still appear in the list.
   */
  @Transactional(readOnly = true)
  public List<Records.CollectionList> findCollectionListEntries() {
    String sql =
        "SELECT c.id, c.title, c.slug, c.collection_date, c.is_client, c.is_blog,"
            + " ci.image_url_web"
            + " FROM collection c"
            + " LEFT JOIN content_image ci ON ci.id = c.cover_image_id"
            + " ORDER BY c.title ASC";
    return jdbcTemplate.query(
        sql,
        (rs, rowNum) ->
            new Records.CollectionList(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("slug"),
                getLocalDate(rs, "collection_date"),
                rs.getString("image_url_web"),
                rs.getBoolean("is_client"),
                rs.getBoolean("is_blog")));
  }

  /**
   * Persist a CollectionEntity. Neither branch writes {@code recipient_emails}, and UPDATE also
   * omits {@code gallery_password}: both columns are owned exclusively by {@link
   * #saveGalleryAccess}, which writes them atomically as a pair. Neither branch touches {@code
   * type}: V52 dropped the column outright, so naming it here again fails at runtime on every
   * create and update. Restoring it takes {@code collection_type_archive} (V51) plus a pre-U4
   * application build -- see {@code V52__drop_collection_type.sql}.
   */
  @Transactional
  public CollectionEntity save(CollectionEntity entity) {
    if (entity.getId() == null) {
      String sql =
          """
          INSERT INTO collection (is_client, is_blog, title, slug, description, collection_date, collection_end_date,
                                 visibility, display_mode, cover_image_id, content_per_page, total_content,
                                 rows_wide, gallery_password, rating, created_at, updated_at)
          VALUES (:isClient, :isBlog, :title, :slug, :description, :collectionDate, :collectionEndDate,
                  :visibility, :displayMode, :coverImageId, :contentPerPage, :totalContent,
                  :rowsWide, :galleryPassword, :rating, :createdAt, :updatedAt)
          """;

      MapSqlParameterSource params =
          createParameterSource()
              .addValue("isClient", entity.isClient())
              .addValue("isBlog", entity.isBlog())
              .addValue("title", entity.getTitle())
              .addValue("slug", entity.getSlug())
              .addValue("description", entity.getDescription())
              .addValue("collectionDate", entity.getCollectionDate())
              .addValue("collectionEndDate", entity.getCollectionEndDate())
              .addValue(
                  "visibility",
                  entity.getVisibility() != null ? entity.getVisibility().name() : "HIDDEN")
              .addValue(
                  "displayMode",
                  entity.getDisplayMode() != null ? entity.getDisplayMode().name() : null)
              .addValue("coverImageId", entity.getCoverImageId())
              .addValue("contentPerPage", entity.getContentPerPage())
              .addValue("totalContent", entity.getTotalContent())
              .addValue("rowsWide", entity.getRowsWide())
              .addValue("galleryPassword", entity.getGalleryPassword())
              .addValue("rating", entity.getRating())
              .addValue(
                  "createdAt",
                  entity.getCreatedAt() != null ? entity.getCreatedAt() : LocalDateTime.now())
              .addValue(
                  "updatedAt",
                  entity.getUpdatedAt() != null ? entity.getUpdatedAt() : LocalDateTime.now());

      Long id = insertAndReturnId(sql, "id", params);
      entity.setId(id);
      return entity;
    } else {
      String sql =
          """
          UPDATE collection
          SET is_client = :isClient, is_blog = :isBlog, title = :title, slug = :slug, description = :description,
              collection_date = :collectionDate, collection_end_date = :collectionEndDate,
              visibility = :visibility, display_mode = :displayMode,
              cover_image_id = :coverImageId, content_per_page = :contentPerPage, total_content = :totalContent,
              rows_wide = :rowsWide, rating = :rating, updated_at = :updatedAt
          WHERE id = :id
          """;

      MapSqlParameterSource params =
          createParameterSource()
              .addValue("id", entity.getId())
              .addValue("isClient", entity.isClient())
              .addValue("isBlog", entity.isBlog())
              .addValue("title", entity.getTitle())
              .addValue("slug", entity.getSlug())
              .addValue("description", entity.getDescription())
              .addValue("collectionDate", entity.getCollectionDate())
              .addValue("collectionEndDate", entity.getCollectionEndDate())
              .addValue(
                  "visibility",
                  entity.getVisibility() != null ? entity.getVisibility().name() : "HIDDEN")
              .addValue(
                  "displayMode",
                  entity.getDisplayMode() != null ? entity.getDisplayMode().name() : null)
              .addValue("coverImageId", entity.getCoverImageId())
              .addValue("contentPerPage", entity.getContentPerPage())
              .addValue("totalContent", entity.getTotalContent())
              .addValue("rowsWide", entity.getRowsWide())
              .addValue("rating", entity.getRating())
              .addValue("updatedAt", LocalDateTime.now());

      update(sql, params);
      return entity;
    }
  }

  /** Update only the rating column for a collection. Returns affected row count. */
  @Transactional
  public int updateRating(Long id, Integer rating) {
    String sql = "UPDATE collection SET rating = :rating, updated_at = NOW() WHERE id = :id";
    MapSqlParameterSource params =
        createParameterSource().addValue("id", id).addValue("rating", rating);
    return update(sql, params);
  }

  /**
   * Update only the visibility column for a collection. Used by pipelines that create a collection
   * through the shared create path (which is privacy-first UNLISTED) but need a different published
   * state -- e.g. the Lightroom day-blog ingest, whose blogs must be LISTED to appear in {@link
   * #findListedBlogsOrdered} and on the public {@code /all-blogs} listing. Returns affected row
   * count.
   */
  @Transactional
  public int updateVisibility(Long id, CollectionVisibility visibility) {
    String sql =
        "UPDATE collection SET visibility = :visibility, updated_at = NOW() WHERE id = :id";
    MapSqlParameterSource params =
        createParameterSource().addValue("id", id).addValue("visibility", visibility.name());
    return update(sql, params);
  }

  /**
   * Update only the gallery_password column for a collection. Used by the parent-password
   * trickle-down path (which writes the password to each CLIENT_GALLERY child without touching
   * recipient_emails). Returns affected row count.
   */
  @Transactional
  public int updateGalleryPassword(Long id, String password) {
    String sql =
        "UPDATE collection SET gallery_password = :password, updated_at = NOW() WHERE id = :id";
    MapSqlParameterSource params =
        createParameterSource().addValue("id", id).addValue("password", password);
    return update(sql, params);
  }

  /** Update gallery_password and recipient_emails atomically for a CLIENT_GALLERY. */
  @Transactional
  public void saveGalleryAccess(Long collectionId, String password, List<String> emails) {
    String[] emailArray = emails != null ? emails.toArray(new String[0]) : new String[0];
    jdbcTemplate.update(
        conn -> {
          var ps =
              conn.prepareStatement(
                  "UPDATE collection SET gallery_password = ?, recipient_emails = ?, updated_at = ? WHERE id = ?");
          ps.setString(1, password);
          ps.setArray(2, conn.createArrayOf("text", emailArray));
          ps.setTimestamp(3, java.sql.Timestamp.valueOf(LocalDateTime.now()));
          ps.setLong(4, collectionId);
          return ps;
        });
  }

  @Transactional(readOnly = true)
  public Optional<CollectionEntity> findById(Long id) {
    String sql = SELECT_COLLECTION + " WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, COLLECTION_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<CollectionEntity> findByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    String sql = SELECT_COLLECTION + " WHERE id IN (:ids)";
    MapSqlParameterSource params = createParameterSource().addValue("ids", ids);
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  @Transactional
  public void deleteById(Long id) {
    String sql = "DELETE FROM collection WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    update(sql, params);
  }

  /** Collection ids a person is tagged on via {@code collection_people}. Empty for null/unknown. */
  @Transactional(readOnly = true)
  public List<Long> findCollectionIdsByPersonId(Long personId) {
    if (personId == null) {
      return List.of();
    }
    String sql = "SELECT collection_id FROM collection_people WHERE person_id = :personId";
    MapSqlParameterSource params = createParameterSource().addValue("personId", personId);
    return namedParameterJdbcTemplate.queryForList(sql, params, Long.class);
  }

  // ============================================================
  // CollectionContent Operations
  // ============================================================

  @Transactional(readOnly = true)
  public List<CollectionContentEntity> findContentByCollectionIdOrderByOrderIndex(
      Long collectionId) {
    String sql =
        SELECT_COLLECTION_CONTENT
            + " WHERE collection_id = :collectionId "
            + "ORDER BY order_index ASC";
    MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
    return query(sql, COLLECTION_CONTENT_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<CollectionContentEntity> findContentByCollectionId(
      Long collectionId, int limit, int offset) {
    String sql =
        SELECT_COLLECTION_CONTENT
            + " WHERE collection_id = :collectionId "
            + "ORDER BY order_index ASC "
            + "LIMIT :limit OFFSET :offset";
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("collectionId", collectionId)
            .addValue("limit", limit)
            .addValue("offset", offset);
    return query(sql, COLLECTION_CONTENT_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public long countContentByCollectionId(Long collectionId) {
    String sql = "SELECT COUNT(*) FROM collection_content WHERE collection_id = :collectionId";
    MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
    Long count = namedParameterJdbcTemplate.queryForObject(sql, params, Long.class);
    return count != null ? count : 0L;
  }

  @Transactional(readOnly = true)
  public List<CollectionContentEntity> findImageContentByCollectionIds(List<Long> collectionIds) {
    if (collectionIds == null || collectionIds.isEmpty()) {
      return List.of();
    }
    String sql =
        """
        SELECT cc.id, cc.collection_id, cc.content_id, cc.order_index, cc.visible,
               cc.created_at, cc.updated_at
        FROM collection_content cc
        JOIN content c ON cc.content_id = c.id
        WHERE cc.collection_id IN (:collectionIds) AND c.content_type = 'IMAGE'
        ORDER BY cc.collection_id, cc.order_index ASC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("collectionIds", collectionIds);
    return query(sql, COLLECTION_CONTENT_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Integer getMaxOrderIndexForCollection(Long collectionId) {
    String sql =
        "SELECT MAX(order_index) FROM collection_content WHERE collection_id = :collectionId";
    MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
    return namedParameterJdbcTemplate.queryForObject(sql, params, Integer.class);
  }

  @Transactional
  public void updateContentOrderIndex(Long id, Integer orderIndex) {
    String sql = "UPDATE collection_content SET order_index = :orderIndex WHERE id = :id";
    MapSqlParameterSource params =
        createParameterSource().addValue("orderIndex", orderIndex).addValue("id", id);
    update(sql, params);
  }

  @Transactional
  public void updateContentVisible(Long id, Boolean visible) {
    String sql = "UPDATE collection_content SET visible = :visible WHERE id = :id";
    MapSqlParameterSource params =
        createParameterSource().addValue("visible", visible).addValue("id", id);
    update(sql, params);
  }

  /**
   * Set visible on the (collectionId, contentId) membership row -- the two-key sibling of {@link
   * #updateContentVisible}. Returns the affected row count so callers can 404 a non-member pair.
   */
  @Transactional
  public int updateContentVisibleForContent(Long collectionId, Long contentId, boolean visible) {
    String sql =
        "UPDATE collection_content SET visible = :visible"
            + " WHERE collection_id = :collectionId AND content_id = :contentId";
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("visible", visible)
            .addValue("collectionId", collectionId)
            .addValue("contentId", contentId);
    return update(sql, params);
  }

  @Transactional
  public void deleteContentByCollectionId(Long collectionId) {
    String sql = "DELETE FROM collection_content WHERE collection_id = :collectionId";
    MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
    update(sql, params);
  }

  @Transactional
  public void removeContentFromCollection(Long collectionId, List<Long> contentIds) {
    if (contentIds == null || contentIds.isEmpty()) {
      return;
    }
    String sql =
        "DELETE FROM collection_content WHERE collection_id = :collectionId AND content_id IN (:contentIds)";
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("collectionId", collectionId)
            .addValue("contentIds", contentIds);
    update(sql, params);
  }

  @Transactional(readOnly = true)
  public Optional<CollectionContentEntity> findContentByCollectionIdAndContentId(
      Long collectionId, Long contentId) {
    String sql =
        SELECT_COLLECTION_CONTENT
            + " WHERE collection_id = :collectionId AND content_id = :contentId";
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("collectionId", collectionId)
            .addValue("contentId", contentId);
    return queryForObject(sql, COLLECTION_CONTENT_ROW_MAPPER, params);
  }

  /**
   * Rows for the given content ids, ordered by collection id. The ORDER BY is load-bearing: without
   * it Postgres returns an arbitrary order, and any caller that resolves "the" parent of a piece of
   * content picks a different collection on different requests (S1).
   */
  @Transactional(readOnly = true)
  public List<CollectionContentEntity> findContentByContentIdsIn(List<Long> contentIds) {
    if (contentIds == null || contentIds.isEmpty()) {
      return List.of();
    }
    String sql =
        SELECT_COLLECTION_CONTENT + " WHERE content_id IN (:contentIds) ORDER BY collection_id";
    MapSqlParameterSource params = createParameterSource().addValue("contentIds", contentIds);
    return query(sql, COLLECTION_CONTENT_ROW_MAPPER, params);
  }

  @Transactional
  public int batchUpdateContentOrderIndexes(
      Long collectionId, Map<Long, Integer> contentIdToOrderIndex) {
    if (contentIdToOrderIndex == null || contentIdToOrderIndex.isEmpty()) {
      return 0;
    }

    StringBuilder sql =
        new StringBuilder("UPDATE collection_content SET order_index = CASE content_id");
    MapSqlParameterSource params = createParameterSource();
    params.addValue("collectionId", collectionId);

    List<Long> contentIds = new ArrayList<>();
    int index = 0;
    for (Map.Entry<Long, Integer> entry : contentIdToOrderIndex.entrySet()) {
      Long contentId = entry.getKey();
      Integer orderIndex = entry.getValue();
      String contentIdParam = "contentId" + index;
      String orderIndexParam = "orderIndex" + index;

      sql.append(" WHEN :").append(contentIdParam).append(" THEN :").append(orderIndexParam);
      params.addValue(contentIdParam, contentId);
      params.addValue(orderIndexParam, orderIndex);
      contentIds.add(contentId);
      index++;
    }

    sql.append(" END WHERE collection_id = :collectionId AND content_id IN (:contentIds)");
    params.addValue("contentIds", contentIds);

    return update(sql.toString(), params);
  }

  @Transactional
  public CollectionContentEntity saveContent(CollectionContentEntity entity) {
    if (entity.getId() == null) {
      String sql =
          """
          INSERT INTO collection_content (collection_id, content_id, order_index, visible, created_at, updated_at)
          VALUES (:collectionId, :contentId, :orderIndex, :visible, :createdAt, :updatedAt)
          """;
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("collectionId", entity.getCollectionId())
              .addValue("contentId", entity.getContentId())
              .addValue("orderIndex", entity.getOrderIndex())
              .addValue("visible", entity.getVisible())
              .addValue(
                  "createdAt",
                  entity.getCreatedAt() != null ? entity.getCreatedAt() : LocalDateTime.now())
              .addValue(
                  "updatedAt",
                  entity.getUpdatedAt() != null ? entity.getUpdatedAt() : LocalDateTime.now());
      Long id = insertAndReturnId(sql, "id", params);
      entity.setId(id);
      return entity;
    } else {
      String sql =
          """
          UPDATE collection_content
          SET collection_id = :collectionId, content_id = :contentId, order_index = :orderIndex, visible = :visible, updated_at = :updatedAt
          WHERE id = :id
          """;
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("collectionId", entity.getCollectionId())
              .addValue("contentId", entity.getContentId())
              .addValue("orderIndex", entity.getOrderIndex())
              .addValue("visible", entity.getVisible())
              .addValue("updatedAt", LocalDateTime.now())
              .addValue("id", entity.getId());
      update(sql, params);
      return entity;
    }
  }
}
