package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.model.BlogModel;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface BlogService {

    @Transactional(readOnly = true)
    List<BlogModel> getAllBlogs();

    BlogModel createBlog(BlogModel blog);
}
