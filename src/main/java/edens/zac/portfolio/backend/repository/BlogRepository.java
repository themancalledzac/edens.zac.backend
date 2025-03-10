package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.BlogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlogRepository extends JpaRepository<BlogEntity, Long> {

    BlogEntity save(BlogEntity blog);

    @Query("SELECT b FROM BlogEntity b LEFT JOIN FETCH b.images i WHERE b.id = :id ORDER BY i.createDate DESC")
    Optional<BlogEntity> findByIdWithImages(@Param("id") Long id);

    // Fallback get by ID with no images
    @Query("SELECT b FROM BlogEntity b WHERE b.id = :id")
    Optional<BlogEntity> findBlogById(@Param("id") Long id);

    // Fallback get by Slug with no images
    @Query("SELECT b FROM BlogEntity b WHERE b.slug = :slug")
    Optional<BlogEntity> findBlogBySlug(@Param("slug") String slug);

    @Query("SELECT b FROM BlogEntity b WHERE b.priority <= :minimumPriority ORDER BY b.priority ASC, b.createdDate DESC")
    List<BlogEntity> getAllBlogs(@Param("minimumPriority") Integer minimumPriority);
}
