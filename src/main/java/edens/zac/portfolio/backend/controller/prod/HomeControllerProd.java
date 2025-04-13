package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.model.HomeCardModel;
import edens.zac.portfolio.backend.services.HomeService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/read/home")
public class HomeControllerProd {

    private final HomeService homeService;

    @GetMapping("getHome")
    public ResponseEntity<?> getHomePage() {
        try {
            List<HomeCardModel> homeCardList = homeService.getHomePage();
            if (homeCardList.isEmpty()) {
                log.warn("No HomeCard found, returning 404");
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body("No HomeCard found");
            }
            return ResponseEntity.ok(homeCardList);
        } catch (Exception e) {
            log.error("Error while getting home page. {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve catalog: " + e.getMessage());
        }
    }

    @Profile("dev")
    @PutMapping("update")
    public ResponseEntity<?> updateHomePage(@RequestBody HomeCardModel homeCardModel) {
        try {
            // TODO: Take updated List<HomeCardModel>, and update accordingly ( or do we only take changes to minimize data? )
            return null;
        } catch (Exception e) {
            log.error("Error while getting home page. {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve catalog: " + e.getMessage());
        }
    }
}
