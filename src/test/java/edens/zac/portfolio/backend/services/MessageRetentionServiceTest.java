package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.MessageRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The deletion here is irreversible and the rows are a stranger's email address, so what these
 * tests pin is mostly the refusals: the default configuration must not reach the repository at all,
 * and the first opt-in must count rather than delete.
 */
@ExtendWith(MockitoExtension.class)
class MessageRetentionServiceTest {

  @Mock private MessageRepository messageRepository;

  private MessageRetentionService service(int days, boolean dryRun) {
    return new MessageRetentionService(messageRepository, days, dryRun);
  }

  @Test
  @DisplayName("default configuration never touches the database")
  void defaultsDoNothing() {
    service(0, true).purgeExpiredMessages();

    verifyNoInteractions(messageRepository);
  }

  @Test
  @DisplayName("a negative retention window is treated as off, not as a cutoff in the future")
  void negativeDaysDoNothing() {
    service(-30, false).purgeExpiredMessages();

    verifyNoInteractions(messageRepository);
  }

  @Test
  @DisplayName("days set but dry-run left on counts and deletes nothing")
  void dryRunCountsOnly() {
    when(messageRepository.countCreatedBefore(any())).thenReturn(4L);

    int deleted = service(90, true).purgeOlderThan(LocalDateTime.of(2026, 1, 1, 0, 0));

    assertThat(deleted).isZero();
    verify(messageRepository).countCreatedBefore(LocalDateTime.of(2026, 1, 1, 0, 0));
    verify(messageRepository, never()).deleteCreatedBefore(any());
  }

  @Test
  @DisplayName("both opt-ins set deletes, and reports the row count")
  void bothOptInsDelete() {
    LocalDateTime cutoff = LocalDateTime.of(2026, 1, 1, 0, 0);
    when(messageRepository.deleteCreatedBefore(cutoff)).thenReturn(4);

    int deleted = service(90, false).purgeOlderThan(cutoff);

    assertThat(deleted).isEqualTo(4);
    verify(messageRepository, never()).countCreatedBefore(any());
  }

  @Test
  @DisplayName("the scheduled entry point derives its cutoff from the configured window")
  void scheduledRunDerivesCutoffFromRetentionDays() {
    LocalDateTime before = LocalDateTime.now().minusDays(90);
    when(messageRepository.deleteCreatedBefore(any())).thenReturn(0);

    service(90, false).purgeExpiredMessages();

    LocalDateTime after = LocalDateTime.now().minusDays(90);
    ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(messageRepository).deleteCreatedBefore(cutoff.capture());
    assertThat(cutoff.getValue()).isBetween(before, after);
  }
}
