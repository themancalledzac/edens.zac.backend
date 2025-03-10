package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.CatalogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogRepository extends JpaRepository<CatalogEntity, Long> {

    Optional<CatalogEntity> findByTitle(String title); // Primary Method

    @Query("SELECT c FROM CatalogEntity c LEFT JOIN FETCH c.images i WHERE c.id = :id ORDER BY i.createDate ASC")
    Optional<CatalogEntity> findByIdWithImages(@Param("id") Long id);

    @Query("SELECT c FROM CatalogEntity c WHERE c.id = :id")
    Optional<CatalogEntity> findCatalogById(@Param("id") Long id);

    @Query("Select c FROM CatalogEntity c WHERE c.slug = :slug")
    Optional<CatalogEntity> findCatalogBySlug(@Param("slug") String slug);

    @Query("SELECT c FROM CatalogEntity c WHERE c.priority <= :minimumPriority ORDER BY c.priority ASC, c.createdDate DESC")
    List<CatalogEntity> getAllCatalogs(@Param("minimumPriority") Integer minimumPriority);
}
