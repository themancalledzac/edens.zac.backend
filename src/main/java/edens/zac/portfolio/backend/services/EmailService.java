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
 * Sends transactional emails via AWS SES v2.
 *
 * <p>Three public senders: {@link #sendGalleryPasswordEmail}, which delivers a clickable gallery
 * URL plus the plaintext password the admin just set; {@link #sendInviteEmail}, which delivers a
 * single-use account-setup link to a newly invited client; and {@link #sendShareLinkEmail}, which
 * delivers a user's own durable share link on their behalf. All three return a typed {@link
 * SendResult} so the caller can surface "email-disabled" or "ses-error" reasons without leaking
 * exception detail.
 *
 * <p>The three bodies are near-identical in shape and are deliberately NOT folded into a shared
 * template path -- that consolidation belongs to its own change, and the promises the bodies make
 * differ (an invite works once and expires; a share link works until its owner resets it).
 *
 * <p>The {@code email.enabled} flag short-circuits the whole flow before any AWS call. This lets
 * the rest of the password admin endpoint ship while SES domain verification and sandbox-removal
 * are in flight — invite creation still returns a copyable link, so nothing depends on delivery.
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

    return dispatch(
        toEmail, subject, htmlBody, textBody, "gallery password email (slug=" + slug + ")");
  }

  /**
   * Send the "you've been invited" email carrying a single-use account-setup link.
   *
   * <p>The invite URL is built by the caller rather than from {@code frontendBaseUrl} here, because
   * the controller already owns that join (and strips a trailing slash), so the emailed link is
   * guaranteed byte-identical to the one returned in the API response for copy-linking.
   *
   * <p>The link is a bearer credential: it is never logged, and the whole body is escaped.
   *
   * @param toEmail recipient address (the invited account's email)
   * @param displayName invitee's display name for the greeting; may be null or blank
   * @param inviteUrl the fully-built {@code <origin>/invite/<token>} link
   * @return {@link SendResult} with {@code sent=true} on success, otherwise a reason code
   */
  public SendResult sendInviteEmail(String toEmail, String displayName, String inviteUrl) {
    if (!enabled) {
      log.info("Email disabled -- skipping invite email (to={})", toEmail);
      return new SendResult(false, "email-disabled");
    }

    String subject = "You've been invited to Zac Eden Photography";
    String htmlBody = buildInviteHtml(displayName, inviteUrl);
    String textBody = buildInviteText(displayName, inviteUrl);

    return dispatch(toEmail, subject, htmlBody, textBody, "invite email");
  }

  /**
   * Send a user's share link on their behalf.
   *
   * <p>Unlike the invite email this is sent by an ordinary user, not an admin, so the copy is
   * deliberately plain about what the recipient is getting: a read-only look at one person's
   * photos, on a link that keeps working until the sender resets it. That last part is the
   * behaviour the whole feature rests on, and it is the opposite of the invite email's "works once,
   * expires in 7 days" -- so the two bodies must not be made to mirror each other.
   *
   * <p>The URL is built by the caller from {@code frontendBaseUrl}, matching {@link
   * #sendInviteEmail}, so the emailed link is byte-identical to the one the sender can copy.
   *
   * <p>The link is a bearer credential: it is never logged, and the whole body is escaped.
   *
   * @param toEmail recipient address, supplied by the sender
   * @param ownerName the sender's display name for the greeting; may be null or blank
   * @param shareUrl the fully-built {@code <origin>/s/<token>} link
   * @return {@link SendResult} with {@code sent=true} on success, otherwise a reason code
   */
  public SendResult sendShareLinkEmail(String toEmail, String ownerName, String shareUrl) {
    if (!enabled) {
      log.info("Email disabled -- skipping share link email (to={})", toEmail);
      return new SendResult(false, "email-disabled");
    }

    String who = isBlank(ownerName) ? "Someone" : ownerName.trim();
    String subject = who + " shared their photos with you";
    String htmlBody = buildShareLinkHtml(who, shareUrl);
    String textBody = buildShareLinkText(who, shareUrl);

    return dispatch(toEmail, subject, htmlBody, textBody, "share link email");
  }

  /**
   * Build and send one SES message, mapping both failure families onto {@code "ses-error"}: {@link
   * SesV2Exception} is the SES API rejecting the request (verification, sandbox, recipient), {@link
   * SdkClientException} a client-side failure (timeout, credentials, region, network).
   *
   * @param label short description used only for logging; never include a token or password
   */
  private SendResult dispatch(
      String toEmail, String subject, String htmlBody, String textBody, String label) {
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
      log.info("Sent {} (to={})", label, toEmail);
      return new SendResult(true, null);
    } catch (SesV2Exception | SdkClientException e) {
      log.error(
          "Failed to send {} (to={}, kind={}): {}",
          label,
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

  /**
   * Hardcoded inline-styled HTML invite body, mirroring {@link #buildHtml}. Every interpolated
   * value is HTML-escaped — the display name is admin-supplied and the URL carries a token.
   */
  private String buildInviteHtml(String displayName, String inviteUrl) {
    String safeUrl = HtmlUtils.htmlEscape(inviteUrl);
    String greeting =
        isBlank(displayName) ? "Hello," : "Hi " + HtmlUtils.htmlEscape(displayName.trim()) + ",";
    return "<!DOCTYPE html>"
        + "<html lang=\"en\"><head><meta charset=\"UTF-8\">"
        + "<title>You've been invited</title></head>"
        + "<body style=\"margin:0;padding:0;background:#ffffff;color:#111111;"
        + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;\">"
        + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
        + "border=\"0\" style=\"max-width:560px;margin:0 auto;padding:32px 24px;\">"
        + "<tr><td>"
        + "<h1 style=\"margin:0 0 16px 0;font-size:20px;font-weight:600;color:#111111;\">"
        + "You've been invited"
        + "</h1>"
        + "<p style=\"margin:0 0 24px 0;font-size:15px;line-height:1.5;color:#333333;\">"
        + greeting
        + " Set up your account to view the galleries and photos shared with you."
        + "</p>"
        + "<p style=\"margin:0 0 32px 0;\">"
        + "<a href=\""
        + safeUrl
        + "\" "
        + "style=\"display:inline-block;padding:12px 24px;background:#111111;color:#ffffff;"
        + "text-decoration:none;font-size:15px;font-weight:500;border-radius:2px;\">"
        + "Set up your account"
        + "</a>"
        + "</p>"
        + "<p style=\"margin:0 0 16px 0;font-size:13px;color:#666666;line-height:1.5;\">"
        + "This link works once and expires in 7 days. Do not forward it — anyone with the link "
        + "can claim the account. If you were not expecting this, you can ignore this email."
        + "</p>"
        + "<hr style=\"border:0;border-top:1px solid #eeeeee;margin:32px 0;\">"
        + "<p style=\"margin:0;font-size:12px;color:#888888;\">Zac Eden Photography</p>"
        + "</td></tr></table></body></html>";
  }

  /** Plain-text invite body. Same content, no styling. */
  private String buildInviteText(String displayName, String inviteUrl) {
    String greeting = isBlank(displayName) ? "Hello," : "Hi " + displayName.trim() + ",";
    return greeting
        + "\n\n"
        + "You've been invited to Zac Eden Photography. Set up your account to view the galleries "
        + "and photos shared with you:\n\n"
        + inviteUrl
        + "\n\n"
        + "This link works once and expires in 7 days. Do not forward it -- anyone with the link "
        + "can claim the account. If you were not expecting this, you can ignore this email.\n\n"
        + "-- Zac Eden Photography";
  }

  /**
   * Inline-styled HTML share-link body, mirroring {@link #buildInviteHtml}. Every interpolated
   * value is escaped -- the name is user-supplied and the URL carries a token.
   */
  private String buildShareLinkHtml(String ownerName, String shareUrl) {
    String safeUrl = HtmlUtils.htmlEscape(shareUrl);
    String safeName = HtmlUtils.htmlEscape(ownerName);
    return "<!DOCTYPE html>"
        + "<html lang=\"en\"><head><meta charset=\"UTF-8\">"
        + "<title>Photos shared with you</title></head>"
        + "<body style=\"margin:0;padding:0;background:#ffffff;color:#111111;"
        + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;\">"
        + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
        + "border=\"0\" style=\"max-width:560px;margin:0 auto;padding:32px 24px;\">"
        + "<tr><td>"
        + "<h1 style=\"margin:0 0 16px 0;font-size:20px;font-weight:600;color:#111111;\">"
        + safeName
        + " shared their photos with you"
        + "</h1>"
        + "<p style=\"margin:0 0 24px 0;font-size:15px;line-height:1.5;color:#333333;\">"
        + "No account or password needed -- just open the link."
        + "</p>"
        + "<p style=\"margin:0 0 32px 0;\">"
        + "<a href=\""
        + safeUrl
        + "\" "
        + "style=\"display:inline-block;padding:12px 24px;background:#111111;color:#ffffff;"
        + "text-decoration:none;font-size:15px;font-weight:500;border-radius:2px;\">"
        + "View the photos"
        + "</a>"
        + "</p>"
        + "<p style=\"margin:0 0 16px 0;font-size:13px;color:#666666;line-height:1.5;\">"
        + "Keep this email -- the same link works every time, for as long as "
        + safeName
        + " leaves it active. It is view-only, and anyone with the link can use it, so only pass "
        + "it on to people you would be happy to show these photos to."
        + "</p>"
        + "<hr style=\"border:0;border-top:1px solid #eeeeee;margin:32px 0;\">"
        + "<p style=\"margin:0;font-size:12px;color:#888888;\">Zac Eden Photography</p>"
        + "</td></tr></table></body></html>";
  }

  /** Plain-text share-link body. Same content, no styling. */
  private String buildShareLinkText(String ownerName, String shareUrl) {
    return ownerName
        + " shared their photos with you.\n\n"
        + "No account or password needed -- just open the link:\n\n"
        + shareUrl
        + "\n\n"
        + "Keep this email -- the same link works every time, for as long as "
        + ownerName
        + " leaves it active. It is view-only, and anyone with the link can use it, so only pass "
        + "it on to people you would be happy to show these photos to.\n\n"
        + "-- Zac Eden Photography";
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
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
