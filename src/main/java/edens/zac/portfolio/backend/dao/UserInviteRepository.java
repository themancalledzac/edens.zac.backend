package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.UserInviteEntity;
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
public class UserInviteRepository extends BaseDao {

  public UserInviteRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final String SELECT_USER_INVITE =
      """
      SELECT id, user_id, token_hash, email, expires_at, used_at, created_by, created_at
      FROM user_invite
      """;

  private static final RowMapper<UserInviteEntity> USER_INVITE_ROW_MAPPER =
      (rs, rowNum) ->
          UserInviteEntity.builder()
              .id(rs.getLong("id"))
              .userId(rs.getLong("user_id"))
              .tokenHash(rs.getString("token_hash"))
              .email(rs.getString("email"))
              .expiresAt(getLocalDateTime(rs, "expires_at"))
              .usedAt(getLocalDateTime(rs, "used_at"))
              .createdBy(getLong(rs, "created_by"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .build();

  @Transactional
  public Long insert(UserInviteEntity entity) {
    String sql =
        """
        INSERT INTO user_invite (user_id, token_hash, email, expires_at, created_by)
        VALUES (:userId, :tokenHash, :email, :expiresAt, :createdBy)
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("userId", entity.getUserId())
            .addValue("tokenHash", entity.getTokenHash())
            .addValue("email", entity.getEmail())
            .addValue("expiresAt", entity.getExpiresAt())
            .addValue("createdBy", entity.getCreatedBy());
    return insertAndReturnId(sql, "id", params);
  }

  @Transactional(readOnly = true)
  public Optional<UserInviteEntity> findByTokenHash(String tokenHash) {
    String sql = SELECT_USER_INVITE + " WHERE token_hash = :tokenHash";
    MapSqlParameterSource params = createParameterSource().addValue("tokenHash", tokenHash);
    return queryForObject(sql, USER_INVITE_ROW_MAPPER, params);
  }

  @Transactional
  public void markUsed(Long id, LocalDateTime usedAt) {
    String sql = "UPDATE user_invite SET used_at = :usedAt WHERE id = :id";
    MapSqlParameterSource params =
        createParameterSource().addValue("usedAt", usedAt).addValue("id", id);
    update(sql, params);
  }
}
