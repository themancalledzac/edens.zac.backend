package edens.zac.portfolio.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "images", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"title", "createDate"})
})
public class Image {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String imageWidth;
    private String imageHeight;
    private String iso;
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
    private String imageUrlLarge;
    private String imageUrlSmall;
    private String imageUrlRaw;
    private String createDate;
    private LocalDateTime updateDate;

//    @ManyToMany
//    @JoinTable(
//            name = "image_adventure", // Name of the join table
//            joinColumns = @JoinColumn(name = "image_id"), // Column for Image
//            inverseJoinColumns = @JoinColumn(name = "adventure_id") // Column for Adventure
//    )
//    private Set<Adventure> adventures = new HashSet<>();
}
