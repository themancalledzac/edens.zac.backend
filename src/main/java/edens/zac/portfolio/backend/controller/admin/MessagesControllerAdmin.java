package edens.zac.portfolio.backend.controller.admin;

import edens.zac.portfolio.backend.dao.MessageRepository;
import edens.zac.portfolio.backend.model.MessageRequests;
import edens.zac.portfolio.backend.services.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

  @GetMapping
  public ResponseEntity<MessageRequests.AdminMessageList> list(
      @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) {
    int safeLimit = Math.max(1, Math.min(limit, 200));
    int safeOffset = Math.max(0, offset);
    var rows = messageRepository.findAll(safeLimit, safeOffset);
    long total = messageRepository.count();
    var view =
        rows.stream()
            .map(
                m ->
                    new MessageRequests.AdminMessageView(
                        m.getId(), m.getEmail(), m.getMessage(), m.getCreatedAt()))
            .toList();
    return ResponseEntity.ok(
        new MessageRequests.AdminMessageList(view, total, safeLimit, safeOffset));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable long id) {
    int rows = messageService.delete(id);
    log.info("Deleted message id={} rowsAffected={}", id, rows);
    return ResponseEntity.noContent().build();
  }
}
