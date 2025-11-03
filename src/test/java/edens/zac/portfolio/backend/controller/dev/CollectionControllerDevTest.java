package edens.zac.portfolio.backend.controller.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.model.CollectionCreateRequest;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionUpdateRequest;
import edens.zac.portfolio.backend.model.CollectionUpdateResponseDTO;
import edens.zac.portfolio.backend.model.GeneralMetadataDTO;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.persistence.EntityNotFoundException;
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

import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CollectionControllerDevTest {

    private MockMvc mockMvc;

    @Mock
    private CollectionService collectionService;

    @InjectMocks
    private CollectionControllerDev contentCollectionController;

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
        GeneralMetadataDTO metadata = GeneralMetadataDTO.builder()
                .tags(new ArrayList<>())
                .people(new ArrayList<>())
                .collections(new ArrayList<>())
                .cameras(new ArrayList<>())
                .lenses(new ArrayList<>())
                .filmTypes(new ArrayList<>())
                .filmFormats(new ArrayList<>())
                .build();

        testCollectionUpdateResponse = CollectionUpdateResponseDTO.builder()
                .collection(testCollection)
                .metadata(metadata)
                .build();

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
        mockMvc.perform(post("/api/admin/collections/createCollection")
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
        mockMvc.perform(post("/api/admin/collections/createCollection")
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
        mockMvc.perform(put("/api/admin/collections/1")
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
                .thenThrow(new EntityNotFoundException("Collection with ID: 999 not found"));

        // Act & Assert
        mockMvc.perform(put("/api/admin/collections/999")
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
        mockMvc.perform(delete("/api/admin/collections/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is("Collection deleted successfully")));

        verify(collectionService).deleteCollection(1L);
    }

    @Test
    @DisplayName("DELETE /collections/{id} should handle not found error")
    void deleteCollection_shouldHandleNotFoundError() throws Exception {
        // Arrange
        doThrow(new EntityNotFoundException("Collection with ID: 999 not found"))
                .when(collectionService).deleteCollection(999L);

        // Act & Assert
        mockMvc.perform(delete("/api/admin/collections/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$", containsString("not found")));

        verify(collectionService).deleteCollection(999L);
    }
}
