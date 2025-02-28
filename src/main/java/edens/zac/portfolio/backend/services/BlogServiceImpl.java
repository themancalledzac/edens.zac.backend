package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.BlogEntity;
import edens.zac.portfolio.backend.model.BlogModel;
import edens.zac.portfolio.backend.repository.BlogRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class BlogServiceImpl implements BlogService {
    private final BlogRepository blogRepository;

    public BlogServiceImpl(BlogRepository blogRepository) {
        this.blogRepository = blogRepository;
    }

//    public BlogServiceImpl

    @Override
    @Transactional
    public List<BlogModel> getAllBlogs() {
        return null;
    }

    @Override
    public BlogModel createBlog(BlogModel blog) {
        BlogEntity blogEntity = BlogEntity.builder()
                .title(blog.getTitle())
                .date(blog.getDate())
                .location(blog.getLocation())
                .paragraph(blog.getParagraph())
                .author(blog.getAuthor())
                .coverImageUrl(blog.getCoverImageUrl())
                .slug(blog.getSlug())
                .build();
        BlogEntity savedBlog = blogRepository.save(blogEntity);
        return convertToBlogModel(savedBlog);
    }

    private BlogModel convertToBlogModel(BlogEntity blogEntity) {
        BlogModel modalBlog = new BlogModel();
        modalBlog.setId(blogEntity.getId());
        modalBlog.setTitle(blogEntity.getTitle());
        modalBlog.setDate(blogEntity.getDate());
        modalBlog.setLocation(blogEntity.getLocation());
        modalBlog.setParagraph(blogEntity.getParagraph());
        modalBlog.setAuthor(blogEntity.getAuthor());
        modalBlog.setCoverImageUrl(blogEntity.getCoverImageUrl());
        modalBlog.setSlug(blogEntity.getSlug());
        return modalBlog;
    }
}
