package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.services.UserSavesService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Per-user saved images. Every endpoint is session-required (401 when anonymous), self-only. */
@RestController
@RequestMapping("/api/read/user/saves")
@RequiredArgsConstructor
public class UserSavesControllerProd {

  private final UserSavesService userSavesService;

  /** Body of {@code POST /api/read/user/saves}. */
  public record AddSaveRequest(@NotNull Long imageId) {}

  /** Add an image to the caller's saves. 201 on success, 401 when anonymous. */
  @PostMapping
  public ResponseEntity<Void> add(
      @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody AddSaveRequest body) {
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    userSavesService.add(principal.userId(), body.imageId());
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  /** Remove an image from the caller's saves. 204 on success, 401 when anonymous. */
  @DeleteMapping("/{imageId}")
  public ResponseEntity<Void> remove(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long imageId) {
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    userSavesService.remove(principal.userId(), imageId);
    return ResponseEntity.noContent().build();
  }

  /** The caller's saved image ids, newest-saved first. 401 when anonymous. */
  @GetMapping
  public ResponseEntity<List<Long>> list(@AuthenticationPrincipal AuthPrincipal principal) {
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    return ResponseEntity.ok(userSavesService.listSavedImageIds(principal.userId()));
  }

  /** The caller's saved images as full models, newest-saved first. 401 when anonymous. */
  @GetMapping("/images")
  public ResponseEntity<List<ContentModels.Image>> listImages(
      @AuthenticationPrincipal AuthPrincipal principal) {
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    return ResponseEntity.ok(userSavesService.listSavedImages(principal.userId()));
  }
}
