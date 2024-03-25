package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.AdventureEntity;
import edens.zac.portfolio.backend.model.AdventureModalDTO;
import edens.zac.portfolio.backend.model.AdventureModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdventureRepository extends JpaRepository<AdventureEntity, Long> {

    Optional<AdventureEntity> findByName(String name);

    List<AdventureEntity> findByMainAdventureTrue();

    @Query("SELECT new edens.zac.portfolio.backend.model.AdventureModalDTO(a.id, a.name, a.imageMainTitle, a.mainAdventure) " +
            "FROM AdventureEntity a WHERE a.mainAdventure = true")
    List<AdventureModalDTO> findMainAdventures();

    AdventureEntity save(AdventureEntity adventure);

}
