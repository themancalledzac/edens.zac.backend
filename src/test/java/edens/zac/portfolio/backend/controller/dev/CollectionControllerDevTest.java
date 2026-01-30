package edens.zac.portfolio.backend.controller.dev;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.model.CollectionCreateRequest;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionReorderRequest;
import edens.zac.portfolio.backend.model.CollectionUpdateRequest;
import edens.zac.portfolio.backend.model.CollectionUpdateResponseDTO;
import edens.zac.portfolio.backend.model.GeneralMetadataDTO;
import edens.zac.portfolio.backend.model.PersonUpdate;
import edens.zac.portfolio.backend.model.TagUpdate;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.types.CollectionType;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CollectionControllerDevTest {

  private MockMvc mockMvc;

  @Mock private CollectionService collectionService;

  @InjectMocks private CollectionControllerDev contentCollectionController;

  private ObjectMapper objectMapper;

  private CollectionModel testCollection;
  private CollectionUpdateResponseDTO testCollectionUpdateResponse;
  private CollectionCreateRequest testCreateRequest;
  private CollectionUpdateRequest testUpdateDTO;

  @BeforeEach
  void setUp() {
    // Initialize ObjectMapper
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();

    // Set up MockMvc
    mockMvc = MockMvcBuilders.standaloneSetup(contentCollectionController).build();

    // Create test collection model (for updateContent, addContents)
    testCollection = new CollectionModel();
    testCollection.setId(1L);
    testCollection.setType(CollectionType.BLOG);
    testCollection.setTitle("Test Blog");
    testCollection.setSlug("test-blog");
    testCollection.setDescription("A test blog collection");
    testCollection.setVisible(true);
    testCollection.setContentPerPage(30);
    testCollection.setContentCount(5);
    testCollection.setTotalPages(1);
    testCollection.setCurrentPage(0);
    testCollection.setCreatedAt(LocalDateTime.now());
    testCollection.setUpdatedAt(LocalDateTime.now());

    // Create test collection update response (for createCollection)
    GeneralMetadataDTO metadata =
        GeneralMetadataDTO.builder()
            .tags(new ArrayList<>())
            .people(new ArrayList<>())
            .collections(new ArrayList<>())
            .cameras(new ArrayList<>())
            .lenses(new ArrayList<>())
            .filmTypes(new ArrayList<>())
            .filmFormats(new ArrayList<>())
            .build();

    testCollectionUpdateResponse =
        CollectionUpdateResponseDTO.builder().collection(testCollection).metadata(metadata).build();

    // Create minimal test create request
    testCreateRequest = new CollectionCreateRequest();
    testCreateRequest.setType(CollectionType.BLOG);
    testCreateRequest.setTitle("New Test Blog");

    // Create test update DTO
    testUpdateDTO = new CollectionUpdateRequest();
    testUpdateDTO.setTitle("Updated Test Blog");
    testUpdateDTO.setDescription("An updated test blog collection");
  }

  @Test
  @DisplayName("POST /collections/createCollection should create a new collection")
  void createCollection_shouldCreateNewCollection() throws Exception {
    // Arrange
    when(collectionService.createCollection(any(CollectionCreateRequest.class)))
        .thenReturn(testCollectionUpdateResponse);

    // Act & Assert
    mockMvc
        .perform(
            post("/api/admin/collections/createCollection")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCreateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.collection.id", is(1)))
        .andExpect(jsonPath("$.collection.title", is("Test Blog")))
        .andExpect(jsonPath("$.collection.type", is("BLOG")));

    verify(collectionService).createCollection(any(CollectionCreateRequest.class));
  }

  @Test
  @DisplayName("POST /collections/createCollection should handle errors")
  void createCollection_shouldHandleErrors() throws Exception {
    // Arrange
    when(collectionService.createCollection(any(CollectionCreateRequest.class)))
        .thenThrow(new RuntimeException("Test error"));

    // Act & Assert
    mockMvc
        .perform(
            post("/api/admin/collections/createCollection")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCreateRequest)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$", containsString("Failed to create collection")));

    verify(collectionService).createCollection(any(CollectionCreateRequest.class));
  }

  @Test
  @DisplayName("PUT /collections/{id} should update collection metadata")
  void updateCollection_shouldUpdateCollectionMetadata() throws Exception {
    // Arrange
    when(collectionService.updateContent(eq(1L), any(CollectionUpdateRequest.class)))
        .thenReturn(testCollection);

    // Act & Assert
    mockMvc
        .perform(
            put("/api/admin/collections/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testUpdateDTO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(1)))
        .andExpect(jsonPath("$.title", is("Test Blog")));

    verify(collectionService).updateContent(eq(1L), any(CollectionUpdateRequest.class));
  }

  @Test
  @DisplayName("PUT /collections/{id} should handle not found error")
  void updateCollection_shouldHandleNotFoundError() throws Exception {
    // Arrange
    when(collectionService.updateContent(eq(999L), any(CollectionUpdateRequest.class)))
        .thenThrow(new IllegalArgumentException("Collection not found with ID: 999"));

    // Act & Assert
    mockMvc
        .perform(
            put("/api/admin/collections/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testUpdateDTO)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$", containsString("not found")));

    verify(collectionService).updateContent(eq(999L), any(CollectionUpdateRequest.class));
  }

  @Test
  @DisplayName("DELETE /collections/{id} should delete collection")
  void deleteCollection_shouldDeleteCollection() throws Exception {
    // Arrange
    doNothing().when(collectionService).deleteCollection(1L);

    // Act & Assert
    mockMvc
        .perform(delete("/api/admin/collections/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", is("Collection deleted successfully")));

    verify(collectionService).deleteCollection(1L);
  }

  @Test
  @DisplayName("DELETE /collections/{id} should handle not found error")
  void deleteCollection_shouldHandleNotFoundError() throws Exception {
    // Arrange
    doThrow(new IllegalArgumentException("Collection not found with ID: 999"))
        .when(collectionService)
        .deleteCollection(999L);

    // Act & Assert
    mockMvc
        .perform(delete("/api/admin/collections/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$", containsString("not found")));

    verify(collectionService).deleteCollection(999L);
  }

  @Test
  @DisplayName("GET /collections/metadata should return all metadata")
  void getMetadata_shouldReturnAllMetadata() throws Exception {
    // Arrange
    GeneralMetadataDTO metadata =
        GeneralMetadataDTO.builder()
            .tags(new ArrayList<>())
            .people(new ArrayList<>())
            .locations(new ArrayList<>())
            .collections(new ArrayList<>())
            .cameras(new ArrayList<>())
            .lenses(new ArrayList<>())
            .filmTypes(new ArrayList<>())
            .filmFormats(new ArrayList<>())
            .build();

    when(collectionService.getGeneralMetadata()).thenReturn(metadata);

    // Act & Assert
    mockMvc
        .perform(get("/api/admin/collections/metadata"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tags", notNullValue()))
        .andExpect(jsonPath("$.people", notNullValue()))
        .andExpect(jsonPath("$.locations", notNullValue()))
        .andExpect(jsonPath("$.collections", notNullValue()))
        .andExpect(jsonPath("$.cameras", notNullValue()))
        .andExpect(jsonPath("$.lenses", notNullValue()))
        .andExpect(jsonPath("$.filmTypes", notNullValue()))
        .andExpect(jsonPath("$.filmFormats", notNullValue()));

    verify(collectionService).getGeneralMetadata();
  }

  @Test
  @DisplayName("GET /collections/metadata should handle errors")
  void getMetadata_shouldHandleErrors() throws Exception {
    // Arrange
    when(collectionService.getGeneralMetadata()).thenThrow(new RuntimeException("Database error"));

    // Act & Assert
    mockMvc
        .perform(get("/api/admin/collections/metadata"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$", containsString("Failed to retrieve general metadata")));

    verify(collectionService).getGeneralMetadata();
  }

  @Test
  @DisplayName("GET /collections/{slug}/update should return collection with metadata")
  void getUpdateCollection_shouldReturnCollectionWithMetadata() throws Exception {
    // Arrange
    when(collectionService.getUpdateCollectionData("test-blog"))
        .thenReturn(testCollectionUpdateResponse);

    // Act & Assert
    mockMvc
        .perform(get("/api/admin/collections/test-blog/update"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.collection", notNullValue()))
        .andExpect(jsonPath("$.collection.id", is(1)))
        .andExpect(jsonPath("$.collection.slug", is("test-blog")))
        // Metadata is @JsonUnwrapped, so fields are at the root level
        .andExpect(jsonPath("$.tags", notNullValue()))
        .andExpect(jsonPath("$.people", notNullValue()))
        .andExpect(jsonPath("$.cameras", notNullValue()))
        .andExpect(jsonPath("$.lenses", notNullValue()))
        .andExpect(jsonPath("$.filmTypes", notNullValue()));

    verify(collectionService).getUpdateCollectionData("test-blog");
  }

  @Test
  @DisplayName("GET /collections/{slug}/update should handle not found error")
  void getUpdateCollection_shouldHandleNotFoundError() throws Exception {
    // Arrange
    when(collectionService.getUpdateCollectionData("non-existent-slug"))
        .thenThrow(
            new IllegalArgumentException("Collection not found with slug: non-existent-slug"));

    // Act & Assert
    mockMvc
        .perform(get("/api/admin/collections/non-existent-slug/update"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$", containsString("not found")));

    verify(collectionService).getUpdateCollectionData("non-existent-slug");
  }

  @Test
  @DisplayName("GET /collections/{slug}/update should handle errors")
  void getUpdateCollection_shouldHandleErrors() throws Exception {
    // Arrange
    when(collectionService.getUpdateCollectionData("test-blog"))
        .thenThrow(new RuntimeException("Database error"));

    // Act & Assert
    mockMvc
        .perform(get("/api/admin/collections/test-blog/update"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$", containsString("Failed to retrieve update data")));

    verify(collectionService).getUpdateCollectionData("test-blog");
  }

  @Test
  @DisplayName(
      "PUT /collections/{id} should update collection with tags using prev/new/remove pattern")
  void updateCollection_shouldUpdateCollectionWithTags() throws Exception {
    // Arrange
    TagUpdate tagUpdate =
        TagUpdate.builder()
            .prev(List.of(1L, 2L)) // Add existing tags 1 and 2
            .newValue(List.of("landscape", "nature")) // Create and add new tags
            .remove(List.of(3L)) // Remove tag 3
            .build();

    CollectionUpdateRequest updateRequest =
        CollectionUpdateRequest.builder()
            .id(1L)
            .title("Updated Blog with Tags")
            .tags(tagUpdate)
            .build();

    when(collectionService.updateContent(eq(1L), any(CollectionUpdateRequest.class)))
        .thenReturn(testCollection);

    // Act & Assert
    mockMvc
        .perform(
            put("/api/admin/collections/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(1)))
        .andExpect(jsonPath("$.title", is("Test Blog")));

    verify(collectionService).updateContent(eq(1L), any(CollectionUpdateRequest.class));
  }

  @Test
  @DisplayName(
      "PUT /collections/{id} should update collection with people using prev/new/remove pattern")
  void updateCollection_shouldUpdateCollectionWithPeople() throws Exception {
    // Arrange
    PersonUpdate personUpdate =
        PersonUpdate.builder()
            .prev(List.of(5L, 6L)) // Add existing people 5 and 6
            .newValue(List.of("John Doe", "Jane Smith")) // Create and add new people
            .remove(List.of(7L)) // Remove person 7
            .build();

    CollectionUpdateRequest updateRequest =
        CollectionUpdateRequest.builder()
            .id(1L)
            .title("Updated Blog with People")
            .people(personUpdate)
            .build();

    when(collectionService.updateContent(eq(1L), any(CollectionUpdateRequest.class)))
        .thenReturn(testCollection);

    // Act & Assert
    mockMvc
        .perform(
            put("/api/admin/collections/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(1)))
        .andExpect(jsonPath("$.title", is("Test Blog")));

    verify(collectionService).updateContent(eq(1L), any(CollectionUpdateRequest.class));
  }

  @Test
  @DisplayName("PUT /collections/{id} should update collection with both tags and people")
  void updateCollection_shouldUpdateCollectionWithTagsAndPeople() throws Exception {
    // Arrange
    TagUpdate tagUpdate = TagUpdate.builder().prev(List.of(1L)).newValue(List.of("travel")).build();

    PersonUpdate personUpdate =
        PersonUpdate.builder().prev(List.of(5L)).newValue(List.of("Alice")).build();

    CollectionUpdateRequest updateRequest =
        CollectionUpdateRequest.builder()
            .id(1L)
            .title("Updated Blog with Tags and People")
            .tags(tagUpdate)
            .people(personUpdate)
            .build();

    when(collectionService.updateContent(eq(1L), any(CollectionUpdateRequest.class)))
        .thenReturn(testCollection);

    // Act & Assert
    mockMvc
        .perform(
            put("/api/admin/collections/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(1)))
        .andExpect(jsonPath("$.title", is("Test Blog")));

    verify(collectionService).updateContent(eq(1L), any(CollectionUpdateRequest.class));
  }

  @Test
  @DisplayName("GET /collections/all should return all collections ordered by date")
  void getAllCollectionsOrderedByDate_shouldReturnAllCollections() throws Exception {
    // Arrange
    List<CollectionModel> collections = List.of(testCollection);
    org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 50);
    org.springframework.data.domain.Page<CollectionModel> page =
        new org.springframework.data.domain.PageImpl<>(collections, pageable, collections.size());
    when(collectionService.getAllCollections(any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

    // Act & Assert
    mockMvc
        .perform(get("/api/admin/collections/all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id", is(1)))
        .andExpect(jsonPath("$.content[0].title", is("Test Blog")))
        .andExpect(jsonPath("$.content[0].slug", is("test-blog")));

    verify(collectionService).getAllCollections(any(org.springframework.data.domain.Pageable.class));
  }

  @Test
  @DisplayName("GET /collections/all should handle errors")
  void getAllCollectionsOrderedByDate_shouldHandleErrors() throws Exception {
    // Arrange
    when(collectionService.getAllCollections(any(org.springframework.data.domain.Pageable.class)))
        .thenThrow(new RuntimeException("Database error"));

    // Act & Assert
    mockMvc
        .perform(get("/api/admin/collections/all"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$", containsString("Failed to retrieve collections")));

    verify(collectionService).getAllCollections(any(org.springframework.data.domain.Pageable.class));
  }

  @Test
  @DisplayName("POST /collections/{collectionId}/reorder should reorder content successfully")
  void reorderCollectionContent_shouldReorderContentSuccessfully() throws Exception {
    // Arrange
    CollectionReorderRequest.ReorderItem reorder1 =
        new CollectionReorderRequest.ReorderItem(10L, 0);
    CollectionReorderRequest.ReorderItem reorder2 =
        new CollectionReorderRequest.ReorderItem(11L, 1);
    CollectionReorderRequest reorderRequest =
        new CollectionReorderRequest(List.of(reorder1, reorder2));

    when(collectionService.reorderContent(eq(1L), any(CollectionReorderRequest.class)))
        .thenReturn(testCollection);

    // Act & Assert
    mockMvc
        .perform(
            post("/api/admin/collections/1/reorder")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reorderRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(1)))
        .andExpect(jsonPath("$.title", is("Test Blog")));

    verify(collectionService).reorderContent(eq(1L), any(CollectionReorderRequest.class));
  }

  @Test
  @DisplayName("POST /collections/{collectionId}/reorder should handle not found error")
  void reorderCollectionContent_shouldHandleNotFoundError() throws Exception {
    // Arrange
    CollectionReorderRequest.ReorderItem reorder1 =
        new CollectionReorderRequest.ReorderItem(10L, 0);
    CollectionReorderRequest reorderRequest = new CollectionReorderRequest(List.of(reorder1));

    when(collectionService.reorderContent(eq(999L), any(CollectionReorderRequest.class)))
        .thenThrow(new IllegalArgumentException("Collection not found with ID: 999"));

    // Act & Assert
    mockMvc
        .perform(
            post("/api/admin/collections/999/reorder")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reorderRequest)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$", containsString("not found")));

    verify(collectionService).reorderContent(eq(999L), any(CollectionReorderRequest.class));
  }

  @Test
  @DisplayName("POST /collections/{collectionId}/reorder should handle invalid request error")
  void reorderCollectionContent_shouldHandleInvalidRequestError() throws Exception {
    // Arrange
    CollectionReorderRequest.ReorderItem reorder1 =
        new CollectionReorderRequest.ReorderItem(10L, -1);
    CollectionReorderRequest reorderRequest = new CollectionReorderRequest(List.of(reorder1));

    when(collectionService.reorderContent(eq(1L), any(CollectionReorderRequest.class)))
        .thenThrow(new IllegalArgumentException("Invalid order index: -1"));

    // Act & Assert
    mockMvc
        .perform(
            post("/api/admin/collections/1/reorder")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reorderRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$", containsString("Invalid reorder request")));

    verify(collectionService).reorderContent(eq(1L), any(CollectionReorderRequest.class));
  }

  @Test
  @DisplayName("POST /collections/{collectionId}/reorder should handle general errors")
  void reorderCollectionContent_shouldHandleGeneralErrors() throws Exception {
    // Arrange
    CollectionReorderRequest.ReorderItem reorder1 =
        new CollectionReorderRequest.ReorderItem(10L, 0);
    CollectionReorderRequest reorderRequest = new CollectionReorderRequest(List.of(reorder1));

    when(collectionService.reorderContent(eq(1L), any(CollectionReorderRequest.class)))
        .thenThrow(new RuntimeException("Database connection error"));

    // Act & Assert
    mockMvc
        .perform(
            post("/api/admin/collections/1/reorder")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reorderRequest)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$", containsString("Failed to reorder content")));

    verify(collectionService).reorderContent(eq(1L), any(CollectionReorderRequest.class));
  }
}
