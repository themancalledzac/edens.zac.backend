package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.CatalogEntity;
import edens.zac.portfolio.backend.model.CatalogModalDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogRepository extends JpaRepository<CatalogEntity, Long> {

    Optional<CatalogEntity> findByName(String name);

    List<CatalogEntity> findByMainCatalogTrue();

    @Query("SELECT new edens.zac.portfolio.backend.model.CatalogModalDTO(a.id, a.name, a.imageMainTitle, a.mainCatalog, a.priority) " +
            "FROM CatalogEntity a WHERE a.mainCatalog = true")
    List<CatalogModalDTO> findMainCatalogs();

    CatalogEntity save(CatalogEntity catalog);

}
