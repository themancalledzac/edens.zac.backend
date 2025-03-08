package edens.zac.portfolio.backend.controller;

import edens.zac.portfolio.backend.model.HomeCardModel;
import edens.zac.portfolio.backend.services.HomeService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/home")
public class HomeController {

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

    // TODO:
    @CrossOrigin(origins = "http://localhost:3000")
    @PostMapping(value = "/createHomeCard/{type}/{slug}")
    public ResponseEntity<?> createHomeCardFromPrevious(@PathVariable String type, @PathVariable String slug) {
        return null;
    }


}
