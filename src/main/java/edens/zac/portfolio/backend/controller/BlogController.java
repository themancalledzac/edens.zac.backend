package edens.zac.portfolio.backend.controller;

import edens.zac.portfolio.backend.model.BlogModel;
import edens.zac.portfolio.backend.services.BlogService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    @PostMapping("/createBlog")
    public BlogModel createBlog(@RequestBody BlogModel blog) {
          return blogService.createBlog(blog);
//        return null;
    }

    @GetMapping("/byLocation/{location}")
    public BlogModel getBlogByLocation(@PathVariable String location) {
        // return blogService.getBlogByLocation(location);
        return null;
    }
}
