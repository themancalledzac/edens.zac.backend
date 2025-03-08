package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.BlogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BlogRepository extends JpaRepository<BlogEntity, Long> {

    BlogEntity save(BlogEntity blog);

    @Query("SELECT b FROM BlogEntity b LEFT JOIN FETCH b.images i WHERE b.id = :id ORDER BY i.createDate DESC")
    Optional<BlogEntity> findByIdWithImages(@Param("id") Long id);

    // Fallback get by ID with no images
    @Query("SELECT b FROM BlogEntity b WHERE b.id = :id")
    Optional<BlogEntity> findBlogById(@Param("id") Long id);

    @Query("SELECT b FROM BlogEntity b LEFT JOIN FETCH b.images i WHERE b.slug = :slug ORDER BY i.createDate DESC")
    Optional<BlogEntity> findBySlugWithImages(@Param("slug") String slug);

    // Fallback get by Slug with no images
    @Query("SELECT b FROM BlogEntity b WHERE b.slug = :slug")
    Optional<BlogEntity> findBlogBySlug(@Param("slug") String slug);
}
