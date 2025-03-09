package edens.zac.portfolio.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "catalogs", uniqueConstraints = {
        @UniqueConstraint(columnNames = "title")
})
public class CatalogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String location;
    private Integer priority;
    private String coverImageUrl;
    private String slug;
    private LocalDate date;
    private LocalDateTime createdDate;

    @ElementCollection
    @CollectionTable(name = "catalog_people", joinColumns = @JoinColumn(name = "catalog_id"))
    @Column(name = "person")
    private List<String> people = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "catalog_tags", joinColumns = @JoinColumn(name = "catalog_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    @ManyToMany(mappedBy = "catalogs")
    private Set<ImageEntity> images = new HashSet<>();
}
