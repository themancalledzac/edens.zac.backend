package edens.zac.portfolio.backend.controller.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.model.ContentCollectionCreateDTO;
import edens.zac.portfolio.backend.model.ContentCollectionModel;
import edens.zac.portfolio.backend.model.ContentCollectionUpdateDTO;
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
    private ContentCollectionCreateDTO testCreateDTO;
    private ContentCollectionUpdateDTO testUpdateDTO;

    @BeforeEach
    void setUp() {
        // Initialize ObjectMapper
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        // Set up MockMvc
        mockMvc = MockMvcBuilders.standaloneSetup(contentCollectionController).build();

        // Create test collection
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

        // Create test create DTO
        testCreateDTO = new ContentCollectionCreateDTO();
        testCreateDTO.setType(CollectionType.BLOG);
        testCreateDTO.setTitle("New Test Blog");
        testCreateDTO.setDescription("A new test blog collection");
        testCreateDTO.setVisible(true);
        testCreateDTO.setPriority(1);
        testCreateDTO.setBlocksPerPage(30);

        // Create test update DTO
        testUpdateDTO = new ContentCollectionUpdateDTO();
        testUpdateDTO.setTitle("Updated Test Blog");
        testUpdateDTO.setDescription("An updated test blog collection");
    }

    @Test
    @DisplayName("POST /collections/createCollection should create a new collection")
    void createCollection_shouldCreateNewCollection() throws Exception {
        // Arrange
        when(contentCollectionService.createCollection(any(ContentCollectionCreateDTO.class)))
                .thenReturn(testCollection);

        // Act & Assert
        mockMvc.perform(post("/api/write/collections/createCollection")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCreateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("Test Blog")))
                .andExpect(jsonPath("$.type", is("BLOG")));

        verify(contentCollectionService).createCollection(any(ContentCollectionCreateDTO.class));
    }

    @Test
    @DisplayName("POST /collections/createCollection should handle errors")
    void createCollection_shouldHandleErrors() throws Exception {
        // Arrange
        when(contentCollectionService.createCollection(any(ContentCollectionCreateDTO.class)))
                .thenThrow(new RuntimeException("Test error"));

        // Act & Assert
        mockMvc.perform(post("/api/write/collections/createCollection")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCreateDTO)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$", containsString("Failed to create collection")));

        verify(contentCollectionService).createCollection(any(ContentCollectionCreateDTO.class));
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
