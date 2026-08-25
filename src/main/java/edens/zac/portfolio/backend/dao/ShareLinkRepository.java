package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.ShareLinkEntity;
import edens.zac.portfolio.backend.types.AccessLevel;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * JdbcTemplate repository for {@code share_link} and its {@code share_link_collection} opt-ins.
 * Links are looked up by the SHA-256 hash of the raw token; the token is additionally stored
 * encrypted (V58) so its owner can read it back to re-send it, which hashing alone made impossible.
 * Lookup always goes through the hash -- the ciphertext varies per call and cannot be an index.
 *
 * <p>{@link #rotateToken} is the "reset link" primitive. It updates the existing row rather than
 * inserting a replacement, so the owner's opt-in rows survive a reset -- and because the cookie
 * carries the same raw token as the URL, that one write invalidates the old link and every live
 * cookie together.
 */
@Component
@Slf4j
public class ShareLinkRepository extends BaseDao {

  public ShareLinkRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final String SELECT_SHARE_LINK =
      """
      SELECT id, user_id, token_hash, token_cipher, level, created_at, rotated_at, last_used_at
      FROM share_link
      """;

  private static final RowMapper<ShareLinkEntity> SHARE_LINK_ROW_MAPPER =
      (rs, rowNum) ->
          ShareLinkEntity.builder()
              .id(rs.getLong("id"))
              .userId(rs.getLong("user_id"))
              .tokenHash(rs.getString("token_hash"))
              .tokenCipher(rs.getString("token_cipher"))
              .level(AccessLevel.valueOf(rs.getString("level")))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .rotatedAt(getLocalDateTime(rs, "rotated_at"))
              .lastUsedAt(getLocalDateTime(rs, "last_used_at"))
              .build();

  @Transactional
  public Long insert(ShareLinkEntity entity) {
    String sql =
        """
        INSERT INTO share_link (user_id, token_hash, token_cipher, level)
        VALUES (:userId, :tokenHash, :tokenCipher, :level)
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("userId", entity.getUserId())
            .addValue("tokenHash", entity.getTokenHash())
            .addValue("tokenCipher", entity.getTokenCipher())
            .addValue(
                "level",
                (entity.getLevel() == null ? AccessLevel.GENERAL : entity.getLevel()).name());
    return insertAndReturnId(sql, "id", params);
  }

  @Transactional(readOnly = true)
  public Optional<ShareLinkEntity> findByTokenHash(String tokenHash) {
    String sql = SELECT_SHARE_LINK + " WHERE token_hash = :tokenHash";
    MapSqlParameterSource params = createParameterSource().addValue("tokenHash", tokenHash);
    return queryForObject(sql, SHARE_LINK_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<ShareLinkEntity> findById(Long id) {
    String sql = SELECT_SHARE_LINK + " WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, SHARE_LINK_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<ShareLinkEntity> findByUserId(Long userId) {
    String sql = SELECT_SHARE_LINK + " WHERE user_id = :userId";
    MapSqlParameterSource params = createParameterSource().addValue("userId", userId);
    return queryForObject(sql, SHARE_LINK_ROW_MAPPER, params);
  }

  /**
   * Point a user's existing link at a new token. This is the whole of "reset link": the old hash
   * stops resolving, so both the previously shared URL and every cookie minted from it die at once,
   * while {@code share_link_collection} rows -- keyed on the unchanged row id -- are untouched.
   *
   * @param userId the owner
   * @param newTokenHash SHA-256 hash of the freshly generated raw token
   * @param newTokenCipher the same token encrypted, so the owner can read it back
   * @return rows affected -- {@code 1} on success, {@code 0} when the user has no link yet
   */
  @Transactional
  public int rotateToken(Long userId, String newTokenHash, String newTokenCipher) {
    String sql =
        """
        UPDATE share_link
           SET token_hash = :tokenHash, token_cipher = :tokenCipher, rotated_at = now()
         WHERE user_id = :userId
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("tokenHash", newTokenHash)
            .addValue("tokenCipher", newTokenCipher)
            .addValue("userId", userId);
    return update(sql, params);
  }

  /** Record that a link was used, for the owner's "last opened" display. */
  @Transactional
  public int touchLastUsed(Long id) {
    String sql = "UPDATE share_link SET last_used_at = now() WHERE id = :id";
    return update(sql, createParameterSource().addValue("id", id));
  }

  @Transactional(readOnly = true)
  public List<Long> findOptInCollectionIds(Long shareLinkId) {
    if (shareLinkId == null) {
      return List.of();
    }
    String sql =
        "SELECT collection_id FROM share_link_collection WHERE share_link_id = :shareLinkId";
    MapSqlParameterSource params = createParameterSource().addValue("shareLinkId", shareLinkId);
    return namedParameterJdbcTemplate.queryForList(sql, params, Long.class);
  }

  /**
   * Every collection in a share's scope: the collections the owner is tagged in, unioned with the
   * collections they explicitly opted in. Resolved in one query -- the tagged-in half joins through
   * {@code share_link.user_id} rather than being passed in, so no caller can accidentally compute
   * the scope of one share against another user's tags.
   *
   * <p>Deliberately a live query, not a stored list. Snapshotting would mean a collection the owner
   * is tagged in tomorrow never appears in a link they already sent.
   */
  @Transactional(readOnly = true)
  public List<Long> findScopeCollectionIds(Long shareLinkId) {
    if (shareLinkId == null) {
      return List.of();
    }
    String sql =
        """
        SELECT cp.collection_id
          FROM collection_people cp
          JOIN share_link sl ON sl.user_id = cp.person_id
         WHERE sl.id = :shareLinkId
        UNION
        SELECT slc.collection_id
          FROM share_link_collection slc
         WHERE slc.share_link_id = :shareLinkId
        """;
    MapSqlParameterSource params = createParameterSource().addValue("shareLinkId", shareLinkId);
    return namedParameterJdbcTemplate.queryForList(sql, params, Long.class);
  }

  /**
   * Whether one collection is in a share's scope. The membership test on the access path, kept as a
   * single EXISTS rather than materializing {@link #findScopeCollectionIds} and searching it --
   * this runs on every per-collection authorization check a link holder makes.
   */
  @Transactional(readOnly = true)
  public boolean isCollectionInScope(Long shareLinkId, Long collectionId) {
    if (shareLinkId == null || collectionId == null) {
      return false;
    }
    String sql =
        """
        SELECT EXISTS(
          SELECT 1
            FROM share_link sl
           WHERE sl.id = :shareLinkId
             AND (EXISTS(SELECT 1 FROM collection_people cp
                          WHERE cp.person_id = sl.user_id
                            AND cp.collection_id = :collectionId)
               OR EXISTS(SELECT 1 FROM share_link_collection slc
                          WHERE slc.share_link_id = sl.id
                            AND slc.collection_id = :collectionId))
        )
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("shareLinkId", shareLinkId)
            .addValue("collectionId", collectionId);
    return Boolean.TRUE.equals(
        namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class));
  }

  /**
   * Opt a role-granted collection into a share. Idempotent -- re-toggling on an already-included
   * collection affects zero rows rather than raising a duplicate-key error.
   */
  @Transactional
  public int addOptIn(Long shareLinkId, Long collectionId) {
    String sql =
        """
        INSERT INTO share_link_collection (share_link_id, collection_id)
        VALUES (:shareLinkId, :collectionId)
        ON CONFLICT DO NOTHING
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("shareLinkId", shareLinkId)
            .addValue("collectionId", collectionId);
    return update(sql, params);
  }

  @Transactional
  public int removeOptIn(Long shareLinkId, Long collectionId) {
    String sql =
        """
        DELETE FROM share_link_collection
         WHERE share_link_id = :shareLinkId AND collection_id = :collectionId
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("shareLinkId", shareLinkId)
            .addValue("collectionId", collectionId);
    return update(sql, params);
  }
}
