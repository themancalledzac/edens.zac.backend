package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.BlogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlogRepository extends JpaRepository<BlogEntity, Long> {

    BlogEntity save(BlogEntity blog);

}
