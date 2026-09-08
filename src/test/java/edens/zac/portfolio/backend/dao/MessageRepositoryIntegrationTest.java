package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.MessageEntity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Real-Postgres coverage for the two things {@code MessageRepositoryTest} cannot reach. It mocks
 * {@link org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate}, so the row mapper
 * never meets a {@code ResultSet} and {@code SELECT_COLUMNS} is never executed -- which is why
 * dropping {@code read_at} from either survived the whole suite.
 *
 * <p>Every case scopes itself with a unique token in the message body, because {@code
 * AbstractPostgresIntegrationTest} truncates auth tables only and {@code messages} rows outlive the
 * test that wrote them.
 */
class MessageRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MessageRepository messageRepository;

  private String token;

  @BeforeEach
  void seedToken() {
    token = "probe" + UUID.randomUUID().toString().replace("-", "");
  }

  private MessageEntity insert(String email) {
    return messageRepository.insert(email, "contact body " + token);
  }

  @Test
  @DisplayName("findAll maps read_at, so a read message comes back with readAt set")
  void findAll_mapsReadAt() {
    MessageEntity unread = insert("unread@example.com");
    MessageEntity read = insert("read@example.com");
    assertThat(messageRepository.markRead(read.getId(), true)).isEqualTo(1);

    List<MessageEntity> rows = messageRepository.findAll(50, 0, null, token);

    assertThat(rows).hasSize(2);
    assertThat(rows)
        .filteredOn(m -> m.getId().equals(read.getId()))
        .singleElement()
        .satisfies(m -> assertThat(m.getReadAt()).isNotNull());
    assertThat(rows)
        .filteredOn(m -> m.getId().equals(unread.getId()))
        .singleElement()
        .satisfies(m -> assertThat(m.getReadAt()).isNull());
  }

  @Test
  @DisplayName("findAll clears readAt again when a message is marked unread")
  void findAll_mapsReadAtBackToNull() {
    MessageEntity message = insert("toggle@example.com");
    messageRepository.markRead(message.getId(), true);
    assertThat(messageRepository.findAll(50, 0, null, token).getFirst().getReadAt()).isNotNull();

    messageRepository.markRead(message.getId(), false);

    assertThat(messageRepository.findAll(50, 0, null, token).getFirst().getReadAt()).isNull();
  }

  @Test
  @DisplayName("count applies both filters, not neither")
  void count_appliesTheSameFiltersAsFindAll() {
    insert("first@example.com");
    insert("second@example.com");
    MessageEntity read = insert("third@example.com");
    messageRepository.markRead(read.getId(), true);

    assertThat(messageRepository.count(null, token)).isEqualTo(3L);
    assertThat(messageRepository.count(true, token)).isEqualTo(2L);
    assertThat(messageRepository.count(false, token)).isEqualTo(1L);
  }

  @Test
  @DisplayName("count agrees with the number of rows findAll would page over")
  void count_matchesFindAll() {
    insert("a@example.com");
    MessageEntity read = insert("b@example.com");
    messageRepository.markRead(read.getId(), true);

    assertThat(messageRepository.count(true, token))
        .isEqualTo(messageRepository.findAll(50, 0, true, token).size());
    assertThat(messageRepository.count(false, token))
        .isEqualTo(messageRepository.findAll(50, 0, false, token).size());
  }
}
