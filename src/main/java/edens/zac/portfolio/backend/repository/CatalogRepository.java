package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.CatalogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CatalogRepository extends JpaRepository<CatalogEntity, Long> {

    Optional<CatalogEntity> findByTitle(String title); // Primary Method

    //
//    List<CatalogEntity> findByPriority(Integer priority);

//    @Query("SELECT new edens.zac.portfolio.backend.model.CatalogModalDTO(a.id, a.name, a.imageMainTitle, a.mainCatalog, a.priority) " +
//            "FROM CatalogEntity a WHERE a.mainCatalog = true")
//    List<CatalogModalDTO> findMainCatalogs();

    @Query("SELECT c FROM CatalogEntity c LEFT JOIN FETCH c.images i WHERE c.id = :id ORDER BY i.createDate DESC")
    Optional<CatalogEntity> findByIdWithImages(@Param("id") Long id);

    @Query("SELECT c FROM CatalogEntity c WHERE c.id = :id")
    Optional<CatalogEntity> findCatalogById(@Param("id") Long id);

    @Query("SELECT c FROM CatalogEntity c LEFT JOIN FETCH c.images i WHERE c.slug = :slug ORDER BY i.createDate DESC")
    Optional<CatalogEntity> findBySlugWithImages(@Param("slug") String slug);

    @Query("Select c FROM CatalogEntity c WHERE c.slug = :slug")
    Optional<CatalogEntity> findCatalogBySlug(String slug);
}
