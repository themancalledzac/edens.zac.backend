package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.types.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class AppUserRepository extends BaseDao {

  public AppUserRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final String SELECT_APP_USER =
      """
      SELECT id, email, password_hash, webauthn_user_handle, name, status,
             created_at, updated_at
      FROM users
      """;

  private static final RowMapper<AppUserEntity> APP_USER_ROW_MAPPER =
      (rs, rowNum) -> {
        Object handle = rs.getObject("webauthn_user_handle");
        return AppUserEntity.builder()
            .id(rs.getLong("id"))
            .email(rs.getString("email"))
            .passwordHash(rs.getString("password_hash"))
            .webauthnUserHandle(handle != null ? (UUID) handle : null)
            .name(rs.getString("name"))
            .status(UserStatus.valueOf(rs.getString("status")))
            .createdAt(getLocalDateTime(rs, "created_at"))
            .updatedAt(getLocalDateTime(rs, "updated_at"))
            .build();
      };

  @Transactional(readOnly = true)
  public Optional<AppUserEntity> findByEmail(String email) {
    String sql = SELECT_APP_USER + " WHERE email = :email";
    MapSqlParameterSource params = createParameterSource().addValue("email", email);
    return queryForObject(sql, APP_USER_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<AppUserEntity> findById(Long id) {
    String sql = SELECT_APP_USER + " WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, APP_USER_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<AppUserEntity> findByWebauthnUserHandle(UUID handle) {
    String sql = SELECT_APP_USER + " WHERE webauthn_user_handle = :handle";
    MapSqlParameterSource params = createParameterSource().addValue("handle", handle);
    return queryForObject(sql, APP_USER_ROW_MAPPER, params);
  }

  /**
   * All users, newest first. Backs the admin user list; callers must not expose sensitive fields.
   */
  @Transactional(readOnly = true)
  public List<AppUserEntity> findAllOrderedByCreatedAt() {
    String sql = SELECT_APP_USER + " ORDER BY created_at DESC";
    return query(sql, APP_USER_ROW_MAPPER, createParameterSource());
  }

  @Transactional
  public Long insert(AppUserEntity entity) {
    String sql =
        """
        INSERT INTO users (email, password_hash, webauthn_user_handle, name, status)
        VALUES (:email, :passwordHash, :webauthnUserHandle, :name, :status)
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("email", entity.getEmail())
            .addValue("passwordHash", entity.getPasswordHash())
            .addValue("webauthnUserHandle", entity.getWebauthnUserHandle())
            .addValue("name", entity.getName())
            .addValue("status", entity.getStatus().name());
    return insertAndReturnId(sql, "id", params);
  }

  @Transactional
  public void updatePasswordHash(Long id, String hash) {
    String sql = "UPDATE users SET password_hash = :hash, updated_at = now() WHERE id = :id";
    MapSqlParameterSource params =
        createParameterSource().addValue("hash", hash).addValue("id", id);
    update(sql, params);
  }

  @Transactional
  public void updateStatus(Long id, UserStatus status) {
    String sql = "UPDATE users SET status = :status, updated_at = now() WHERE id = :id";
    MapSqlParameterSource params =
        createParameterSource().addValue("status", status.name()).addValue("id", id);
    update(sql, params);
  }

  @Transactional
  public void updateName(Long id, String name) {
    String sql = "UPDATE users SET name = :name, updated_at = now() WHERE id = :id";
    MapSqlParameterSource params =
        createParameterSource().addValue("name", name).addValue("id", id);
    update(sql, params);
  }
}
