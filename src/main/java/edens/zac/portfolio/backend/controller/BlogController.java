package edens.zac.portfolio.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.model.BlogCreateDTO;
import edens.zac.portfolio.backend.model.BlogModel;
import edens.zac.portfolio.backend.services.BlogService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/blog")
public class BlogController {

    private final BlogService blogService;

    @CrossOrigin(origins = "http://localhost:3000")
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

    @GetMapping("/{id}")
    public ResponseEntity<?> getBlogWithImages(@PathVariable Long id) {
        try {
            BlogModel blog = blogService.getBlogById(id);
            if (blog == null) {
                log.warn("Blog is null, returning 404");
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body("Blog with ID " + id + " not found");
            }
            return ResponseEntity.ok(blog);
        } catch (Exception e) {
            log.error("Error getting blog {}: {}", id, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve blog: " + e.getMessage());
        }
    }


    @GetMapping("/getAll")
    public List<BlogModel> getAllBlogs() {
        // TODO: add 'page(default=0)' and 'size(default=10)' params
        return blogService.getAllBlogs();
    }

    @GetMapping("/slug/{slug}")
    public BlogModel getBlogBySlug(@PathVariable String slug) {
        // return blogService.getBlogBySlug(slug);
        return null;
    }

    @GetMapping("/byLocation/{location}")
    public BlogModel getBlogByLocation(@PathVariable String location) {
        // return blogService.getBlogByLocation(location);
        return null;
    }
}
