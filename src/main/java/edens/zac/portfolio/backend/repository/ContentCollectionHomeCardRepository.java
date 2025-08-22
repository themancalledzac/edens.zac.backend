package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.ContentCollectionHomeCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContentCollectionHomeCardRepository extends JpaRepository<ContentCollectionHomeCardEntity, Long> {

    @Query("SELECT c FROM ContentCollectionHomeCardEntity c WHERE c.priority >= :maxPriority AND c.isActiveHomeCard = true ORDER BY c.priority ASC, c.createdDate DESC")
    List<ContentCollectionHomeCardEntity> getHomePage(@Param("maxPriority") Integer maxPriority);

    Optional<ContentCollectionHomeCardEntity> findByCardTypeAndReferenceId(String cardType, Long referenceId);

    Optional<ContentCollectionHomeCardEntity> findByReferenceId(Long referenceId);
}
