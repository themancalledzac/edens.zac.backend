package edens.zac.portfolio.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@NoArgsConstructor
@Data
public class BlogModelDTO {

    private Long id;
    private String name;
    private LocalDate date;
    private String location;
    private String paragraph;
    private String author;
    private String coverImageUrl;
    private String slug;

    public BlogModelDTO(Long id, String name, LocalDate date, String location, String paragraph, String author, String coverImageUrl, String slug) {
        this.id = id;
        this.name = name;
        this.date = date;
        this.location = location;
        this.paragraph = paragraph;
        this.author = author;
        this.coverImageUrl = coverImageUrl;
        this.slug = slug;
    }
}
