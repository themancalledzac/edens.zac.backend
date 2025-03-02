package edens.zac.portfolio.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.model.BlogCreateDTO;
import edens.zac.portfolio.backend.model.BlogModel;
import edens.zac.portfolio.backend.services.BlogService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/getAll")
    public List<BlogModel> getAllBlogs() {
        return blogService.getAllBlogs();
    }

    @GetMapping("/{id}")
    public BlogModel getBlog(@PathVariable Long id) {
        // return blogService.getBlogById(id);
        return null;
    }

    @GetMapping("/slug/{slug}")
    public BlogModel getBlogBySlug(@PathVariable String slug) {
        // return blogService.getBlogBySlug(slug);
        return null;
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @PostMapping(value = "/createBlog",
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<BlogModel> createBlog(
            @RequestPart("blogDTO") String blogDtoJson,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {

        try {
            // convert JSON string to a BlogCreateDTO object
            ObjectMapper objectMapper = new ObjectMapper();
            BlogCreateDTO blogDTO = objectMapper.readValue(blogDtoJson, BlogCreateDTO.class);
            log.info("Request to create blog: {}", blogDTO.getTitle());

            BlogModel createBlog = blogService.createBlog(blogDTO, images);
            return ResponseEntity.ok(createBlog);
        } catch (Exception e) {
            log.error("Error creating blog: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/byLocation/{location}")
    public BlogModel getBlogByLocation(@PathVariable String location) {
        // return blogService.getBlogByLocation(location);
        return null;
    }
}
