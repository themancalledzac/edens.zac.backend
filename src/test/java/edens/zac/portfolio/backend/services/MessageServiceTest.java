package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.MessageRepository;
import edens.zac.portfolio.backend.entity.MessageEntity;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

  @Mock private MessageRepository messageRepository;
  @Mock private EmailService emailService;

  @InjectMocks private MessageService messageService;

  private MessageEntity savedEntity;

  @BeforeEach
  void setUp() {
    savedEntity = new MessageEntity();
    savedEntity.setId(1L);
    savedEntity.setEmail("user@example.com");
    savedEntity.setMessage("Hello");
    savedEntity.setCreatedAt(LocalDateTime.now());
  }

  @Nested
  class Create {

    @Test
    void happyPath_insertsEmailsAndMarksSent() {
      when(messageRepository.insert("user@example.com", "Hello")).thenReturn(savedEntity);

      MessageEntity result = messageService.create("user@example.com", "Hello");

      assertThat(result.getId()).isEqualTo(1L);
      verify(emailService).sendContactNotification(savedEntity);
      verify(messageRepository).markEmailSent(1L);
    }

    @Test
    void emailFailure_swallowsException_doesNotMarkSent() {
      when(messageRepository.insert("user@example.com", "Hello")).thenReturn(savedEntity);
      doThrow(new RuntimeException("SMTP error")).when(emailService).sendContactNotification(any());

      MessageEntity result = messageService.create("user@example.com", "Hello");

      assertThat(result.getId()).isEqualTo(1L);
      verify(messageRepository, never()).markEmailSent(any());
    }
  }
}
