package edens.zac.portfolio.backend.controller.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.model.BlogCreateDTO;
import edens.zac.portfolio.backend.model.BlogModel;
import edens.zac.portfolio.backend.services.BlogService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/write/blog")
@Configuration
@Profile("dev")
public class BlogControllerDev {

    private final BlogService blogService;

    @PostMapping(value = "/createBlog",
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<?> createBlog(
            @RequestPart("blogDTO") String blogDtoJson,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {

        try {
            // convert JSON string to a BlogCreateDTO object
            ObjectMapper objectMapper = new ObjectMapper();
            BlogCreateDTO blogDTO = objectMapper.readValue(blogDtoJson, BlogCreateDTO.class);

            // Validate the blog data
            if (blogDTO.getTitle() == null || blogDTO.getTitle().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Blog title is required");
            }

            // Create Blog
            BlogModel createBlog = blogService.createBlog(blogDTO, images);
            log.info("Successfully created blog: {}", createBlog.getId());

            return ResponseEntity.ok(createBlog);
        } catch (Exception e) {
            log.error("Error creating blog: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to create blog: " + e.getMessage());
        }
    }
}
