package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.MessageRepository;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Retention TTL for contact-form messages, which are PII: a stranger's email address next to
 * whatever they chose to write.
 *
 * <p>Deleting them is irreversible — the contact form is the only writer and nothing archives what
 * a purge removes. So this is off unless two separate properties are set, and enabling only the
 * first gets a report rather than a deletion:
 *
 * <ul>
 *   <li>{@code app.messages.retention.days} — 0 (the default) means the job returns before it
 *       touches the database at all.
 *   <li>{@code app.messages.retention.dry-run} — true (the default) means it logs the count it
 *       would delete and deletes nothing.
 * </ul>
 *
 * <p>Run the reporting mode first and read the count. A local backend can point at the production
 * database, so "try it on localhost" is not a safe way to find out what this does.
 */
@Service
@Slf4j
public class MessageRetentionService {

  private final MessageRepository messageRepository;
  private final int retentionDays;
  private final boolean dryRun;

  /** Both properties default to the safe end: retention off, and reporting rather than deleting. */
  public MessageRetentionService(
      MessageRepository messageRepository,
      @Value("${app.messages.retention.days:0}") int retentionDays,
      @Value("${app.messages.retention.dry-run:true}") boolean dryRun) {
    this.messageRepository = messageRepository;
    this.retentionDays = retentionDays;
    this.dryRun = dryRun;
  }

  /** Daily at 03:15 server time. Does nothing unless {@code retention.days} is above zero. */
  @Scheduled(cron = "0 15 3 * * *")
  public void purgeExpiredMessages() {
    if (retentionDays <= 0) {
      return;
    }
    purgeOlderThan(LocalDateTime.now().minusDays(retentionDays));
  }

  /**
   * Report or delete messages older than {@code cutoff}, and return the number of rows the call
   * removed — always 0 in dry-run mode.
   *
   * <p>Package-private and cutoff-taking so a test can exercise both modes without aging a row or
   * waiting for the cron, the same arrangement {@code JobTrackingService} uses.
   */
  int purgeOlderThan(LocalDateTime cutoff) {
    if (dryRun) {
      long candidates = messageRepository.countCreatedBefore(cutoff);
      log.info(
          "Message retention DRY RUN: {} message(s) older than {} would be deleted. Set"
              + " app.messages.retention.dry-run=false to delete them.",
          candidates,
          cutoff);
      return 0;
    }

    int deleted = messageRepository.deleteCreatedBefore(cutoff);
    log.info("Message retention: deleted {} message(s) older than {}", deleted, cutoff);
    return deleted;
  }
}
