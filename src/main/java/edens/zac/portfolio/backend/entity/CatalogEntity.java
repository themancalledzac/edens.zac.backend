package edens.zac.portfolio.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// TODO:
//  1. Update catalogs to ALSO contain 'MainImage', 'Location', 'StartTime', 'EndTime', 'People', 'Strava'
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "catalogs", uniqueConstraints = {
        @UniqueConstraint(columnNames = "name")
})
public class CatalogEntity {

    // TODO: Update to have a 'catalog_priority' number
    //  This is needed so we don't have to organize by id ( which is basically uuid and not an order ). this way we can also update what organization it is.

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String imageMainTitle;
    private Boolean mainCatalog;
    private Long priority;

    @ManyToMany(mappedBy = "catalogs")
    private Set<ImageEntity> images = new HashSet<>();

    public CatalogEntity(String catalogName) {
        this.name = catalogName;
    }
}
