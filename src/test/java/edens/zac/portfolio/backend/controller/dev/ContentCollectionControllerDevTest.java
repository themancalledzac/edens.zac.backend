package edens.zac.portfolio.backend.controller.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.model.ContentCollectionCreateRequest;
import edens.zac.portfolio.backend.model.ContentCollectionModel;
import edens.zac.portfolio.backend.model.ContentCollectionUpdateDTO;
import edens.zac.portfolio.backend.model.ContentCollectionUpdateResponseDTO;
import edens.zac.portfolio.backend.model.GeneralMetadataDTO;
import edens.zac.portfolio.backend.services.ContentCollectionService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContentCollectionControllerDevTest {

    private MockMvc mockMvc;

    @Mock
    private ContentCollectionService contentCollectionService;

    @InjectMocks
    private ContentCollectionControllerDev contentCollectionController;

    private ObjectMapper objectMapper;

    private ContentCollectionModel testCollection;
    private ContentCollectionUpdateResponseDTO testCollectionUpdateResponse;
    private ContentCollectionCreateRequest testCreateRequest;
    private ContentCollectionUpdateDTO testUpdateDTO;

    @BeforeEach
    void setUp() {
        // Initialize ObjectMapper
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        // Set up MockMvc
        mockMvc = MockMvcBuilders.standaloneSetup(contentCollectionController).build();

        // Create test collection model (for updateContent, addContentBlocks)
        testCollection = new ContentCollectionModel();
        testCollection.setId(1L);
        testCollection.setType(CollectionType.BLOG);
        testCollection.setTitle("Test Blog");
        testCollection.setSlug("test-blog");
        testCollection.setDescription("A test blog collection");
        testCollection.setVisible(true);
        testCollection.setPriority(1);
        testCollection.setBlocksPerPage(30);
        testCollection.setTotalBlocks(5);
        testCollection.setTotalPages(1);
        testCollection.setCurrentPage(0);
        testCollection.setContentBlocks(new ArrayList<>());
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

        testCollectionUpdateResponse = ContentCollectionUpdateResponseDTO.builder()
                .collection(testCollection)
                .metadata(metadata)
                .build();

        // Create minimal test create request
        testCreateRequest = new ContentCollectionCreateRequest();
        testCreateRequest.setType(CollectionType.BLOG);
        testCreateRequest.setTitle("New Test Blog");

        // Create test update DTO
        testUpdateDTO = new ContentCollectionUpdateDTO();
        testUpdateDTO.setTitle("Updated Test Blog");
        testUpdateDTO.setDescription("An updated test blog collection");
    }

    @Test
    @DisplayName("POST /collections/createCollection should create a new collection")
    void createCollection_shouldCreateNewCollection() throws Exception {
        // Arrange
        when(contentCollectionService.createCollection(any(ContentCollectionCreateRequest.class)))
                .thenReturn(testCollectionUpdateResponse);

        // Act & Assert
        mockMvc.perform(post("/api/write/collections/createCollection")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCreateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection.id", is(1)))
                .andExpect(jsonPath("$.collection.title", is("Test Blog")))
                .andExpect(jsonPath("$.collection.type", is("BLOG")));

        verify(contentCollectionService).createCollection(any(ContentCollectionCreateRequest.class));
    }

    @Test
    @DisplayName("POST /collections/createCollection should handle errors")
    void createCollection_shouldHandleErrors() throws Exception {
        // Arrange
        when(contentCollectionService.createCollection(any(ContentCollectionCreateRequest.class)))
                .thenThrow(new RuntimeException("Test error"));

        // Act & Assert
        mockMvc.perform(post("/api/write/collections/createCollection")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCreateRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$", containsString("Failed to create collection")));

        verify(contentCollectionService).createCollection(any(ContentCollectionCreateRequest.class));
    }

    @Test
    @DisplayName("PUT /collections/{id} should update collection metadata")
    void updateCollection_shouldUpdateCollectionMetadata() throws Exception {
        // Arrange
        when(contentCollectionService.updateContent(eq(1L), any(ContentCollectionUpdateDTO.class)))
                .thenReturn(testCollection);

        // Act & Assert
        mockMvc.perform(put("/api/write/collections/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testUpdateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Test Blog")));

        verify(contentCollectionService).updateContent(eq(1L), any(ContentCollectionUpdateDTO.class));
    }

    @Test
    @DisplayName("PUT /collections/{id} should handle not found error")
    void updateCollection_shouldHandleNotFoundError() throws Exception {
        // Arrange
        when(contentCollectionService.updateContent(eq(999L), any(ContentCollectionUpdateDTO.class)))
                .thenThrow(new EntityNotFoundException("Collection with ID: 999 not found"));

        // Act & Assert
        mockMvc.perform(put("/api/write/collections/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testUpdateDTO)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$", containsString("not found")));

        verify(contentCollectionService).updateContent(eq(999L), any(ContentCollectionUpdateDTO.class));
    }


    @Test
    @DisplayName("POST /collections/{id}/content should add content blocks (files only)")
    void addContentBlocks_shouldAddContentBlocks() throws Exception {
        // Arrange
        when(contentCollectionService.addContentBlocks(eq(1L), anyList()))
                .thenReturn(testCollection);

        // Create a mock image file
        MockMultipartFile imageFile = new MockMultipartFile(
                "files",
                "test-image.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        // Act & Assert
        mockMvc.perform(multipart("/api/write/collections/1/content")
                .file(imageFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Test Blog")));

        verify(contentCollectionService).addContentBlocks(eq(1L), anyList());
    }

    @Test
    @DisplayName("POST /collections/{id}/content should handle not found error")
    void addContentBlocks_shouldHandleNotFoundError() throws Exception {
        // Arrange
        when(contentCollectionService.addContentBlocks(eq(999L), anyList()))
                .thenThrow(new EntityNotFoundException("Collection with ID: 999 not found"));

        // Create a mock image file
        MockMultipartFile imageFile = new MockMultipartFile(
                "files",
                "test-image.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        // Act & Assert
        mockMvc.perform(multipart("/api/write/collections/999/content")
                .file(imageFile))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$", containsString("not found")));

        verify(contentCollectionService).addContentBlocks(eq(999L), anyList());
    }

    @Test
    @DisplayName("DELETE /collections/{id}/content/{blockId} should remove content block")
    void removeContentBlock_shouldRemoveContentBlock() throws Exception {
        // Arrange
        when(contentCollectionService.updateContent(eq(1L), any(ContentCollectionUpdateDTO.class)))
                .thenReturn(testCollection);

        // Act & Assert
        mockMvc.perform(delete("/api/write/collections/1/content/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Test Blog")));

        verify(contentCollectionService).updateContent(eq(1L), any(ContentCollectionUpdateDTO.class));
    }

    @Test
    @DisplayName("DELETE /collections/{id} should delete collection")
    void deleteCollection_shouldDeleteCollection() throws Exception {
        // Arrange
        doNothing().when(contentCollectionService).deleteCollection(1L);

        // Act & Assert
        mockMvc.perform(delete("/api/write/collections/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is("Collection deleted successfully")));

        verify(contentCollectionService).deleteCollection(1L);
    }

    @Test
    @DisplayName("DELETE /collections/{id} should handle not found error")
    void deleteCollection_shouldHandleNotFoundError() throws Exception {
        // Arrange
        doThrow(new EntityNotFoundException("Collection with ID: 999 not found"))
                .when(contentCollectionService).deleteCollection(999L);

        // Act & Assert
        mockMvc.perform(delete("/api/write/collections/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$", containsString("not found")));

        verify(contentCollectionService).deleteCollection(999L);
    }
}
