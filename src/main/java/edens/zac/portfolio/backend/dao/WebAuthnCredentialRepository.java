package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.WebAuthnCredentialEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JDBC repository for WebAuthn (passkey) credentials. BYTEA columns are bound/read as byte[]. */
@Component
@Slf4j
public class WebAuthnCredentialRepository extends BaseDao {

  public WebAuthnCredentialRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final RowMapper<WebAuthnCredentialEntity> ROW_MAPPER =
      (rs, rowNum) ->
          WebAuthnCredentialEntity.builder()
              .id(rs.getLong("id"))
              .userId(rs.getLong("user_id"))
              .credentialId(rs.getBytes("credential_id"))
              .publicKey(rs.getBytes("public_key"))
              .signCount(rs.getLong("sign_count"))
              .transports(rs.getString("transports"))
              .label(rs.getString("label"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .lastUsedAt(getLocalDateTime(rs, "last_used_at"))
              .build();

  private static final String SELECT_COLUMNS =
      "id, user_id, credential_id, public_key, sign_count, transports, label, "
          + "created_at, last_used_at";

  /**
   * Insert a new credential row and return the generated id.
   *
   * @param credential entity to insert
   * @return generated primary-key id
   */
  @Transactional
  public Long insert(WebAuthnCredentialEntity credential) {
    String sql =
        """
        INSERT INTO webauthn_credential
          (user_id, credential_id, public_key, sign_count, transports, label)
        VALUES
          (:userId, :credentialId, :publicKey, :signCount, :transports, :label)
        """;
    var params =
        createParameterSource()
            .addValue("userId", credential.getUserId())
            .addValue("credentialId", credential.getCredentialId())
            .addValue("publicKey", credential.getPublicKey())
            .addValue("signCount", credential.getSignCount())
            .addValue("transports", credential.getTransports())
            .addValue("label", credential.getLabel());
    return insertAndReturnId(sql, "id", params);
  }

  /**
   * Find all credentials for a given user, ordered by creation time ascending.
   *
   * @param userId app_user primary key
   * @return list of credentials (may be empty)
   */
  @Transactional(readOnly = true)
  public List<WebAuthnCredentialEntity> findByUserId(Long userId) {
    String sql =
        "SELECT "
            + SELECT_COLUMNS
            + " FROM webauthn_credential WHERE user_id = :userId "
            + "ORDER BY created_at ASC";
    var params = createParameterSource().addValue("userId", userId);
    return query(sql, ROW_MAPPER, params);
  }

  /**
   * Find a credential by its raw credential-id bytes.
   *
   * @param credentialId raw BYTEA credential id
   * @return credential if found
   */
  @Transactional(readOnly = true)
  public Optional<WebAuthnCredentialEntity> findByCredentialId(byte[] credentialId) {
    String sql =
        "SELECT "
            + SELECT_COLUMNS
            + " FROM webauthn_credential WHERE credential_id = :credentialId";
    var params = createParameterSource().addValue("credentialId", credentialId);
    return queryForObject(sql, ROW_MAPPER, params);
  }

  /**
   * Update the sign count and last-used timestamp after a successful assertion.
   *
   * @param id credential primary key
   * @param signCount new sign count (must be strictly greater than stored value)
   * @param lastUsedAt timestamp of the successful assertion
   */
  @Transactional
  public void updateSignCountAndLastUsed(Long id, long signCount, LocalDateTime lastUsedAt) {
    String sql =
        """
        UPDATE webauthn_credential
        SET sign_count = :signCount, last_used_at = :lastUsedAt
        WHERE id = :id
        """;
    var params =
        createParameterSource()
            .addValue("id", id)
            .addValue("signCount", signCount)
            .addValue("lastUsedAt", lastUsedAt);
    update(sql, params);
  }
}
