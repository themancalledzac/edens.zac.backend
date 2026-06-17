package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.UserSessionEntity;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class UserSessionRepository extends BaseDao {

  public UserSessionRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final String SELECT_USER_SESSION =
      """
      SELECT id, user_id, token_hash, mfa_satisfied, ip, user_agent,
             created_at, last_seen_at, expires_at, revoked_at
      FROM user_session
      """;

  private static final RowMapper<UserSessionEntity> USER_SESSION_ROW_MAPPER =
      (rs, rowNum) ->
          UserSessionEntity.builder()
              .id(rs.getLong("id"))
              .userId(rs.getLong("user_id"))
              .tokenHash(rs.getString("token_hash"))
              .mfaSatisfied(rs.getBoolean("mfa_satisfied"))
              .ip(rs.getString("ip"))
              .userAgent(rs.getString("user_agent"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .lastSeenAt(getLocalDateTime(rs, "last_seen_at"))
              .expiresAt(getLocalDateTime(rs, "expires_at"))
              .revokedAt(getLocalDateTime(rs, "revoked_at"))
              .build();

  @Transactional
  public Long insert(UserSessionEntity entity) {
    String sql =
        """
        INSERT INTO user_session (user_id, token_hash, mfa_satisfied, ip, user_agent, expires_at)
        VALUES (:userId, :tokenHash, :mfaSatisfied, :ip, :userAgent, :expiresAt)
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("userId", entity.getUserId())
            .addValue("tokenHash", entity.getTokenHash())
            .addValue("mfaSatisfied", entity.isMfaSatisfied())
            .addValue("ip", entity.getIp())
            .addValue("userAgent", entity.getUserAgent())
            .addValue("expiresAt", entity.getExpiresAt());
    return insertAndReturnId(sql, "id", params);
  }

  @Transactional(readOnly = true)
  public Optional<UserSessionEntity> findByTokenHash(String tokenHash) {
    String sql = SELECT_USER_SESSION + " WHERE token_hash = :tokenHash";
    MapSqlParameterSource params = createParameterSource().addValue("tokenHash", tokenHash);
    return queryForObject(sql, USER_SESSION_ROW_MAPPER, params);
  }

  @Transactional
  public void touch(Long id, LocalDateTime lastSeenAt, LocalDateTime expiresAt) {
    String sql =
        "UPDATE user_session SET last_seen_at = :lastSeenAt, expires_at = :expiresAt WHERE id = :id";
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("lastSeenAt", lastSeenAt)
            .addValue("expiresAt", expiresAt)
            .addValue("id", id);
    update(sql, params);
  }

  @Transactional
  public void revokeByTokenHash(String tokenHash) {
    String sql =
        "UPDATE user_session SET revoked_at = now() WHERE token_hash = :tokenHash AND revoked_at IS NULL";
    MapSqlParameterSource params = createParameterSource().addValue("tokenHash", tokenHash);
    update(sql, params);
  }
}
