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

    @Query("SELECT h FROM ContentCollectionHomeCardEntity h " +
           "LEFT JOIN ContentCollectionEntity c ON h.referenceId = c.id " +
           "WHERE h.priority <= :maxPriority " +
           "AND h.isActiveHomeCard = true " +
           "AND h.coverImageUrl IS NOT NULL " +
           "AND h.coverImageUrl <> '' " +
           "AND (h.referenceId IS NULL OR c.visible = true) " +
           "ORDER BY h.priority ASC, h.createdDate DESC")
    List<ContentCollectionHomeCardEntity> getHomePage(@Param("maxPriority") Integer maxPriority);

    Optional<ContentCollectionHomeCardEntity> findByCardTypeAndReferenceId(String cardType, Long referenceId);

    Optional<ContentCollectionHomeCardEntity> findByReferenceId(Long referenceId);
}
