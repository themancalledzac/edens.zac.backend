package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.MessageRepository;
import edens.zac.portfolio.backend.entity.MessageEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Orchestrates contact message persistence and best-effort owner email notification. */
@Service
@Slf4j
@RequiredArgsConstructor
public class MessageService {

  private final MessageRepository messageRepository;
  private final EmailService emailService;

  /**
   * Persists a contact message and attempts to send an owner notification email.
   *
   * <p>The message is always saved regardless of email outcome. If the notification fails, the
   * exception is swallowed and logged so that the caller still receives the saved entity.
   *
   * @param email sender's email address
   * @param message body of the contact message
   * @return the persisted {@link MessageEntity}
   */
  public MessageEntity create(String email, String message) {
    MessageEntity entity = messageRepository.insert(email, message);
    try {
      emailService.sendContactNotification(entity);
      messageRepository.markEmailSent(entity.getId());
    } catch (Exception e) {
      log.error("Failed to send contact notification for message id={}", entity.getId(), e);
    }
    return entity;
  }
}
