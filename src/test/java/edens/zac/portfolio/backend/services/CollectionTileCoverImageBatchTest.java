package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.CollectionPeopleRepository;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.CollectionSiblingRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.ContentType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regression guard for the home / parent read path: a collection that contains child-collection
 * tiles must batch-load the tiles' referenced collections and cover images, NOT issue per-tile
 * queries. The per-tile path (findById + findImageById + per-image tags/people/locations) turned
 * the home read into a ~30-query N+1 that cost ~7s over the remote-DB tunnel.
 *
 * <p>Wires a real {@link ContentModelConverter} into a real {@link CollectionProcessingUtil} so the
 * query pattern of the actual read path is exercised end to end.
 */
@ExtendWith(MockitoExtension.class)
class CollectionTileCoverImageBatchTest {

  @Mock private CollectionRepository collectionRepository;
  @Mock private CollectionPeopleRepository collectionPeopleRepository;
  @Mock private CollectionSiblingRepository collectionSiblingRepository;
  @Mock private ContentRepository contentRepository;
  @Mock private ContentMutationUtil contentMutationUtil;
  @Mock private LocationRepository locationRepository;
  @Mock private TagRepository tagRepository;
  @Mock private PersonRepository personRepository;

  private CollectionProcessingUtil util;

  @BeforeEach
  void setUp() {
    ContentModelConverter converter =
        new ContentModelConverter(
            contentRepository,
            collectionRepository,
            tagRepository,
            personRepository,
            locationRepository);
    util =
        new CollectionProcessingUtil(
            collectionRepository,
            collectionPeopleRepository,
            collectionSiblingRepository,
            contentRepository,
            converter,
            contentMutationUtil,
            locationRepository,
            tagRepository,
            personRepository);
  }

  private ContentCollectionEntity tile(long contentId, long referencedCollectionId) {
    CollectionEntity ref = new CollectionEntity();
    ref.setId(referencedCollectionId);
    return ContentCollectionEntity.builder()
        .id(contentId)
        .contentType(ContentType.COLLECTION)
        .referencedCollection(ref)
        .build();
  }

  private CollectionContentEntity join(long contentId, int orderIndex) {
    return CollectionContentEntity.builder()
        .id(contentId)
        .collectionId(34L)
        .contentId(contentId)
        .orderIndex(orderIndex)
        .visible(true)
        .build();
  }

  @Test
  void convertToModel_homeWithChildTiles_batchesCoverImagesWithoutPerTileQueries() {
    CollectionEntity home = new CollectionEntity();
    home.setId(34L);
    home.setType(CollectionType.HOME);
    home.setTitle("Home");
    home.setSlug("home");

    // Three child-collection tiles: content 101/102/103 -> collections 201/202/203.
    List<CollectionContentEntity> joins = List.of(join(101L, 0), join(102L, 1), join(103L, 2));
    List<ContentEntity> tiles = List.of(tile(101L, 201L), tile(102L, 202L), tile(103L, 203L));
    when(contentRepository.findAllByIds(List.of(101L, 102L, 103L))).thenReturn(tiles);

    // Referenced collections, each with a cover image (301/302/303).
    CollectionEntity c201 =
        CollectionEntity.builder()
            .id(201L)
            .title("Film")
            .slug("film")
            .type(CollectionType.PORTFOLIO)
            .coverImageId(301L)
            .build();
    CollectionEntity c202 =
        CollectionEntity.builder()
            .id(202L)
            .title("Adventure")
            .slug("adventure")
            .type(CollectionType.PORTFOLIO)
            .coverImageId(302L)
            .build();
    CollectionEntity c203 =
        CollectionEntity.builder()
            .id(203L)
            .title("Travel")
            .slug("travel")
            .type(CollectionType.PORTFOLIO)
            .coverImageId(303L)
            .build();
    lenient()
        .when(collectionRepository.findByIds(List.of(201L, 202L, 203L)))
        .thenReturn(List.of(c201, c202, c203));

    ContentImageEntity cover301 = ContentImageEntity.builder().id(301L).build();
    ContentImageEntity cover302 = ContentImageEntity.builder().id(302L).build();
    ContentImageEntity cover303 = ContentImageEntity.builder().id(303L).build();
    lenient()
        .when(contentRepository.findImagesByIds(List.of(301L, 302L, 303L)))
        .thenReturn(List.of(cover301, cover302, cover303));

    // Metadata batch loads must never be per-tile; return empty regardless of the id list.
    lenient().when(tagRepository.findTagsByContentIds(anyList())).thenReturn(Map.of());
    lenient().when(personRepository.findPeopleByContentIds(anyList())).thenReturn(Map.of());
    lenient().when(locationRepository.findLocationsByContentIds(anyList())).thenReturn(Map.of());

    CollectionModel model = util.convertToModel(home, joins, 0, 30, 3);

    // Correctness: three tiles, titles + cover images resolved from the referenced collections.
    assertThat(model.getContent()).hasSize(3);
    ContentModel first = model.getContent().get(0);
    assertThat(first).isInstanceOf(ContentModels.Collection.class);
    ContentModels.Collection firstTile = (ContentModels.Collection) first;
    assertThat(firstTile.title()).isEqualTo("Film");
    assertThat(firstTile.referencedCollectionId()).isEqualTo(201L);
    assertThat(firstTile.coverImage()).isNotNull();
    assertThat(firstTile.coverImage().id()).isEqualTo(301L);

    // The whole point: no per-tile lookups.
    verify(collectionRepository, never()).findById(anyLong());
    verify(contentRepository, never()).findImageById(anyLong());

    // Batch lookups instead: one referenced-collection load, one cover-image load.
    verify(collectionRepository).findByIds(List.of(201L, 202L, 203L));
    verify(contentRepository).findImagesByIds(List.of(301L, 302L, 303L));
  }
}
