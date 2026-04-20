package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import edens.zac.portfolio.backend.entity.MessageEntity;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

  @Mock private JavaMailSender mailSender;

  private EmailService emailService;

  @BeforeEach
  void setUp() {
    emailService = new EmailService(mailSender);
    ReflectionTestUtils.setField(emailService, "notifyTo", "owner@example.com");
    ReflectionTestUtils.setField(emailService, "from", "sender@example.com");
  }

  private MessageEntity testMessage() {
    MessageEntity msg = new MessageEntity();
    msg.setId(1L);
    msg.setEmail("user@example.com");
    msg.setMessage("Hello, I'd like to connect.");
    msg.setCreatedAt(LocalDateTime.of(2026, 4, 19, 10, 0));
    return msg;
  }

  @Test
  void sendContactNotificationCallsMailSender() {
    emailService.sendContactNotification(testMessage());
    verify(mailSender).send(any(SimpleMailMessage.class));
  }

  @Test
  void sendContactNotificationPropagatesSmtpException() {
    doThrow(new MailSendException("SMTP unavailable"))
        .when(mailSender)
        .send(any(SimpleMailMessage.class));
    assertThatThrownBy(() -> emailService.sendContactNotification(testMessage()))
        .isInstanceOf(MailSendException.class);
  }
}
