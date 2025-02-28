package edens.zac.portfolio.backend.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Data
@Builder
public class BlogModel {
    private Long id;
    private String title;
    private String date;
    private String location;
    private String paragraph; // Break the content into paragraphs at some point
    //    private String paragraphShort; // TODO: Do we want a 'smol' paragraph for our index page, on hover or something?
//    private List<ImageModel> images;
    private String author;
    private List<String> tags;
    private String coverImageUrl;
    private String slug;

    public BlogModel(Long id, String title, String date, String location, String paragraph, String author, List<String> tags, String coverImageUrl, String slug) {
        this.id = id;
        this.title = title;
        this.date = date;
        this.location = location;
        this.paragraph = paragraph;
        this.author = author;
        this.tags = tags;
        this.coverImageUrl = coverImageUrl;
        this.slug = slug;

    }
}
