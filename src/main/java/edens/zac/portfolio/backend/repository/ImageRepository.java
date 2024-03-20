package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.ImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ImageRepository extends JpaRepository<ImageEntity, UUID> {
    Optional<ImageEntity> findByTitleAndCreateDate(String title, String createDate);

    Optional<ImageEntity> findById(Long imageId);

    @Query("SELECT i FROM ImageEntity i LEFT JOIN FETCH i.adventures WHERE i.id = :id")
    Optional<ImageEntity> findByIdWithAdventures(@Param("id") Long id);

    @Query("SELECT i.id FROM ImageEntity i JOIN i.adventures a WHERE a.name = :name")
    Set<Long> findImageIdsByAdventureName(@Param("name") String name);

    @Query("SELECT i.id FROM ImageEntity i JOIN i.adventures a WHERE a.name = :name AND i.rating = 5 AND i.imageWidth > i.imageHeight")
    Set<Long> findImageIdsByAdventureNameAndCriteria(@Param("name") String name);

    @Query("SELECT i FROM ImageEntity i LEFT JOIN FETCH i.adventures WHERE i.id = :id AND i.rating >= :minRating")
    Optional<ImageEntity> findByIdWithAdventuresAndMinRating(@Param("id") Long id, @Param("minRating") Integer minRating);

    @Query("SELECT i FROM ImageEntity i JOIN FETCH i.adventures a WHERE a.name = :name")
//    @Query("SELECT DISTINCT i FROM Image i JOIN FETCH i.adventures a WHERE a.name = :name")
    List<ImageEntity> findByAdventureName(@Param("name") String name);
}
