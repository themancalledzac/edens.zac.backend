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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

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
    ContentPersonEntity saved = ContentPersonEntity.builder().id(10L).personName("Charlie").build();
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
    contentMutationUtil.handleContentChildCollectionUpdates(1L, null);
    verifyNoInteractions(collectionRepository);
  }

  @Test
  void handleContentChildCollectionUpdates_updatesOrderIndex() {
    CollectionContentEntity joinEntry = CollectionContentEntity.builder().id(10L).build();
    when(collectionRepository.findContentByCollectionIdAndContentId(5L, 1L))
        .thenReturn(Optional.of(joinEntry));

    Records.ChildCollection update = new Records.ChildCollection(5L, null, null, null, null, 3);
    contentMutationUtil.handleContentChildCollectionUpdates(1L, List.of(update));

    verify(collectionRepository).updateContentOrderIndex(10L, 3);
  }

  @Test
  void handleContentChildCollectionUpdates_updatesVisibility() {
    CollectionContentEntity joinEntry = CollectionContentEntity.builder().id(10L).build();
    when(collectionRepository.findContentByCollectionIdAndContentId(5L, 1L))
        .thenReturn(Optional.of(joinEntry));

    Records.ChildCollection update = new Records.ChildCollection(5L, null, null, null, false, null);
    contentMutationUtil.handleContentChildCollectionUpdates(1L, List.of(update));

    verify(collectionRepository).updateContentVisible(10L, false);
  }

  @Test
  void handleContentChildCollectionUpdates_processesMultipleCollections() {
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
    contentMutationUtil.handleContentChildCollectionUpdates(1L, updates);

    verify(collectionRepository).updateContentOrderIndex(10L, 1);
    verify(collectionRepository).updateContentOrderIndex(11L, 2);
  }

  // =============================================================================
  // handleAddToCollections tests
  // =============================================================================

  @Test
  void handleAddToCollections_addsImageToCollection() {
    CollectionEntity collection = CollectionEntity.builder().id(5L).title("Test").build();
    when(collectionRepository.findById(5L)).thenReturn(Optional.of(collection));
    when(collectionRepository.findContentByCollectionIdAndContentId(5L, 1L))
        .thenReturn(Optional.empty());
    when(collectionRepository.getNextOrderIndexForCollection(5L)).thenReturn(0);

    Records.ChildCollection childCollection =
        new Records.ChildCollection(5L, null, null, null, true, null);
    contentMutationUtil.handleAddToCollections(1L, List.of(childCollection));

    verify(collectionRepository).saveContent(any(CollectionContentEntity.class));
  }

  @Test
  void handleAddToCollections_skipsDuplicate() {
    CollectionEntity collection = CollectionEntity.builder().id(5L).title("Test").build();
    when(collectionRepository.findById(5L)).thenReturn(Optional.of(collection));
    CollectionContentEntity existing = CollectionContentEntity.builder().id(10L).build();
    when(collectionRepository.findContentByCollectionIdAndContentId(5L, 1L))
        .thenReturn(Optional.of(existing));

    Records.ChildCollection childCollection =
        new Records.ChildCollection(5L, null, null, null, true, null);
    contentMutationUtil.handleAddToCollections(1L, List.of(childCollection));

    verify(collectionRepository, never()).saveContent(any());
  }

  @Test
  void handleAddToCollections_collectionNotFound_throws() {
    when(collectionRepository.findById(99L)).thenReturn(Optional.empty());

    Records.ChildCollection childCollection =
        new Records.ChildCollection(99L, null, null, null, true, null);
    assertThrows(
        ResourceNotFoundException.class,
        () -> contentMutationUtil.handleAddToCollections(1L, List.of(childCollection)));
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
    ContentPersonEntity created = ContentPersonEntity.builder().id(1L).personName("Alice").build();
    when(personRepository.findByPersonNameIgnoreCase("Alice")).thenReturn(Optional.empty());
    when(personRepository.save(any(ContentPersonEntity.class))).thenReturn(created);

    contentMutationUtil.associateExtractedKeywords(1L, null, List.of("Alice"));

    verify(contentRepository).saveContentPeople(eq(1L), anyList());
  }

  @Test
  void associateExtractedKeywords_mergesNewTagsWithExisting_keepsTagsMissingFromExport() {
    // Existing tags 10, 20 + new export's 30 -> additive merge keeps all three, drops none.
    TagEntity sunset = TagEntity.builder().id(30L).tagName("sunset").slug("sunset").build();
    when(tagRepository.findBySlug("sunset")).thenReturn(Optional.of(sunset));
    when(tagRepository.findTagIdsByContentIds(List.of(1L)))
        .thenReturn(Map.of(1L, List.of(10L, 20L)));

    contentMutationUtil.associateExtractedKeywords(1L, List.of("sunset"), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
    verify(tagRepository).saveContentTags(eq(1L), captor.capture());
    assertEquals(Set.of(10L, 20L, 30L), new HashSet<>(captor.getValue()));
  }

  @Test
  void associateExtractedKeywords_mergesNewPeopleWithExisting_keepsPeopleMissingFromExport() {
    ContentPersonEntity alice = ContentPersonEntity.builder().id(30L).personName("Alice").build();
    when(personRepository.findByPersonNameIgnoreCase("Alice")).thenReturn(Optional.of(alice));
    when(contentRepository.findPersonIdsByImageIds(List.of(1L)))
        .thenReturn(Map.of(1L, List.of(10L, 20L)));

    contentMutationUtil.associateExtractedKeywords(1L, null, List.of("Alice"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
    verify(contentRepository).saveContentPeople(eq(1L), captor.capture());
    assertEquals(Set.of(10L, 20L, 30L), new HashSet<>(captor.getValue()));
  }

  /**
   * Regression: a duplicate-name person made findByPersonNameIgnoreCase (a queryForObject) throw
   * IncorrectResultSizeDataAccessException, which was caught and logged as a WARN. The Lightroom
   * export then reported success with the person tag silently missing. V53 removes the duplicates;
   * this asserts the failure is reported rather than swallowed if it ever recurs.
   */
  @Test
  void associateExtractedKeywords_personLookupFails_returnsFailureInsteadOfSwallowing() {
    when(personRepository.findByPersonNameIgnoreCase("Tara Edens"))
        .thenThrow(new IncorrectResultSizeDataAccessException(1, 2));

    List<String> failures =
        contentMutationUtil.associateExtractedKeywords(1L, null, List.of("Tara Edens"));

    assertEquals(1, failures.size());
    assertTrue(failures.get(0).contains("people"), failures.get(0));
    verify(contentRepository, never()).saveContentPeople(anyLong(), anyList());
  }

  /** A failing person lookup must not take the image's tags down with it. */
  @Test
  void associateExtractedKeywords_personLookupFails_tagsStillAssociated() {
    TagEntity rome = TagEntity.builder().id(76L).tagName("Rome Italy").slug("rome-italy").build();
    when(tagRepository.findBySlug("rome-italy")).thenReturn(Optional.of(rome));
    when(personRepository.findByPersonNameIgnoreCase("Tara Edens"))
        .thenThrow(new IncorrectResultSizeDataAccessException(1, 2));

    List<String> failures =
        contentMutationUtil.associateExtractedKeywords(
            1L, List.of("Rome Italy"), List.of("Tara Edens"));

    verify(tagRepository).saveContentTags(eq(1L), anyList());
    assertEquals(1, failures.size());
  }

  /** The inverse: a failing tag association must not skip the people block. */
  @Test
  void associateExtractedKeywords_tagFailure_peopleStillAssociated() {
    when(tagRepository.findBySlug("rome-italy")).thenThrow(new RuntimeException("tag boom"));
    ContentPersonEntity tara =
        ContentPersonEntity.builder().id(107L).personName("Tara Edens").build();
    when(personRepository.findByPersonNameIgnoreCase("Tara Edens")).thenReturn(Optional.of(tara));

    List<String> failures =
        contentMutationUtil.associateExtractedKeywords(
            1L, List.of("Rome Italy"), List.of("Tara Edens"));

    verify(contentRepository).saveContentPeople(eq(1L), anyList());
    assertEquals(1, failures.size());
    assertTrue(failures.get(0).contains("tags"), failures.get(0));
  }

  @Test
  void associateExtractedKeywords_success_returnsNoFailures() {
    ContentPersonEntity tara =
        ContentPersonEntity.builder().id(107L).personName("Tara Edens").build();
    when(personRepository.findByPersonNameIgnoreCase("Tara Edens")).thenReturn(Optional.of(tara));

    assertTrue(
        contentMutationUtil.associateExtractedKeywords(1L, null, List.of("Tara Edens")).isEmpty());
  }

  // =============================================================================
  // updateImageTagsOptimized tests
  // =============================================================================

  @Test
  void updateImageTagsOptimized_appliesUpdateAndSaves() {
    TagEntity existing = TagEntity.builder().id(1L).tagName("nature").build();
    List<TagEntity> currentTags = List.of(existing);

    CollectionRequests.TagUpdate update = new CollectionRequests.TagUpdate(null, null, List.of(1L));
    Set<TagEntity> newTags = new HashSet<>();
    ContentImageEntity image = ContentImageEntity.builder().id(1L).build();

    contentMutationUtil.updateImageTagsOptimized(image, update, currentTags, newTags);

    assertTrue(image.getTags().isEmpty());
    verify(tagRepository).saveContentTags(eq(1L), anyList());
  }

  @Test
  void updateImageLocationsOptimized_appliesUpdateAndSaves() {
    LocationEntity existing = LocationEntity.builder().id(1L).locationName("NYC").build();
    List<LocationEntity> currentLocations = List.of(existing);

    CollectionRequests.LocationUpdate update =
        new CollectionRequests.LocationUpdate(null, null, List.of(1L));
    Set<LocationEntity> newLocs = new HashSet<>();
    ContentImageEntity image = ContentImageEntity.builder().id(1L).build();

    contentMutationUtil.updateImageLocationsOptimized(image, update, currentLocations, newLocs);

    assertTrue(image.getLocations().isEmpty());
    verify(locationRepository).saveContentLocations(eq(1L), anyList());
  }

  @Test
  @DisplayName("handleAddToCollections appends after the target's existing content")
  void handleAddToCollections_nullOrderIndex_appendsAfterMax() {
    // Scope: the orderIndex=null append path (max + 1). It cannot pin Rule B -- parent-ness is
    // derived from the collection_content join and collectionRepository is a mock here, so no
    // builder-built fixture is a wrapper. RuleBMixedContentIntegrationTest is the real pin.
    CollectionEntity target =
        CollectionEntity.builder().id(9L).slug("target").title("Target").build();
    when(collectionRepository.findById(9L)).thenReturn(Optional.of(target));
    when(collectionRepository.findContentByCollectionIdAndContentId(9L, 77L))
        .thenReturn(Optional.empty());
    when(collectionRepository.getNextOrderIndexForCollection(9L)).thenReturn(3);

    contentMutationUtil.handleAddToCollections(
        77L, List.of(new Records.ChildCollection(9L, null, null, null, true, null)));

    ArgumentCaptor<CollectionContentEntity> captor =
        ArgumentCaptor.forClass(CollectionContentEntity.class);
    verify(collectionRepository).saveContent(captor.capture());
    assertEquals(3, captor.getValue().getOrderIndex().intValue());
  }
}
