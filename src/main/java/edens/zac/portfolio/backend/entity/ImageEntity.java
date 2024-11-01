package edens.zac.portfolio.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "images", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"title", "createDate"})
})
public class ImageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private Integer imageWidth;
    private Integer imageHeight;
    private Integer iso;
    private String author;
    private Integer rating;
    private String fStop;
    private String lens;
    private Boolean blackAndWhite;
    private String shutterSpeed;
    private String rawFileName;
    private String camera;
    private String focalLength;
    private String location;
    private String imageUrlWeb;
    private String imageUrlSmall;
    private String imageUrlRaw;
    private String createDate;
    private LocalDateTime updateDate;

    @ManyToMany(cascade = CascadeType.PERSIST)
    @JoinTable(
            name = "image_catalog", // Name of the join table
            joinColumns = @JoinColumn(name = "image_id"), // Column for Image
            inverseJoinColumns = @JoinColumn(name = "catalog_id") // Column for Catalog
    )
    private Set<CatalogEntity> catalogs = new HashSet<>();

    @Transient
    public List<String> getCatalogNames() {
        return catalogs.stream().map(CatalogEntity::getName).collect(Collectors.toList());
    }
}
