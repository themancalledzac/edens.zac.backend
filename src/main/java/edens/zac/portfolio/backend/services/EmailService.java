package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.MessageEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Sends email notifications via Gmail SMTP for contact form submissions. */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

  private final JavaMailSender mailSender;

  @Value("${app.contact.notify-to:${spring.mail.username:}}")
  private String notifyTo;

  @Value("${spring.mail.username:}")
  private String from;

  /** Sends an owner notification email for the given contact message. */
  public void sendContactNotification(MessageEntity msg) {
    var mail = new SimpleMailMessage();
    mail.setFrom(from);
    mail.setTo(notifyTo);
    mail.setSubject("New contact message [id=" + msg.getId() + "]");
    mail.setText(
        "From: "
            + msg.getEmail()
            + "\nSent: "
            + msg.getCreatedAt()
            + "\nMessage ID: "
            + msg.getId()
            + "\n\n"
            + msg.getMessage());
    mailSender.send(mail);
    log.debug("Contact notification sent for message id={}", msg.getId());
  }
}
