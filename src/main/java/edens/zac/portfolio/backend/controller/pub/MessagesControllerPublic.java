package edens.zac.portfolio.backend.controller.pub;

import edens.zac.portfolio.backend.config.ContactMessageLimiter;
import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.model.MessageRequests;
import edens.zac.portfolio.backend.services.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public REST controller for contact message submission.
 *
 * <p>Accepts contact form messages from anonymous visitors and persists them via {@link
 * MessageService}. No authentication required. Available in all Spring profiles.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/public/messages")
public class MessagesControllerPublic {

  private final MessageService messageService;
  private final ContactMessageLimiter contactMessageLimiter;

  /**
   * Create a new contact message.
   *
   * <p>Validates the request body, enforces a per-email rate limit, delegates persistence to {@link
   * MessageService}, and returns the saved message id and timestamp.
   *
   * @param request validated contact form payload containing email and message body
   * @return 201 Created with {@link MessageRequests.CreatedResponse}, or 429 Too Many Requests when
   *     the per-email limit is exceeded
   */
  @PostMapping
  public ResponseEntity<?> createMessage(
      @Valid @RequestBody MessageRequests.CreateMessage request) {
    log.debug("Received contact message");
    if (!contactMessageLimiter.tryConsume(request.email())) {
      log.warn("Per-email rate limit exceeded");
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
          .body(
              GlobalExceptionHandler.ErrorResponse.of(
                  HttpStatus.TOO_MANY_REQUESTS,
                  "Rate limit exceeded for this email. Please try again later."));
    }
    var entity = messageService.create(request.email(), request.message());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new MessageRequests.CreatedResponse(entity.getId(), entity.getCreatedAt()));
  }
}
