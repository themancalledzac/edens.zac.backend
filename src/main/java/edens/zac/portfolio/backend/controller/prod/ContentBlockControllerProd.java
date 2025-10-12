package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.entity.ContentTagEntity;
import edens.zac.portfolio.backend.repository.ContentPersonRepository;
import edens.zac.portfolio.backend.repository.ContentTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/read/blocks")
public class ContentBlockControllerProd {

    private final ContentTagRepository contentTagRepository;
    private final ContentPersonRepository contentPersonRepository;

    /**
     * Get all tags (ordered alphabetically)
     * GET /api/read/blocks/tags
     *
     * @return ResponseEntity with list of all tags
     */
    @GetMapping("/tags")
    public ResponseEntity<?> getAllTags() {
        try {
            List<ContentTagEntity> tags = contentTagRepository.findAllByOrderByTagNameAsc();

            // Convert to simple DTO format
            List<TagResponse> response = tags.stream()
                    .map(tag -> new TagResponse(
                            tag.getId(),
                            tag.getTagName(),
                            tag.getTotalUsageCount()
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting all tags: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve tags: " + e.getMessage());
        }
    }

    /**
     * Get all people (ordered alphabetically)
     * GET /api/read/blocks/people
     *
     * @return ResponseEntity with list of all people
     */
    @GetMapping("/people")
    public ResponseEntity<?> getAllPeople() {
        try {
            List<ContentPersonEntity> people = contentPersonRepository.findAllByOrderByPersonNameAsc();

            // Convert to simple DTO format
            List<PersonResponse> response = people.stream()
                    .map(person -> new PersonResponse(
                            person.getId(),
                            person.getPersonName(),
                            person.getImageCount()
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting all people: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve people: " + e.getMessage());
        }
    }

    /**
     * Response DTO for tags
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class TagResponse {
        private Long id;
        private String tagName;
        private int usageCount;
    }

    /**
     * Response DTO for people
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class PersonResponse {
        private Long id;
        private String personName;
        private int imageCount;
    }
}
