package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.models.Image;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ImageRepository extends JpaRepository<Image, Object> {
    @Query("SELECT q FROM Image q WHERE q.image LIKE %?1%")
    List<Image> getContainingQuote(String word);
}
