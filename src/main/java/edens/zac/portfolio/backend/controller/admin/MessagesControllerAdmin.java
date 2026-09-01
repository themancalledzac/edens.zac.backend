package edens.zac.portfolio.backend.controller.admin;

import edens.zac.portfolio.backend.dao.MessageRepository;
import edens.zac.portfolio.backend.model.MessageRequests;
import edens.zac.portfolio.backend.services.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin REST controller for reading contact messages.
 *
 * <p>Runs in dev and prod (no {@code @Profile} gating). In prod, access is restricted by {@link
 * edens.zac.portfolio.backend.config.InternalSecretFilter}.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/messages")
public class MessagesControllerAdmin {

  private final MessageRepository messageRepository;
  private final MessageService messageService;

  /**
   * One page of messages, newest first.
   *
   * @param unread omitted for both states, {@code true} for unread only, {@code false} for read
   *     only
   * @param q case-insensitive substring of the sender address or the body; omitted for no filter
   * @return the page plus the total matching the SAME filters, so the admin list's "N of M" counts
   *     one row set rather than two
   */
  @GetMapping
  public ResponseEntity<MessageRequests.AdminMessageList> list(
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(required = false) Boolean unread,
      @RequestParam(required = false) String q) {
    int safeLimit = Math.max(1, Math.min(limit, 200));
    int safeOffset = Math.max(0, offset);
    var rows = messageRepository.findAll(safeLimit, safeOffset, unread, q);
    long total = messageRepository.count(unread, q);
    var view =
        rows.stream()
            .map(
                m ->
                    new MessageRequests.AdminMessageView(
                        m.getId(), m.getEmail(), m.getMessage(), m.getCreatedAt(), m.getReadAt()))
            .toList();
    return ResponseEntity.ok(
        new MessageRequests.AdminMessageList(view, total, safeLimit, safeOffset));
  }

  /**
   * Mark a message read or unread.
   *
   * @param id the message id
   * @param body omitted or {@code {"read": true}} to mark read, {@code {"read": false}} to undo
   * @return {@code 204 No Content}, or {@code 404} if no such message
   */
  @PatchMapping("/{id}/read")
  public ResponseEntity<Void> markRead(
      @PathVariable long id, @RequestBody(required = false) MessageRequests.MarkRead body) {
    boolean read = body == null || body.read() == null || body.read();
    int rows = messageService.markRead(id, read);
    log.info("Marked message id={} read={} rowsAffected={}", id, read, rows);
    return rows > 0 ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
  }

  /**
   * Delete a contact message.
   *
   * @param id the message id
   * @return {@code 204 No Content}, or {@code 404} if no such message
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable long id) {
    int rows = messageService.delete(id);
    log.info("Deleted message id={} rowsAffected={}", id, rows);
    return rows > 0 ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
  }
}
