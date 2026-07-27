package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

  @Mock private SesV2Client sesClient;

  private EmailService newService(boolean enabled) {
    return new EmailService(sesClient, enabled, "no-reply@edens.zac", "https://edens.zac");
  }

  @Nested
  class Disabled {

    @Test
    void shortCircuitsWithoutCallingSes() {
      EmailService service = newService(false);

      EmailService.SendResult result =
          service.sendGalleryPasswordEmail(
              "client@example.com", "Smith Wedding", "smith-wedding", "abcdef12");

      assertThat(result.sent()).isFalse();
      assertThat(result.reason()).isEqualTo("email-disabled");
      verify(sesClient, never()).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void inviteShortCircuitsWithoutCallingSes() {
      EmailService service = newService(false);

      EmailService.SendResult result =
          service.sendInviteEmail(
              "invitee@example.com", "Dana", "https://edens.zac/invite/tok-123");

      // Invites must stay shippable while SES verification is in flight: the send no-ops and the
      // caller still returns the copyable link.
      assertThat(result.sent()).isFalse();
      assertThat(result.reason()).isEqualTo("email-disabled");
      verify(sesClient, never()).sendEmail(any(SendEmailRequest.class));
    }
  }

  @Nested
  class Enabled {

    @Test
    void sendsRequestWithExpectedShape() {
      EmailService service = newService(true);

      EmailService.SendResult result =
          service.sendGalleryPasswordEmail(
              "client@example.com", "Smith Wedding", "smith-wedding", "abcdef12");

      ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
      verify(sesClient).sendEmail(captor.capture());

      SendEmailRequest sent = captor.getValue();
      assertThat(sent.fromEmailAddress()).isEqualTo("no-reply@edens.zac");
      assertThat(sent.destination().toAddresses()).containsExactly("client@example.com");

      String subject = sent.content().simple().subject().data();
      assertThat(subject).contains("Smith Wedding");

      Body body = sent.content().simple().body();
      String htmlBody = body.html().data();
      String textBody = body.text().data();

      // Password lands in both bodies.
      assertThat(htmlBody).contains("abcdef12");
      assertThat(textBody).contains("abcdef12");

      // Gallery URL is built from frontend-base-url + slug.
      assertThat(htmlBody).contains("https://edens.zac/smith-wedding");
      assertThat(textBody).contains("https://edens.zac/smith-wedding");

      assertThat(result.sent()).isTrue();
      assertThat(result.reason()).isNull();
    }

    @Test
    void htmlEscapesInterpolatedTitle() {
      EmailService service = newService(true);

      service.sendGalleryPasswordEmail(
          "client@example.com", "<script>alert('xss')</script>", "evil-slug", "safe-password-123");

      ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
      verify(sesClient).sendEmail(captor.capture());
      String htmlBody = captor.getValue().content().simple().body().html().data();

      assertThat(htmlBody).doesNotContain("<script>alert('xss')</script>");
      assertThat(htmlBody).contains("&lt;script&gt;");
    }

    @Test
    void htmlEscapesPasswordWithSpecialChars() {
      EmailService service = newService(true);

      service.sendGalleryPasswordEmail("client@example.com", "Title", "slug", "p<>&\"'word");

      ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
      verify(sesClient).sendEmail(captor.capture());
      String htmlBody = captor.getValue().content().simple().body().html().data();
      String textBody = captor.getValue().content().simple().body().text().data();

      // HTML body: special chars are escaped.
      assertThat(htmlBody).contains("p&lt;&gt;&amp;&quot;&#39;word");
      assertThat(htmlBody).doesNotContain("p<>&\"'word");

      // Text body: raw password (no HTML, no escaping needed).
      assertThat(textBody).contains("p<>&\"'word");
    }

    @Test
    void sendsInviteRequestWithExpectedShape() {
      EmailService service = newService(true);

      EmailService.SendResult result =
          service.sendInviteEmail(
              "invitee@example.com", "Dana", "https://edens.zac/invite/tok-123");

      ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
      verify(sesClient).sendEmail(captor.capture());

      SendEmailRequest sent = captor.getValue();
      assertThat(sent.fromEmailAddress()).isEqualTo("no-reply@edens.zac");
      assertThat(sent.destination().toAddresses()).containsExactly("invitee@example.com");
      assertThat(sent.content().simple().subject().data()).contains("invited");

      Body body = sent.content().simple().body();
      // The caller-built URL is used verbatim in both bodies — it must match the copyable link.
      assertThat(body.html().data()).contains("https://edens.zac/invite/tok-123");
      assertThat(body.text().data()).contains("https://edens.zac/invite/tok-123");
      assertThat(body.html().data()).contains("Dana");
      assertThat(body.text().data()).contains("Dana");

      assertThat(result.sent()).isTrue();
      assertThat(result.reason()).isNull();
    }

    @Test
    void inviteFallsBackToGenericGreetingWhenDisplayNameMissing() {
      EmailService service = newService(true);

      service.sendInviteEmail("invitee@example.com", null, "https://edens.zac/invite/tok-123");

      ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
      verify(sesClient).sendEmail(captor.capture());
      Body body = captor.getValue().content().simple().body();

      assertThat(body.text().data()).startsWith("Hello,");
      assertThat(body.html().data()).contains("Hello,");
      assertThat(body.text().data()).doesNotContain("null");
      assertThat(body.html().data()).doesNotContain("null");
    }

    @Test
    void inviteHtmlEscapesDisplayName() {
      EmailService service = newService(true);

      service.sendInviteEmail(
          "invitee@example.com", "<script>alert('xss')</script>", "https://edens.zac/invite/tok");

      ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
      verify(sesClient).sendEmail(captor.capture());
      String htmlBody = captor.getValue().content().simple().body().html().data();

      assertThat(htmlBody).doesNotContain("<script>alert('xss')</script>");
      assertThat(htmlBody).contains("&lt;script&gt;");
    }

    @Test
    void inviteSesExceptionReturnsErrorReason() {
      EmailService service = newService(true);
      when(sesClient.sendEmail(any(SendEmailRequest.class)))
          .thenThrow(SesV2Exception.builder().message("rejected").build());

      EmailService.SendResult result =
          service.sendInviteEmail("invitee@example.com", "Dana", "https://edens.zac/invite/tok");

      assertThat(result.sent()).isFalse();
      assertThat(result.reason()).isEqualTo("ses-error");
    }

    @Test
    void sesExceptionReturnsErrorReason() {
      EmailService service = newService(true);
      when(sesClient.sendEmail(any(SendEmailRequest.class)))
          .thenThrow(SesV2Exception.builder().message("rejected").build());

      EmailService.SendResult result =
          service.sendGalleryPasswordEmail("client@example.com", "Title", "slug", "password-1234");

      assertThat(result.sent()).isFalse();
      assertThat(result.reason()).isEqualTo("ses-error");
    }

    @Test
    void sdkClientExceptionReturnsErrorReason() {
      // Network failure / credential failure / region misconfig throws SdkClientException
      // (NOT a subclass of SesV2Exception). Without the widened catch, this would crash with a
      // 500 instead of returning a typed reason.
      EmailService service = newService(true);
      when(sesClient.sendEmail(any(SendEmailRequest.class)))
          .thenThrow(SdkClientException.builder().message("connection refused").build());

      EmailService.SendResult result =
          service.sendGalleryPasswordEmail("client@example.com", "Title", "slug", "password-1234");

      assertThat(result.sent()).isFalse();
      assertThat(result.reason()).isEqualTo("ses-error");
    }
  }
}
