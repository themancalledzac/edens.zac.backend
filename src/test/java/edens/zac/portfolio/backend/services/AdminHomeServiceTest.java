package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.AdminHomeTileRepository;
import edens.zac.portfolio.backend.dao.AdminHomeTileRepository.TileRow;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.CollectionType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminHomeServiceTest {

  @Mock private AdminHomeTileRepository tileRepository;
  @Mock private CollectionService collectionService;
  @Mock private ContentRepository contentRepository;

  private AdminHomeService service;

  @BeforeEach
  void setUp() {
    service = new AdminHomeService(tileRepository, collectionService, contentRepository);
  }

  /**
   * Build a CollectionModel whose cover image carries only the supplied imageUrl.
   * ContentModels.Image is a 28-field record; everything except imageUrl is null because the
   * service only reads the URL.
   */
  private static CollectionModel collectionWithCoverUrl(long id, String slug, String url) {
    ContentModels.Image cover =
        new ContentModels.Image(
            null, // id
            null, // contentType
            null, // title
            null, // description
            url, // imageUrl
            null, // imageUrlRaw
            null, // orderIndex
            null, // visible
            null, // createdAt
            null, // updatedAt
            null, // imageWidth
            null, // imageHeight
            null, // iso
            null, // author
            null, // rating
            null, // fStop
            null, // lens
            null, // blackAndWhite
            null, // isFilm
            null, // filmType
            null, // filmFormat
            null, // shutterSpeed
            null, // camera
            null, // focalLength
            null, // locations
            null, // captureDate
            null, // tags
            null, // people
            null); // collections
    return CollectionModel.builder().id(id).slug(slug).coverImage(cover).build();
  }

  @Nested
  class GetTiles {

    @Test
    void resolvesCoversForCardTilesAndLeavesOthersNull() {
      when(tileRepository.findAllOrderedByDisplay())
          .thenReturn(
              List.of(
                  new TileRow("home", 0),
                  new TileRow("all-collections", 1),
                  new TileRow("all-images", 2),
                  new TileRow("metadata", 3),
                  new TileRow("comments", 4),
                  new TileRow("blogs", 5),
                  new TileRow("client-galleries", 6),
                  new TileRow("create", 7),
                  new TileRow("manage", 8),
                  new TileRow("about", 9)));

      var homeChild = collectionWithCoverUrl(11L, "film", "https://cdn/example/home-child.webp");
      when(collectionService.findChildCollectionsForHome()).thenReturn(List.of(homeChild));

      var anyVisible = collectionWithCoverUrl(21L, "any", "https://cdn/example/any.webp");
      when(collectionService.findAllListedWithCovers()).thenReturn(List.of(anyVisible));

      when(contentRepository.findRandomImageWebUrl())
          .thenReturn(Optional.of("https://cdn/example/random-image.webp"));

      var blog = collectionWithCoverUrl(31L, "trip", "https://cdn/example/blog.webp");
      when(collectionService.findVisibleByTypeOrderByDate(CollectionType.BLOG))
          .thenReturn(List.of(blog));

      var gallery = collectionWithCoverUrl(41L, "smith", "https://cdn/example/gallery.webp");
      when(collectionService.findByTypeForAdminCovers(CollectionType.CLIENT_GALLERY))
          .thenReturn(List.of(gallery));

      List<Records.AdminHomeTileResponse> tiles = service.getTiles();

      assertThat(tiles).hasSize(10);
      assertThat(tiles)
          .extracting(Records.AdminHomeTileResponse::tileKey)
          .containsExactly(
              "home",
              "all-collections",
              "all-images",
              "metadata",
              "comments",
              "blogs",
              "client-galleries",
              "create",
              "manage",
              "about");
      assertThat(tiles.get(0).coverImageUrl()).isEqualTo("https://cdn/example/home-child.webp");
      assertThat(tiles.get(1).coverImageUrl()).isEqualTo("https://cdn/example/any.webp");
      assertThat(tiles.get(2).coverImageUrl()).isEqualTo("https://cdn/example/random-image.webp");
      assertThat(tiles.get(3).coverImageUrl()).isNull(); // metadata - no source
      assertThat(tiles.get(4).coverImageUrl()).isNull(); // comments - no source
      assertThat(tiles.get(5).coverImageUrl()).isEqualTo("https://cdn/example/blog.webp");
      assertThat(tiles.get(6).coverImageUrl()).isEqualTo("https://cdn/example/gallery.webp");
      assertThat(tiles.get(7).coverImageUrl()).isNull(); // create - no source
      assertThat(tiles.get(8).coverImageUrl()).isNull(); // manage - no source
      assertThat(tiles.get(9).coverImageUrl()).isNull(); // about - no source
      assertThat(tiles.get(0).displayOrder()).isZero();
      assertThat(tiles.get(9).displayOrder()).isEqualTo(9);
    }

    @Test
    void cachesResultUntilEvicted() {
      when(tileRepository.findAllOrderedByDisplay())
          .thenReturn(List.of(new TileRow("metadata", 3)));

      service.getTiles();
      service.getTiles();
      service.getTiles();

      verify(tileRepository, times(1)).findAllOrderedByDisplay();
    }

    @Test
    void evictAllForcesRecomputeOnNextCall() {
      when(tileRepository.findAllOrderedByDisplay())
          .thenReturn(List.of(new TileRow("metadata", 3)));

      service.getTiles();
      service.evictAll();
      service.getTiles();

      verify(tileRepository, times(2)).findAllOrderedByDisplay();
    }

    @Test
    void emptyCandidatesYieldNullCover() {
      when(tileRepository.findAllOrderedByDisplay()).thenReturn(List.of(new TileRow("blogs", 5)));
      when(collectionService.findVisibleByTypeOrderByDate(CollectionType.BLOG))
          .thenReturn(List.of());

      List<Records.AdminHomeTileResponse> tiles = service.getTiles();

      assertThat(tiles).hasSize(1);
      assertThat(tiles.get(0).coverImageUrl()).isNull();
    }

    @Test
    void unknownTileKeyYieldsNullCoverWithoutThrowing() {
      when(tileRepository.findAllOrderedByDisplay())
          .thenReturn(List.of(new TileRow("not-a-known-key", 99)));

      List<Records.AdminHomeTileResponse> tiles = service.getTiles();

      assertThat(tiles).hasSize(1);
      assertThat(tiles.get(0).tileKey()).isEqualTo("not-a-known-key");
      assertThat(tiles.get(0).coverImageUrl()).isNull();
      verifyNoMoreInteractions(collectionService, contentRepository);
    }
  }
}
