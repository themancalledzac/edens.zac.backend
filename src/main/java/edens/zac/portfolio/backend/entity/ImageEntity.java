package edens.zac.portfolio.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private String createDate; // TODO: update this to LocalDatetime - see if that works
    private LocalDateTime updateDate;

    @ManyToMany(mappedBy = "images", cascade = CascadeType.PERSIST)
    private Set<CatalogEntity> catalogs = new HashSet<>();

    @ManyToMany(mappedBy = "images")
    @Builder.Default
    private Set<BlogEntity> blogs = new HashSet<>();

    @Transient
    public List<String> getCatalogNames() {
        return catalogs.stream().map(CatalogEntity::getTitle).collect(Collectors.toList());
    }

    @Transient
    public List<String> getBlogTitles() {
        return blogs == null ? new ArrayList<>() : blogs.stream()
                .map(BlogEntity::getTitle)
                .collect(Collectors.toList());
    }
}
