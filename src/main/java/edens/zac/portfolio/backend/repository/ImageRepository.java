package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.model.Image;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ImageRepository {
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ImageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(Image image) {
        String sql = "INSERT INTO images (uuid, name, location, imageUrlLarge, imageUrlSmall, imageUrlRaw, rating, date, createDate, updateDate) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, image.getUuid(), image.getName(), image.getLocation(), image.getImageUrlLarge(), image.getImageUrlSmall(), image.getImageUrlRaw(), image.getRating(), image.getDate(), image.getCreateDate(), image.getUpdateDate());
    }


}
