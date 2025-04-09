package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.HomeCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HomeCardRepository extends JpaRepository<HomeCardEntity, Long> {

    @Query("SELECT c FROM HomeCardEntity c WHERE c.priority <= :maxPriority AND c.isActiveHomeCard = true ORDER BY c.priority ASC, c.createdDate DESC")
    List<HomeCardEntity> getHomePage(@Param("maxPriority") Integer maxPriority);

//    // Find a specific type of card
//    List<HomeCardEntity> findByCardType(String cardType);

    // Find by Reference
    Optional<HomeCardEntity> findByCardTypeAndReferenceId(String cardType, Long referenceId);

//    // Find homeCard Collections // TODO Not sure if needed
//    @Query("SELECT c FROM HomeCardEntity c WHERE c.cardType = :cardType AND c.slug = :slug")
//    Optional<HomeCardEntity> findCollectionCard(@Param("cardType") String cardType, @Param("slug") String slug);
}
