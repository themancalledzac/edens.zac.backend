package edens.zac.portfolio.backend.controller.dev;

import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.services.AdminHomeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/admin-home")
@Profile("dev")
class AdminHomeControllerDev {

  private final AdminHomeService adminHomeService;

  @GetMapping("/tiles")
  ResponseEntity<List<Records.AdminHomeTileResponse>> getTiles() {
    List<Records.AdminHomeTileResponse> tiles = adminHomeService.getTiles();
    log.info("Returning {} admin home tiles", tiles.size());
    return ResponseEntity.ok(tiles);
  }
}
