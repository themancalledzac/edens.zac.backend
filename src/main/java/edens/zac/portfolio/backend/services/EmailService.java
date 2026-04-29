package edens.zac.portfolio.backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

/**
 * Sends transactional gallery emails via AWS SES v2.
 *
 * <p>One public method today: {@link #sendGalleryPasswordEmail}, which delivers a clickable gallery
 * URL plus the plaintext password the admin just set. Returns a typed {@link SendResult} so the
 * caller can surface "email-disabled" or "ses-error" reasons without leaking exception detail.
 *
 * <p>The {@code email.enabled} flag short-circuits the whole flow before any AWS call. This lets
 * the rest of the password admin endpoint ship while SES domain verification and sandbox-removal
 * are in flight.
 */
@Service
@Slf4j
public class EmailService {

  private final SesV2Client sesClient;
  private final boolean enabled;
  private final String fromAddress;
  private final String frontendBaseUrl;

  /**
   * Construct the email service. {@code enabled}, {@code fromAddress} and {@code frontendBaseUrl}
   * are bound from the {@code email.*} properties; tests instantiate this directly with literal
   * values.
   */
  public EmailService(
      SesV2Client sesClient,
      @Value("${email.enabled:false}") boolean enabled,
      @Value("${email.from-address}") String fromAddress,
      @Value("${email.frontend-base-url}") String frontendBaseUrl) {
    this.sesClient = sesClient;
    this.enabled = enabled;
    this.fromAddress = fromAddress;
    this.frontendBaseUrl = frontendBaseUrl;
  }

  /** Result of a send attempt. {@code reason} is {@code null} on success. */
  public record SendResult(boolean sent, String reason) {}

  /**
   * Send the "your gallery is ready" email to a client with a link plus the plaintext password.
   *
   * @param toEmail recipient address (validated upstream by the controller)
   * @param collectionTitle gallery display title (HTML-escaped before interpolation)
   * @param slug URL slug used to build the gallery link
   * @param plaintextPassword the password the admin just set (escaped in HTML, raw in text body)
   * @return {@link SendResult} with {@code sent=true} on success, otherwise a reason code
   */
  public SendResult sendGalleryPasswordEmail(
      String toEmail, String collectionTitle, String slug, String plaintextPassword) {
    if (!enabled) {
      log.info("Email disabled -- skipping gallery password email (slug={}, to={})", slug, toEmail);
      return new SendResult(false, "email-disabled");
    }

    String galleryUrl = frontendBaseUrl + "/" + slug;
    String subject = "Your gallery is ready: " + collectionTitle;
    String htmlBody = buildHtml(collectionTitle, galleryUrl, plaintextPassword);
    String textBody = buildText(collectionTitle, galleryUrl, plaintextPassword);

    SendEmailRequest request =
        SendEmailRequest.builder()
            .fromEmailAddress(fromAddress)
            .destination(Destination.builder().toAddresses(toEmail).build())
            .content(
                EmailContent.builder()
                    .simple(
                        Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(
                                Body.builder()
                                    .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                    .text(Content.builder().data(textBody).charset("UTF-8").build())
                                    .build())
                            .build())
                    .build())
            .build();

    try {
      sesClient.sendEmail(request);
      log.info("Sent gallery password email (slug={}, to={})", slug, toEmail);
      return new SendResult(true, null);
    } catch (SesV2Exception | SdkClientException e) {
      // SesV2Exception = SES API rejected the request (verification, sandbox, recipient).
      // SdkClientException = client-side failure (timeout, credentials, region, network).
      log.error(
          "Failed to send gallery password email (slug={}, to={}, kind={}): {}",
          slug,
          toEmail,
          e.getClass().getSimpleName(),
          e.getMessage());
      return new SendResult(false, "ses-error");
    }
  }

  /**
   * Hardcoded inline-styled HTML email body. Black/white minimal, ~50 lines, no template engine.
   * All interpolated values are HTML-escaped to defend against any future caller passing
   * user-controlled input.
   */
  private String buildHtml(String collectionTitle, String galleryUrl, String plaintextPassword) {
    String safeTitle = HtmlUtils.htmlEscape(collectionTitle);
    String safeUrl = HtmlUtils.htmlEscape(galleryUrl);
    String safePassword = HtmlUtils.htmlEscape(plaintextPassword);
    return "<!DOCTYPE html>"
        + "<html lang=\"en\"><head><meta charset=\"UTF-8\">"
        + "<title>"
        + safeTitle
        + "</title></head>"
        + "<body style=\"margin:0;padding:0;background:#ffffff;color:#111111;"
        + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;\">"
        + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
        + "border=\"0\" style=\"max-width:560px;margin:0 auto;padding:32px 24px;\">"
        + "<tr><td>"
        + "<h1 style=\"margin:0 0 16px 0;font-size:20px;font-weight:600;color:#111111;\">"
        + "Your gallery is ready"
        + "</h1>"
        + "<p style=\"margin:0 0 24px 0;font-size:15px;line-height:1.5;color:#333333;\">"
        + "Your photos from <strong>"
        + safeTitle
        + "</strong> are now available to view and download."
        + "</p>"
        + "<p style=\"margin:0 0 32px 0;\">"
        + "<a href=\""
        + safeUrl
        + "\" "
        + "style=\"display:inline-block;padding:12px 24px;background:#111111;color:#ffffff;"
        + "text-decoration:none;font-size:15px;font-weight:500;border-radius:2px;\">"
        + "View gallery"
        + "</a>"
        + "</p>"
        + "<p style=\"margin:0 0 8px 0;font-size:14px;color:#333333;\">Use this password:</p>"
        + "<p style=\"margin:0 0 24px 0;font-size:16px;color:#111111;\">"
        + "<code style=\"background:#f4f4f4;padding:6px 10px;border-radius:2px;"
        + "font-family:'SFMono-Regular',Menlo,Consolas,monospace;\">"
        + safePassword
        + "</code>"
        + "</p>"
        + "<p style=\"margin:0 0 16px 0;font-size:13px;color:#666666;line-height:1.5;\">"
        + "This password unlocks only this gallery. Keep it private. If you have any trouble, "
        + "reply to this email."
        + "</p>"
        + "<hr style=\"border:0;border-top:1px solid #eeeeee;margin:32px 0;\">"
        + "<p style=\"margin:0;font-size:12px;color:#888888;\">Zac Eden Photography</p>"
        + "</td></tr></table></body></html>";
  }

  /** Plain-text fallback body. Same content, no styling, raw password (no escaping needed). */
  private String buildText(String collectionTitle, String galleryUrl, String plaintextPassword) {
    return "Your gallery '"
        + collectionTitle
        + "' is ready.\n\n"
        + "View it here: "
        + galleryUrl
        + "\n"
        + "Password: "
        + plaintextPassword
        + "\n\n"
        + "This password unlocks only this gallery. Keep it private.\n\n"
        + "-- Zac Eden Photography";
  }
}
