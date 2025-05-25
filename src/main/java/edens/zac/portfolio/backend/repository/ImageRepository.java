package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.ImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface ImageRepository extends JpaRepository<ImageEntity, UUID>, JpaSpecificationExecutor<ImageEntity> {
    Optional<ImageEntity> findByTitleAndCreateDate(String title, String createDate);

    Optional<ImageEntity> findById(Long id);


    @Query("SELECT i FROM ImageEntity i LEFT JOIN FETCH i.catalogs WHERE i.id = :id")
    Optional<ImageEntity> findByIdWithCatalogs(@Param("id") Long id);

    @Query("SELECT i.id FROM ImageEntity i JOIN i.catalogs a WHERE a.title = :title")
    Set<Long> findImageIdsByCatalogTitle(@Param("title") String title);

    @Query("SELECT i FROM ImageEntity i LEFT JOIN FETCH i.catalogs WHERE i.id = :id AND i.rating >= :minRating")
    Optional<ImageEntity> findByIdWithCatalogsAndMinRating(@Param("id") Long id, @Param("minRating") Integer minRating);

    @Query("SELECT i FROM ImageEntity i JOIN i.catalogs c WHERE c.slug = :slug")
    List<ImageEntity> findImagesByCatalogSlugOrdered(@Param("slug") String slug);

    @Query("SELECT i FROM ImageEntity i JOIN i.catalogs c WHERE c.title = :title")
    List<ImageEntity> findImagesByCatalogTitle(@Param("title") String title);

}
