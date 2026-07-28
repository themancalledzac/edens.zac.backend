package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import edens.zac.portfolio.backend.dao.CollectionPeopleRepository;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.CollectionSiblingRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.ContentTextEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import edens.zac.portfolio.backend.types.ContentType;
import edens.zac.portfolio.backend.types.DisplayMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CollectionProcessingUtilTest {

  @Mock private CollectionRepository collectionRepository;

  @Mock private CollectionPeopleRepository collectionPeopleRepository;

  @Mock private ContentRepository contentRepository;

  @Mock private ContentModelConverter contentModelConverter;

  @Mock private LocationRepository locationRepository;

  @Mock private TagRepository tagRepository;

  @Mock private PersonRepository personRepository;

  @Mock private CollectionSiblingRepository collectionSiblingRepository;

  @InjectMocks private CollectionProcessingUtil util;

  private CollectionEntity testEntity;
  private List<ContentEntity> testBlocks;

  @BeforeEach
  void setUp() {
    // Create test entity
    testEntity = new CollectionEntity();
    testEntity.setId(1L);
    testEntity.setType(CollectionType.BLOG);
    testEntity.setTitle("Test Blog");
    testEntity.setSlug("test-blog");
    testEntity.setDescription("Test description");
    testEntity.setVisibility(CollectionVisibility.LISTED);
    testEntity.setContentPerPage(30);
    testEntity.setTotalContent(2);
    testEntity.setCreatedAt(LocalDateTime.now());
    testEntity.setUpdatedAt(LocalDateTime.now());

    // Create test content blocks
    testBlocks = new ArrayList<>();
    ContentTextEntity block1 = new ContentTextEntity();
    block1.setId(1L);
    block1.setContentType(ContentType.TEXT);
    block1.setTextContent("Test content 1");

    ContentTextEntity block2 = new ContentTextEntity();
    block2.setId(2L);
    block2.setContentType(ContentType.TEXT);
    block2.setTextContent("Test content 2");

    testBlocks.add(block1);
    testBlocks.add(block2);

    // Note: CollectionContentEntity now uses IDs instead of entity references
    // These are not used in the tests below, but kept for reference
  }

  @Test
  void convertToBasicModel_shouldConvertEntityToModel() {
    // Arrange
    //        when(homeCardRepository.findByReferenceId(any())).thenReturn(Optional.empty());

    // Act
    CollectionModel model = util.convertToBasicModel(testEntity);

    // Assert
    assertNotNull(model);
    assertEquals(testEntity.getId(), model.getId());
    assertEquals(testEntity.getType(), model.getType());
    assertEquals(testEntity.getTitle(), model.getTitle());
    assertEquals(testEntity.getSlug(), model.getSlug());
    assertEquals(testEntity.getDescription(), model.getDescription());
    assertEquals(testEntity.getVisibility(), model.getVisibility());
    assertEquals(testEntity.getContentPerPage(), model.getContentPerPage());
    assertEquals(testEntity.getTotalContent(), model.getContentCount());
    assertEquals(testEntity.getTotalPages(), model.getTotalPages());
    assertEquals(0, model.getCurrentPage());
  }

  @Test
  void validateAndEnsureUniqueSlug_shouldReturnOriginalSlugWhenUnique() {
    // Arrange
    when(collectionRepository.findBySlug("test-slug")).thenReturn(Optional.empty());

    // Act
    String result = util.validateAndEnsureUniqueSlug("test-slug", null);

    // Assert
    assertEquals("test-slug", result);
  }

  @Test
  void validateAndEnsureUniqueSlug_shouldAppendNumberWhenSlugExists() {
    // Arrange
    CollectionEntity existingEntity = new CollectionEntity();
    existingEntity.setId(2L);

    when(collectionRepository.findBySlug("test-slug")).thenReturn(Optional.of(existingEntity));
    when(collectionRepository.findBySlug("test-slug-1")).thenReturn(Optional.empty());

    // Act
    String result = util.validateAndEnsureUniqueSlug("test-slug", 1L);

    // Assert
    assertEquals("test-slug-1", result);
  }

  @Test
  void applyPaginationDefaults_setsPaginationOnly_leavesVisibilityUntouched() {
    // Arrange
    CollectionEntity entity = new CollectionEntity();
    entity.setType(CollectionType.CLIENT_GALLERY);
    entity.setVisibility(CollectionVisibility.HIDDEN);

    // Act
    CollectionEntity result = util.applyPaginationDefaults(entity);

    // Assert
    assertEquals(30, result.getContentPerPage());
    // Visibility is no longer touched here: the create-path default (UNLISTED) lives in
    // toEntity, and this method must not flip an entity's visibility.
    assertEquals(CollectionVisibility.HIDDEN, result.getVisibility());
  }

  @Test
  void toEntity_unlistedDefaultAppliesToAllTypes() {
    // UNLISTED is the universal create default -- no type is exempt.
    when(collectionRepository.findBySlug(anyString())).thenReturn(Optional.empty());

    for (CollectionType type : CollectionType.values()) {
      CollectionRequests.Create request =
          new CollectionRequests.Create(type, "Typed " + type.name());

      CollectionEntity entity = util.toEntity(request, 30);

      assertEquals(
          CollectionVisibility.UNLISTED,
          entity.getVisibility(),
          "Create default for type " + type + " must be UNLISTED");
    }
  }

  @Test
  void populateSiblings_listedOnlyTrue_resolvesCoverImageUrlInBatch() {
    CollectionModel model = CollectionModel.builder().id(5L).build();
    // Sibling 9 has cover image 100; sibling 11 has none.
    List<Records.SiblingRow> rows =
        List.of(
            new Records.SiblingRow(
                9L,
                "Dolomites Film",
                "dolomites-film",
                CollectionType.PORTFOLIO,
                100L,
                false,
                false),
            new Records.SiblingRow(
                11L, "Alps Digital", "alps-digital", CollectionType.PORTFOLIO, null, false, false));
    when(collectionSiblingRepository.findSiblings(5L, true)).thenReturn(rows);

    ContentImageEntity cover =
        ContentImageEntity.builder()
            .id(100L)
            .imageUrlWeb("https://cdn.example.com/dolomites-film-cover.jpg")
            .build();
    when(contentRepository.findImagesByIds(List.of(100L))).thenReturn(List.of(cover));

    util.populateSiblings(model, true);

    assertThat(model.getSiblings()).hasSize(2);
    Records.CollectionList withCover = model.getSiblings().get(0);
    assertThat(withCover.id()).isEqualTo(9L);
    assertThat(withCover.name()).isEqualTo("Dolomites Film");
    assertThat(withCover.slug()).isEqualTo("dolomites-film");
    assertThat(withCover.type()).isEqualTo(CollectionType.PORTFOLIO);
    assertThat(withCover.coverImageUrl())
        .isEqualTo("https://cdn.example.com/dolomites-film-cover.jpg");

    Records.CollectionList withoutCover = model.getSiblings().get(1);
    assertThat(withoutCover.id()).isEqualTo(11L);
    assertThat(withoutCover.coverImageUrl()).isNull();

    verify(collectionSiblingRepository).findSiblings(5L, true);
    verify(contentRepository).findImagesByIds(List.of(100L));
  }

  @Test
  void populateSiblings_noCoverImages_skipsImageLookup() {
    CollectionModel model = CollectionModel.builder().id(5L).build();
    List<Records.SiblingRow> rows =
        List.of(
            new Records.SiblingRow(
                11L, "Alps Digital", "alps-digital", CollectionType.PORTFOLIO, null, false, false));
    when(collectionSiblingRepository.findSiblings(5L, false)).thenReturn(rows);

    util.populateSiblings(model, false);

    assertThat(model.getSiblings()).hasSize(1);
    assertThat(model.getSiblings().get(0).coverImageUrl()).isNull();
    // No cover image ids -> no batch image lookup at all.
    verify(contentRepository, never()).findImagesByIds(anyList());
  }

  @Test
  void populateSiblings_nullModel_isNoOp() {
    util.populateSiblings(null, true);
    verifyNoInteractions(collectionSiblingRepository);
  }

  @Test
  void populateSiblings_nullId_isNoOp() {
    util.populateSiblings(CollectionModel.builder().build(), false);
    verifyNoInteractions(collectionSiblingRepository);
  }

  /**
   * Build an Update that only carries date-range fields (everything else null). Canonical 23-arg
   * order: id, type, isClient, isBlog, title, slug, description, locations, collectionDate,
   * collectionEndDate, clearCollectionDate, clearCollectionEndDate, visibility, rating,
   * displayMode, contentPerPage, rowsWide, coverImageId, tags, people, collections, siblings,
   * parents.
   */
  private static CollectionRequests.Update dateRangeUpdate(
      LocalDate collectionDate,
      LocalDate collectionEndDate,
      Boolean clearCollectionDate,
      Boolean clearCollectionEndDate) {
    return new CollectionRequests.Update(
        1L,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        collectionDate,
        collectionEndDate,
        clearCollectionDate,
        clearCollectionEndDate,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  @Test
  void applyBasicUpdates_setsCollectionEndDate_whenValidRange() {
    testEntity.setCollectionDate(LocalDate.of(2026, 3, 5));

    util.applyBasicUpdates(
        testEntity,
        dateRangeUpdate(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 7), null, null));

    assertEquals(LocalDate.of(2026, 3, 7), testEntity.getCollectionEndDate());
    assertEquals(LocalDate.of(2026, 3, 5), testEntity.getCollectionDate());
  }

  @Test
  void applyBasicUpdates_rejectsEndBeforeStart() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                util.applyBasicUpdates(
                    testEntity,
                    dateRangeUpdate(
                        LocalDate.of(2026, 3, 7), LocalDate.of(2026, 3, 5), null, null)));
    assertTrue(ex.getMessage().contains("collectionEndDate"));
  }

  @Test
  void applyBasicUpdates_rejectsEndDateWithoutStartDate() {
    // Entity has no start date, and none is supplied — only an end date is.
    testEntity.setCollectionDate(null);

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                util.applyBasicUpdates(
                    testEntity, dateRangeUpdate(null, LocalDate.of(2026, 3, 7), null, null)));
    assertTrue(ex.getMessage().contains("requires a collectionDate"));
  }

  @Test
  void applyBasicUpdates_endDateValidAgainstExistingStartDate() {
    // Start date already persisted on the entity; the update supplies only the end date.
    testEntity.setCollectionDate(LocalDate.of(2026, 3, 1));

    util.applyBasicUpdates(testEntity, dateRangeUpdate(null, LocalDate.of(2026, 3, 9), null, null));

    assertEquals(LocalDate.of(2026, 3, 9), testEntity.getCollectionEndDate());
  }

  @Test
  void applyBasicUpdates_clearCollectionEndDate_setsNull() {
    testEntity.setCollectionDate(LocalDate.of(2026, 3, 1));
    testEntity.setCollectionEndDate(LocalDate.of(2026, 3, 9));

    util.applyBasicUpdates(testEntity, dateRangeUpdate(null, null, null, Boolean.TRUE));

    assertNull(testEntity.getCollectionEndDate());
  }

  @Test
  void convertToBasicModel_carriesCollectionEndDate() {
    testEntity.setCollectionDate(LocalDate.of(2026, 3, 5));
    testEntity.setCollectionEndDate(LocalDate.of(2026, 3, 7));

    CollectionModel model = util.convertToBasicModel(testEntity);

    assertEquals(LocalDate.of(2026, 3, 7), model.getCollectionEndDate());
  }

  // =============================================================================
  // Chronological default display mode
  // =============================================================================

  @Test
  void toEntity_defaultsDisplayModeToChronologicalForEveryType() {
    // Every create without an explicit displayMode lands on CHRONOLOGICAL regardless of type;
    // ORDERED is opt-in via a later update.
    when(collectionRepository.findBySlug(anyString())).thenReturn(Optional.empty());

    for (CollectionType type : CollectionType.values()) {
      CollectionRequests.Create request =
          new CollectionRequests.Create(type, "Typed " + type.name());

      CollectionEntity entity = util.toEntity(request, 30);

      assertEquals(
          DisplayMode.CHRONOLOGICAL,
          entity.getDisplayMode(),
          "Create default displayMode for type " + type + " must be CHRONOLOGICAL");
    }
  }

  @Test
  void applyBasicUpdates_explicitOrderedDisplayModeIsRespected() {
    testEntity.setDisplayMode(DisplayMode.CHRONOLOGICAL);

    util.applyBasicUpdates(
        testEntity,
        new CollectionRequests.Update(
            1L,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            DisplayMode.ORDERED,
            null,
            null,
            null,
            null,
            null,
            null,
            null));

    assertEquals(DisplayMode.ORDERED, testEntity.getDisplayMode());
  }

  @Test
  void convertToBasicModel_nullDisplayModeFallsBackToChronologicalRegardlessOfType() {
    testEntity.setType(CollectionType.PORTFOLIO);
    testEntity.setDisplayMode(null);

    CollectionModel model = util.convertToBasicModel(testEntity);

    assertEquals(DisplayMode.CHRONOLOGICAL, model.getDisplayMode());
  }

  @Test
  void convertToBasicModel_storedOrderedDisplayModeIsPreserved() {
    // Existing rows are NOT backfilled: a stored ORDERED mode must survive conversion.
    testEntity.setDisplayMode(DisplayMode.ORDERED);

    CollectionModel model = util.convertToBasicModel(testEntity);

    assertEquals(DisplayMode.ORDERED, model.getDisplayMode());
  }

  // =============================================================================
  // Dual-compat type/flag resolution on create and update
  // =============================================================================

  @Test
  void toEntity_isClientTrue_setsClientGalleryTypeAndFlags() {
    when(collectionRepository.findBySlug(anyString())).thenReturn(Optional.empty());
    CollectionRequests.Create request =
        new CollectionRequests.Create(null, "Boolean Gallery", null, null, null, null, true, false);

    CollectionEntity entity = util.toEntity(request, 30);

    assertEquals(CollectionType.CLIENT_GALLERY, entity.getType());
    assertTrue(entity.isClient());
    assertFalse(entity.isBlog());
  }

  @Test
  void toEntity_legacyBlogType_derivesIsBlog() {
    when(collectionRepository.findBySlug(anyString())).thenReturn(Optional.empty());
    CollectionRequests.Create request =
        new CollectionRequests.Create(CollectionType.BLOG, "Legacy Blog");

    CollectionEntity entity = util.toEntity(request, 30);

    assertEquals(CollectionType.BLOG, entity.getType());
    assertFalse(entity.isClient());
    assertTrue(entity.isBlog());
  }

  @Test
  void toEntity_neitherTypeNorBooleans_landsOnMisc() {
    when(collectionRepository.findBySlug(anyString())).thenReturn(Optional.empty());
    CollectionRequests.Create request = new CollectionRequests.Create(null, "Untyped Create");

    CollectionEntity entity = util.toEntity(request, 30);

    assertEquals(CollectionType.MISC, entity.getType());
    assertFalse(entity.isClient());
    assertFalse(entity.isBlog());
  }

  @Test
  void applyBasicUpdates_isBlogTrue_setsBlogTypeAndFlags() {
    testEntity.setType(CollectionType.MISC);

    util.applyBasicUpdates(testEntity, typeFlagsUpdate(null, false, true));

    assertEquals(CollectionType.BLOG, testEntity.getType());
    assertTrue(testEntity.isBlog());
    assertFalse(testEntity.isClient());
  }

  @Test
  void applyBasicUpdates_legacyClientGalleryType_derivesIsClient() {
    testEntity.setType(CollectionType.MISC);

    util.applyBasicUpdates(testEntity, typeFlagsUpdate(CollectionType.CLIENT_GALLERY, null, null));

    assertEquals(CollectionType.CLIENT_GALLERY, testEntity.getType());
    assertTrue(testEntity.isClient());
    assertFalse(testEntity.isBlog());
  }

  /**
   * Build an Update that carries only the type/flag triple (everything else null), so the flag
   * tests do not inline the wide canonical constructor at mixed arities.
   */
  private static CollectionRequests.Update typeFlagsUpdate(
      CollectionType type, Boolean isClient, Boolean isBlog) {
    return new CollectionRequests.Update(
        1L, type, isClient, isBlog, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null);
  }

  @Test
  void toEntity_bothFlagsTrue_isRejected() {
    // The both-true 400 was pinned on the update path only. All three create surfaces (JSON
    // create, child create, multipart create) funnel through toEntity, so this one unit test
    // covers them; a MockMvc variant would only re-test GlobalExceptionHandler's IAE -> 400.
    CollectionRequests.Create request =
        new CollectionRequests.Create(null, "Both Flags", null, null, null, null, true, true);

    assertThrows(IllegalArgumentException.class, () -> util.toEntity(request, 30));
  }

  @Test
  void applyBasicUpdates_partialIsBlogFalse_onClientGallery_doesNotDemote() {
    // Wiring-seam pin: applyBasicUpdates must hand the entity's CURRENT booleans to the compat
    // resolver, not just its legacy type column. Without that, {"isBlog": false} alone clears
    // is_client on a drifted row today, and demotes every client gallery once phase 2 nulls the
    // type column. Mutating entity.getType() to null must not change the outcome.
    testEntity.setType(CollectionType.CLIENT_GALLERY);
    testEntity.setClient(true);
    testEntity.setBlog(false);

    util.applyBasicUpdates(testEntity, typeFlagsUpdate(null, null, false));

    assertEquals(CollectionType.CLIENT_GALLERY, testEntity.getType());
    assertTrue(testEntity.isClient());
    assertFalse(testEntity.isBlog());
  }

  @Test
  void applyBasicUpdates_isClientDemotion_clearsGalleryAccess() {
    // updateGalleryAccess refuses non-CLIENT_GALLERY/PARENT targets and the read gate keys on
    // galleryPassword != null, so a demoted collection would otherwise keep an enforced password
    // that no endpoint can clear.
    testEntity.setType(CollectionType.CLIENT_GALLERY);
    testEntity.setClient(true);
    testEntity.setGalleryPassword("secret");
    testEntity.setRecipientEmails(new ArrayList<>(List.of("client@example.com")));

    util.applyBasicUpdates(testEntity, typeFlagsUpdate(null, false, null));

    assertEquals(CollectionType.MISC, testEntity.getType());
    assertFalse(testEntity.isClient());
    assertNull(testEntity.getGalleryPassword());
    assertTrue(testEntity.getRecipientEmails().isEmpty());
    verify(collectionRepository).saveGalleryAccess(1L, null, List.of());
  }

  @Test
  void applyBasicUpdates_bothFlagsTrue_isRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> util.applyBasicUpdates(testEntity, typeFlagsUpdate(null, true, true)));
  }

  @Test
  void applyBasicUpdates_noTypeOrFlagsInRequest_leavesTypeAndFlagsUntouched() {
    testEntity.setType(CollectionType.BLOG);
    testEntity.setBlog(true);

    // Description-only update: no type field and no booleans in the request.
    util.applyBasicUpdates(
        testEntity,
        new CollectionRequests.Update(
            1L,
            null,
            null,
            null,
            "New description",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null));

    assertEquals(CollectionType.BLOG, testEntity.getType());
    assertTrue(testEntity.isBlog());
  }

  @Test
  @DisplayName("toEntity always sets the default contentPerPage")
  void toEntity_alwaysSetsContentPerPage() {
    when(collectionRepository.findBySlug(anyString())).thenReturn(Optional.empty());
    CollectionRequests.Create request =
        new CollectionRequests.Create(CollectionType.PARENT, "Any Create");

    CollectionEntity entity = util.toEntity(request, 30);

    assertEquals(30, entity.getContentPerPage());
  }

  @Test
  @DisplayName("applyBasicUpdates accepts contentPerPage and rowsWide on any collection")
  void applyBasicUpdates_acceptsLayoutFieldsOnWrapper() {
    testEntity.setType(CollectionType.PARENT);

    util.applyBasicUpdates(testEntity, contentPerPageAndRowsWideUpdate(45, 6));

    assertEquals(45, testEntity.getContentPerPage());
    assertEquals(6, testEntity.getRowsWide());
  }

  @Test
  @DisplayName("applyPaginationDefaults fills contentPerPage on any collection")
  void applyPaginationDefaults_fillsOnAnyCollection() {
    CollectionEntity entity = new CollectionEntity();
    entity.setType(CollectionType.PARENT);
    entity.setContentPerPage(null);

    util.applyPaginationDefaults(entity);

    assertNotNull(entity.getContentPerPage());
  }

  private CollectionRequests.Update contentPerPageAndRowsWideUpdate(
      Integer contentPerPage, Integer rowsWide) {
    return new CollectionRequests.Update(
        1L,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        contentPerPage,
        rowsWide,
        null,
        null,
        null,
        null,
        null);
  }
}
