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

    @Query("SELECT b FROM BlogEntity b LEFT JOIN FETCH b.images WHERE b.id = :id")
    Optional<BlogEntity> findByIdWithImages(@Param("id") Long id);

    @Query("SELECT b FROM BlogEntity b WHERE b.id = :id")
    Optional<BlogEntity> findById(@Param("id") Long id);
}
