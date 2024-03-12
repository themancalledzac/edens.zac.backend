package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ImageRepository extends JpaRepository<Image, UUID> {
    Optional<Image> findByTitleAndCreateDate(String title, String createDate);

    Optional<Image> findById(Long imageId);

    @Query("SELECT i FROM Image i LEFT JOIN FETCH i.adventures WHERE i.id = :id")
    Optional<Image> findByIdWithAdventures(@Param("id") Long id);

    @Query("SELECT i.id FROM Image i JOIN i.adventures a WHERE a.name = :name")
    Set<Long> findImageIdsByAdventureName(@Param("name") String name);

    @Query("SELECT i FROM Image i JOIN FETCH i.adventures a WHERE a.name = :name")
//    @Query("SELECT DISTINCT i FROM Image i JOIN FETCH i.adventures a WHERE a.name = :name")
    List<Image> findByAdventureName(@Param("name") String name);
}
