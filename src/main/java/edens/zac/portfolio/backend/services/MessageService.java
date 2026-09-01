package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.MessageRepository;
import edens.zac.portfolio.backend.entity.MessageEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Persists contact-form messages. Kept as a service for future seam (auditing, dispatch, etc.). */
@Service
@Slf4j
@RequiredArgsConstructor
public class MessageService {

  private final MessageRepository messageRepository;

  public MessageEntity create(String email, String message) {
    return messageRepository.insert(email, message);
  }

  public int delete(long id) {
    return messageRepository.deleteById(id);
  }

  /** Set or clear a message's read marker. Returns rows affected: 0 means no such message. */
  public int markRead(long id, boolean read) {
    return messageRepository.markRead(id, read);
  }
}
