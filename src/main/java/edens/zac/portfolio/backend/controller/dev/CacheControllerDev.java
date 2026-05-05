package edens.zac.portfolio.backend.controller.dev;

import edens.zac.portfolio.backend.services.AdminHomeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only admin cache controller. Exposes a single POST endpoint that clears in-process caches
 * (currently only the admin home tile cover cache). Called by the FE "Clear Cache" action through
 * the BFF proxy. No-op for prod (route not registered under any profile other than "dev").
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/cache")
@Profile("dev")
class CacheControllerDev {

  private final AdminHomeService adminHomeService;

  @PostMapping("/clear")
  ResponseEntity<Void> clearCache() {
    log.info("Clearing in-process admin caches");
    adminHomeService.evictAll();
    return ResponseEntity.noContent().build();
  }
}
