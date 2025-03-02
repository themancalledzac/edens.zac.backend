package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.model.BlogCreateDTO;
import edens.zac.portfolio.backend.model.BlogModel;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface BlogService {

    @Transactional(readOnly = true)
    List<BlogModel> getAllBlogs();

    BlogModel createBlog(BlogCreateDTO blogDTO, List<MultipartFile> images);
}
