package edens.zac.portfolio.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.entity.LocationEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.CollectionType;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentMutationUtilTest {

  @Mock private ContentRepository contentRepository;
  @Mock private CollectionRepository collectionRepository;
  @Mock private TagRepository tagRepository;
  @Mock private PersonRepository personRepository;
  @Mock private LocationRepository locationRepository;

  @InjectMocks private ContentMutationUtil contentMutationUtil;

  // =============================================================================
  // updateTags tests
  // =============================================================================

  @Test
  void updateTags_nullUpdate_returnsCurrentTags() {
    Set<TagEntity> current = Set.of(new TagEntity("existing"));
    Set<TagEntity> result = contentMutationUtil.updateTags(current, null, null);
    assertEquals(current, result);
  }

  @Test
  void updateTags_removeById() {
    TagEntity tag1 = TagEntity.builder().id(1L).tagName("keep").build();
    TagEntity tag2 = TagEntity.builder().id(2L).tagName("remove-me").build();
    Set<TagEntity> current = new HashSet<>(Set.of(tag1, tag2));

    CollectionRequests.TagUpdate update = new CollectionRequests.TagUpdate(null, null, List.of(2L));
    Set<TagEntity> result = contentMutationUtil.updateTags(current, update, null);

    assertEquals(1, result.size());
    assertTrue(result.contains(tag1));
  }

  @Test
  void updateTags_addExistingById() {
    TagEntity existing = TagEntity.builder().id(5L).tagName("existing").build();
    when(tagRepository.findById(5L)).thenReturn(Optional.of(existing));

    CollectionRequests.TagUpdate update = new CollectionRequests.TagUpdate(List.of(5L), null, null);
    Set<TagEntity> result = contentMutationUtil.updateTags(new HashSet<>(), update, null);

    assertEquals(1, result.size());
    assertTrue(result.contains(existing));
  }

  @Test
  void updateTags_addExistingById_notFound_throws() {
    when(tagRepository.findById(99L)).thenReturn(Optional.empty());

    CollectionRequests.TagUpdate update =
        new CollectionRequests.TagUpdate(List.of(99L), null, null);
    assertThrows(
        IllegalArgumentException.class,
        () -> contentMutationUtil.updateTags(new HashSet<>(), update, null));
  }

  @Test
  void updateTags_createNew() {
    when(tagRepository.findByTagNameIgnoreCase("landscape")).thenReturn(Optional.empty());
    TagEntity saved = TagEntity.builder().id(10L).tagName("landscape").slug("landscape").build();
    when(tagRepository.save(any(TagEntity.class))).thenReturn(saved);

    Set<TagEntity> newTags = new HashSet<>();
    CollectionRequests.TagUpdate update =
        new CollectionRequests.TagUpdate(null, List.of("landscape"), null);
    Set<TagEntity> result = contentMutationUtil.updateTags(new HashSet<>(), update, newTags);

    assertEquals(1, result.size());
    assertEquals(1, newTags.size());
    assertTrue(newTags.contains(saved));
  }

  @Test
  void updateTags_createNew_existingFound() {
    TagEntity existing = TagEntity.builder().id(3L).tagName("Landscape").slug("landscape").build();
    when(tagRepository.findByTagNameIgnoreCase("landscape")).thenReturn(Optional.of(existing));

    Set<TagEntity> newTags = new HashSet<>();
    CollectionRequests.TagUpdate update =
        new CollectionRequests.TagUpdate(null, List.of("landscape"), null);
    Set<TagEntity> result = contentMutationUtil.updateTags(new HashSet<>(), update, newTags);

    assertEquals(1, result.size());
    assertTrue(newTags.isEmpty());
    verify(tagRepository, never()).save(any());
  }

  // =============================================================================
  // updatePeople tests
  // =============================================================================

  @Test
  void updatePeople_nullUpdate_returnsCurrentPeople() {
    Set<ContentPersonEntity> current = Set.of(new ContentPersonEntity("Alice"));
    Set<ContentPersonEntity> result = contentMutationUtil.updatePeople(current, null, null);
    assertEquals(current, result);
  }

  @Test
  void updatePeople_removeById() {
    ContentPersonEntity person1 = ContentPersonEntity.builder().id(1L).personName("Alice").build();
    ContentPersonEntity person2 = ContentPersonEntity.builder().id(2L).personName("Bob").build();
    Set<ContentPersonEntity> current = new HashSet<>(Set.of(person1, person2));

    CollectionRequests.PersonUpdate update =
        new CollectionRequests.PersonUpdate(null, null, List.of(2L));
    Set<ContentPersonEntity> result = contentMutationUtil.updatePeople(current, update, null);

    assertEquals(1, result.size());
    assertTrue(result.contains(person1));
  }

  @Test
  void updatePeople_createNew() {
    when(personRepository.findByPersonNameIgnoreCase("Charlie")).thenReturn(Optional.empty());
    ContentPersonEntity saved =
        ContentPersonEntity.builder().id(10L).personName("Charlie").slug("charlie").build();
    when(personRepository.save(any(ContentPersonEntity.class))).thenReturn(saved);

    Set<ContentPersonEntity> newPeople = new HashSet<>();
    CollectionRequests.PersonUpdate update =
        new CollectionRequests.PersonUpdate(null, List.of("Charlie"), null);
    Set<ContentPersonEntity> result =
        contentMutationUtil.updatePeople(new HashSet<>(), update, newPeople);

    assertEquals(1, result.size());
    assertTrue(newPeople.contains(saved));
  }

  // =============================================================================
  // updateLocations tests
  // =============================================================================

  @Test
  void updateLocations_nullUpdate_returnsCurrentLocations() {
    LocationEntity loc = LocationEntity.builder().id(1L).locationName("NYC").build();
    Set<LocationEntity> current = Set.of(loc);
    Set<LocationEntity> result = contentMutationUtil.updateLocations(current, null, null);
    assertEquals(current, result);
  }

  @Test
  void updateLocations_removeById() {
    LocationEntity loc1 = LocationEntity.builder().id(1L).locationName("NYC").build();
    LocationEntity loc2 = LocationEntity.builder().id(2L).locationName("LA").build();
    Set<LocationEntity> current = new HashSet<>(Set.of(loc1, loc2));

    CollectionRequests.LocationUpdate update =
        new CollectionRequests.LocationUpdate(null, null, List.of(2L));
    Set<LocationEntity> result = contentMutationUtil.updateLocations(current, update, null);

    assertEquals(1, result.size());
    assertTrue(result.contains(loc1));
  }

  @Test
  void updateLocations_addExistingById() {
    LocationEntity existing = LocationEntity.builder().id(5L).locationName("Paris").build();
    when(locationRepository.findById(5L)).thenReturn(Optional.of(existing));

    CollectionRequests.LocationUpdate update =
        new CollectionRequests.LocationUpdate(List.of(5L), null, null);
    Set<LocationEntity> result = contentMutationUtil.updateLocations(new HashSet<>(), update, null);

    assertEquals(1, result.size());
    assertTrue(result.contains(existing));
  }

  @Test
  void updateLocations_createNew() {
    LocationEntity created = LocationEntity.builder().id(10L).locationName("Tokyo").build();
    when(locationRepository.findOrCreate("Tokyo")).thenReturn(created);

    Set<LocationEntity> newLocs = new HashSet<>();
    CollectionRequests.LocationUpdate update =
        new CollectionRequests.LocationUpdate(null, List.of("Tokyo"), null);
    Set<LocationEntity> result =
        contentMutationUtil.updateLocations(new HashSet<>(), update, newLocs);

    assertEquals(1, result.size());
    assertTrue(newLocs.contains(created));
  }

  // =============================================================================
  // handleContentChildCollectionUpdates tests
  // =============================================================================

  @Test
  void handleContentChildCollectionUpdates_nullList_noOp() {
    ContentImageEntity image = ContentImageEntity.builder().id(1L).build();
    contentMutationUtil.handleContentChildCollectionUpdates(image, null);
    verifyNoInteractions(collectionRepository);
  }

  @Test
  void handleContentChildCollectionUpdates_updatesOrderIndex() {
    ContentImageEntity image = ContentImageEntity.builder().id(1L).build();
    CollectionContentEntity joinEntry = CollectionContentEntity.builder().id(10L).build();
    when(collectionRepository.findContentByCollectionIdAndContentId(5L, 1L))
        .thenReturn(Optional.of(joinEntry));

    Records.ChildCollection update = new Records.ChildCollection(5L, null, null, null, null, 3);
    contentMutationUtil.handleContentChildCollectionUpdates(image, List.of(update));

    verify(collectionRepository).updateContentOrderIndex(10L, 3);
  }

  @Test
  void handleContentChildCollectionUpdates_updatesVisibility() {
    ContentImageEntity image = ContentImageEntity.builder().id(1L).build();
    CollectionContentEntity joinEntry = CollectionContentEntity.builder().id(10L).build();
    when(collectionRepository.findContentByCollectionIdAndContentId(5L, 1L))
        .thenReturn(Optional.of(joinEntry));

    Records.ChildCollection update = new Records.ChildCollection(5L, null, null, null, false, null);
    contentMutationUtil.handleContentChildCollectionUpdates(image, List.of(update));

    verify(collectionRepository).updateContentVisible(10L, false);
  }

  @Test
  void handleContentChildCollectionUpdates_processesMultipleCollections() {
    ContentImageEntity image = ContentImageEntity.builder().id(1L).build();
    CollectionContentEntity join1 = CollectionContentEntity.builder().id(10L).build();
    CollectionContentEntity join2 = CollectionContentEntity.builder().id(11L).build();
    when(collectionRepository.findContentByCollectionIdAndContentId(5L, 1L))
        .thenReturn(Optional.of(join1));
    when(collectionRepository.findContentByCollectionIdAndContentId(6L, 1L))
        .thenReturn(Optional.of(join2));

    List<Records.ChildCollection> updates =
        List.of(
            new Records.ChildCollection(5L, null, null, null, null, 1),
            new Records.ChildCollection(6L, null, null, null, null, 2));
    contentMutationUtil.handleContentChildCollectionUpdates(image, updates);

    verify(collectionRepository).updateContentOrderIndex(10L, 1);
    verify(collectionRepository).updateContentOrderIndex(11L, 2);
  }

  // =============================================================================
  // handleAddToCollections tests
  // =============================================================================

  @Test
  void handleAddToCollections_addsImageToCollection() {
    ContentImageEntity image = ContentImageEntity.builder().id(1L).build();
    CollectionEntity collection =
        CollectionEntity.builder().id(5L).title("Test").type(CollectionType.BLOG).build();
    when(collectionRepository.findById(5L)).thenReturn(Optional.of(collection));
    when(collectionRepository.findContentByCollectionIdAndContentId(5L, 1L))
        .thenReturn(Optional.empty());
    when(collectionRepository.getMaxOrderIndexForCollection(5L)).thenReturn(null);

    Records.ChildCollection childCollection =
        new Records.ChildCollection(5L, null, null, null, true, null);
    contentMutationUtil.handleAddToCollections(image, List.of(childCollection));

    verify(collectionRepository).saveContent(any(CollectionContentEntity.class));
  }

  @Test
  void handleAddToCollections_rejectsParentTypeCollection() {
    ContentImageEntity image = ContentImageEntity.builder().id(1L).build();
    CollectionEntity collection =
        CollectionEntity.builder().id(5L).title("Parent").type(CollectionType.PARENT).build();
    when(collectionRepository.findById(5L)).thenReturn(Optional.of(collection));

    Records.ChildCollection childCollection =
        new Records.ChildCollection(5L, null, null, null, true, null);
    assertThrows(
        IllegalArgumentException.class,
        () -> contentMutationUtil.handleAddToCollections(image, List.of(childCollection)));
  }

  @Test
  void handleAddToCollections_skipsDuplicate() {
    ContentImageEntity image = ContentImageEntity.builder().id(1L).build();
    CollectionEntity collection =
        CollectionEntity.builder().id(5L).title("Test").type(CollectionType.BLOG).build();
    when(collectionRepository.findById(5L)).thenReturn(Optional.of(collection));
    CollectionContentEntity existing = CollectionContentEntity.builder().id(10L).build();
    when(collectionRepository.findContentByCollectionIdAndContentId(5L, 1L))
        .thenReturn(Optional.of(existing));

    Records.ChildCollection childCollection =
        new Records.ChildCollection(5L, null, null, null, true, null);
    contentMutationUtil.handleAddToCollections(image, List.of(childCollection));

    verify(collectionRepository, never()).saveContent(any());
  }

  @Test
  void handleAddToCollections_collectionNotFound_throws() {
    ContentImageEntity image = ContentImageEntity.builder().id(1L).build();
    when(collectionRepository.findById(99L)).thenReturn(Optional.empty());

    Records.ChildCollection childCollection =
        new Records.ChildCollection(99L, null, null, null, true, null);
    assertThrows(
        ResourceNotFoundException.class,
        () -> contentMutationUtil.handleAddToCollections(image, List.of(childCollection)));
  }

  // =============================================================================
  // associateExtractedKeywords tests
  // =============================================================================

  @Test
  void associateExtractedKeywords_nullInputs_noOp() {
    contentMutationUtil.associateExtractedKeywords(1L, null, null);
    verifyNoInteractions(tagRepository, personRepository);
  }

  @Test
  void associateExtractedKeywords_emptyInputs_noOp() {
    contentMutationUtil.associateExtractedKeywords(1L, List.of(), List.of());
    verifyNoInteractions(tagRepository, personRepository);
  }

  @Test
  void associateExtractedKeywords_createsTags() {
    TagEntity existing = TagEntity.builder().id(1L).tagName("nature").slug("nature").build();
    when(tagRepository.findBySlug("nature")).thenReturn(Optional.of(existing));

    TagEntity created = TagEntity.builder().id(2L).tagName("sunset").slug("sunset").build();
    when(tagRepository.findBySlug("sunset")).thenReturn(Optional.empty());
    when(tagRepository.save(any(TagEntity.class))).thenReturn(created);

    contentMutationUtil.associateExtractedKeywords(1L, List.of("nature", "sunset"), null);

    verify(tagRepository).saveContentTags(eq(1L), anyList());
  }

  @Test
  void associateExtractedKeywords_deduplicatesTags() {
    TagEntity tag = TagEntity.builder().id(1L).tagName("nature").slug("nature").build();
    when(tagRepository.findBySlug("nature")).thenReturn(Optional.of(tag));

    contentMutationUtil.associateExtractedKeywords(1L, List.of("nature", "Nature"), null);

    // findBySlug called only once due to dedup
    verify(tagRepository, times(1)).findBySlug("nature");
  }

  @Test
  void associateExtractedKeywords_createsPeople() {
    ContentPersonEntity created =
        ContentPersonEntity.builder().id(1L).personName("Alice").slug("alice").build();
    when(personRepository.findBySlug("alice")).thenReturn(Optional.empty());
    when(personRepository.save(any(ContentPersonEntity.class))).thenReturn(created);

    contentMutationUtil.associateExtractedKeywords(1L, null, List.of("Alice"));

    verify(contentRepository).saveImagePeople(eq(1L), anyList());
  }

  // =============================================================================
  // updateImageTagsOptimized tests
  // =============================================================================

  @Test
  void updateImageTagsOptimized_appliesUpdateAndSaves() {
    ContentImageEntity image = ContentImageEntity.builder().id(1L).build();
    TagEntity existing = TagEntity.builder().id(1L).tagName("nature").build();
    List<TagEntity> currentTags = List.of(existing);

    CollectionRequests.TagUpdate update = new CollectionRequests.TagUpdate(null, null, List.of(1L));
    Set<TagEntity> newTags = new HashSet<>();

    contentMutationUtil.updateImageTagsOptimized(image, update, currentTags, newTags);

    assertTrue(image.getTags().isEmpty());
    verify(tagRepository).saveContentTags(eq(1L), anyList());
  }

  @Test
  void updateImageLocationsOptimized_appliesUpdateAndSaves() {
    ContentImageEntity image = ContentImageEntity.builder().id(1L).build();
    LocationEntity existing = LocationEntity.builder().id(1L).locationName("NYC").build();
    List<LocationEntity> currentLocations = List.of(existing);

    CollectionRequests.LocationUpdate update =
        new CollectionRequests.LocationUpdate(null, null, List.of(1L));
    Set<LocationEntity> newLocs = new HashSet<>();

    contentMutationUtil.updateImageLocationsOptimized(image, update, currentLocations, newLocs);

    assertTrue(image.getLocations().isEmpty());
    verify(locationRepository).saveImageLocations(eq(1L), anyList());
  }
}
