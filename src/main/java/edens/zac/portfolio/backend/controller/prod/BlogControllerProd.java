package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.model.BlogModel;
import edens.zac.portfolio.backend.services.BlogService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/read/blog")
public class BlogControllerProd {

    private final BlogService blogService;

    @GetMapping("/{slug}")
    public ResponseEntity<?> getBlogBySlug(@PathVariable String slug) {
        try {
            BlogModel blog = blogService.getBlogBySlug(slug);
            if (blog == null) {
                log.warn("Blog is null, returning 404");
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body("Blog with slug " + slug + " not found");
            }
            return ResponseEntity.ok(blog);
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve blog: " + e.getMessage());
        }
    }

    @GetMapping("byId/{id}")
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

    @GetMapping("/getAllBlogs")
    public ResponseEntity<?> getAllBlogs() {
        try {
            List<BlogModel> blogList = blogService.getAllBlogs();
            if (blogList == null) {
                log.warn("No blogs returned, returning 404");
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body("No blogs found");
            }
            return ResponseEntity.ok(blogList);
        } catch (Exception e) {
            log.error("Error getting blogs: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve blogs: " + e.getMessage());
        }
    }

    @GetMapping("/byLocation/{location}")
    public BlogModel getBlogByLocation(@PathVariable String location) {
        // return blogService.getBlogByLocation(location);
        return null;
    }
}
