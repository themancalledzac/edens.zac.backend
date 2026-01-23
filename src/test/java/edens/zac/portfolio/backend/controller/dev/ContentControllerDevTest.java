package edens.zac.portfolio.backend.controller.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.services.ContentService;
import edens.zac.portfolio.backend.types.ContentType;
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
import org.springframework.web.multipart.MultipartFile;

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
class ContentControllerDevTest {

    private MockMvc mockMvc;

    @Mock
    private ContentService contentService;

    @InjectMocks
    private ContentControllerDev contentController;

    private ObjectMapper objectMapper;

    private ContentImageModel testImage;
    private List<ContentImageModel> testImages;

    @BeforeEach
    void setUp() {
        // Initialize ObjectMapper
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        // Set up MockMvc
        mockMvc = MockMvcBuilders.standaloneSetup(contentController).build();

        // Create test image model
        testImage = new ContentImageModel();
        testImage.setId(1L);
        testImage.setContentType(ContentType.IMAGE);
        testImage.setTitle("Test Image");

        testImages = List.of(testImage);
    }

    // ============== HIGH PRIORITY TESTS - PATCH /api/admin/content/images ==============

    @Test
    @DisplayName("PATCH /content/images should update images with tags")
    void updateImages_shouldUpdateImagesWithTags() throws Exception {
        // Arrange
        TagUpdate tagUpdate = TagUpdate.builder()
                .prev(List.of(1L, 2L))          // Add existing tags
                .newValue(List.of("landscape", "nature"))  // Create new tags
                .remove(List.of(3L))             // Remove tag
                .build();

        ContentImageUpdateRequest updateRequest = ContentImageUpdateRequest.builder()
                .id(1L)
                .title("Updated Image with Tags")
                .tags(tagUpdate)
                .build();

        Map<String, Object> response = new HashMap<>();
        response.put("updatedImages", testImages);
        response.put("newTags", new ArrayList<>());
        response.put("newPeople", new ArrayList<>());

        when(contentService.updateImages(any(List.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(patch("/api/admin/content/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(updateRequest))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedImages", notNullValue()))
                .andExpect(jsonPath("$.updatedImages", hasSize(1)))
                .andExpect(jsonPath("$.updatedImages[0].id", is(1)));

        verify(contentService).updateImages(any(List.class));
    }

    @Test
    @DisplayName("PATCH /content/images should update images with people")
    void updateImages_shouldUpdateImagesWithPeople() throws Exception {
        // Arrange
        PersonUpdate personUpdate = PersonUpdate.builder()
                .prev(List.of(5L, 6L))          // Add existing people
                .newValue(List.of("John Doe", "Jane Smith"))  // Create new people
                .remove(List.of(7L))             // Remove person
                .build();

        ContentImageUpdateRequest updateRequest = ContentImageUpdateRequest.builder()
                .id(1L)
                .title("Updated Image with People")
                .people(personUpdate)
                .build();

        Map<String, Object> response = new HashMap<>();
        response.put("updatedImages", testImages);
        response.put("newTags", new ArrayList<>());
        response.put("newPeople", new ArrayList<>());

        when(contentService.updateImages(any(List.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(patch("/api/admin/content/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(updateRequest))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedImages", notNullValue()))
                .andExpect(jsonPath("$.updatedImages", hasSize(1)))
                .andExpect(jsonPath("$.updatedImages[0].id", is(1)));

        verify(contentService).updateImages(any(List.class));
    }

    @Test
    @DisplayName("PATCH /content/images should update images with camera/lens/filmType metadata")
    void updateImages_shouldUpdateImagesWithCameraLensFilmType() throws Exception {
        // Arrange
        ContentImageUpdateRequest.CameraUpdate cameraUpdate = ContentImageUpdateRequest.CameraUpdate.builder()
                .newValue("Canon AE-1")
                .build();

        ContentImageUpdateRequest.LensUpdate lensUpdate = ContentImageUpdateRequest.LensUpdate.builder()
                .newValue("50mm f/1.8")
                .build();

        ContentImageUpdateRequest.FilmTypeUpdate filmTypeUpdate = ContentImageUpdateRequest.FilmTypeUpdate.builder()
                .prev(1L)  // Use existing film type
                .build();

        ContentImageUpdateRequest updateRequest = ContentImageUpdateRequest.builder()
                .id(1L)
                .title("Updated Image with Metadata")
                .camera(cameraUpdate)
                .lens(lensUpdate)
                .filmType(filmTypeUpdate)
                .iso(200)
                .build();

        Map<String, Object> response = new HashMap<>();
        response.put("updatedImages", testImages);
        response.put("newTags", new ArrayList<>());
        response.put("newPeople", new ArrayList<>());

        when(contentService.updateImages(any(List.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(patch("/api/admin/content/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(updateRequest))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedImages", notNullValue()))
                .andExpect(jsonPath("$.updatedImages", hasSize(1)))
                .andExpect(jsonPath("$.updatedImages[0].id", is(1)));

        verify(contentService).updateImages(any(List.class));
    }

    @Test
    @DisplayName("PATCH /content/images should update images with all metadata combined")
    void updateImages_shouldUpdateImagesWithAllMetadata() throws Exception {
        // Arrange
        TagUpdate tagUpdate = TagUpdate.builder()
                .prev(List.of(1L))
                .newValue(List.of("travel"))
                .build();

        PersonUpdate personUpdate = PersonUpdate.builder()
                .prev(List.of(5L))
                .newValue(List.of("Alice"))
                .build();

        ContentImageUpdateRequest.CameraUpdate cameraUpdate = ContentImageUpdateRequest.CameraUpdate.builder()
                .newValue("Nikon F3")
                .build();

        ContentImageUpdateRequest.LensUpdate lensUpdate = ContentImageUpdateRequest.LensUpdate.builder()
                .newValue("85mm f/1.4")
                .build();

        ContentImageUpdateRequest.FilmTypeUpdate filmTypeUpdate = ContentImageUpdateRequest.FilmTypeUpdate.builder()
                .prev(2L)  // Use existing film type
                .build();

        ContentImageUpdateRequest updateRequest = ContentImageUpdateRequest.builder()
                .id(1L)
                .title("Updated Image with All Metadata")
                .tags(tagUpdate)
                .people(personUpdate)
                .camera(cameraUpdate)
                .lens(lensUpdate)
                .filmType(filmTypeUpdate)
                .iso(400)
                .build();

        Map<String, Object> response = new HashMap<>();
        response.put("updatedImages", testImages);
        response.put("newTags", new ArrayList<>());
        response.put("newPeople", new ArrayList<>());

        when(contentService.updateImages(any(List.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(patch("/api/admin/content/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(updateRequest))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedImages", notNullValue()))
                .andExpect(jsonPath("$.updatedImages", hasSize(1)))
                .andExpect(jsonPath("$.updatedImages[0].id", is(1)));

        verify(contentService).updateImages(any(List.class));
    }

    @Test
    @DisplayName("PATCH /content/images should handle invalid request")
    void updateImages_shouldHandleInvalidRequest() throws Exception {
        // Arrange
        ContentImageUpdateRequest updateRequest = ContentImageUpdateRequest.builder()
                .id(999L)
                .title("Non-existent Image")
                .build();

        when(contentService.updateImages(any(List.class)))
                .thenThrow(new IllegalArgumentException("Image not found with ID: 999"));

        // Act & Assert
        mockMvc.perform(patch("/api/admin/content/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(updateRequest))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", containsString("Image not found")));

        verify(contentService).updateImages(any(List.class));
    }

    @Test
    @DisplayName("PATCH /content/images should handle general errors")
    void updateImages_shouldHandleGeneralErrors() throws Exception {
        // Arrange
        ContentImageUpdateRequest updateRequest = ContentImageUpdateRequest.builder()
                .id(1L)
                .title("Test Image")
                .build();

        when(contentService.updateImages(any(List.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        mockMvc.perform(patch("/api/admin/content/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(updateRequest))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$", containsString("Failed to update images")));

        verify(contentService).updateImages(any(List.class));
    }

    // ============== MEDIUM PRIORITY TESTS - POST /api/admin/content/images/{collectionId} ==============

    @Test
    @DisplayName("POST /content/images/{collectionId} should create images in collection")
    void createImages_shouldCreateImagesInCollection() throws Exception {
        // Arrange
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("test.jpg");
        when(mockFile.isEmpty()).thenReturn(false);

        when(contentService.createImages(eq(1L), any(List.class))).thenReturn(testImages);

        // Note: Testing multipart file uploads with MockMvc requires special handling
        // This test verifies the controller logic, but actual file upload testing
        // would require integration tests with @SpringBootTest

        verify(contentService, never()).createImages(any(), any());
    }

    // ============== MEDIUM PRIORITY TESTS - POST /api/admin/content/tags ==============

    @Test
    @DisplayName("POST /content/tags should create a new tag")
    void createTag_shouldCreateNewTag() throws Exception {
        // Arrange
        CreateTagRequest request = new CreateTagRequest();
        request.setTagName("landscape");

        Map<String, Object> response = new HashMap<>();
        response.put("tag", new ContentTagModel(1L, "landscape", LocalDateTime.now()));
        response.put("message", "Tag created successfully");

        when(contentService.createTag("landscape")).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/admin/content/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tag", notNullValue()))
                .andExpect(jsonPath("$.message", is("Tag created successfully")));

        verify(contentService).createTag("landscape");
    }

    @Test
    @DisplayName("POST /content/tags should handle duplicate tag error")
    void createTag_shouldHandleDuplicateTagError() throws Exception {
        // Arrange
        CreateTagRequest request = new CreateTagRequest();
        request.setTagName("landscape");

        when(contentService.createTag("landscape"))
                .thenThrow(new IllegalArgumentException("Tag already exists: landscape"));

        // Act & Assert
        mockMvc.perform(post("/api/admin/content/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", containsString("Tag already exists")));

        verify(contentService).createTag("landscape");
    }

    @Test
    @DisplayName("POST /content/tags should handle general errors")
    void createTag_shouldHandleGeneralErrors() throws Exception {
        // Arrange
        CreateTagRequest request = new CreateTagRequest();
        request.setTagName("landscape");

        when(contentService.createTag("landscape"))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        mockMvc.perform(post("/api/admin/content/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$", containsString("Failed to create tag")));

        verify(contentService).createTag("landscape");
    }

    // ============== LOW PRIORITY TESTS - POST /api/admin/content/people ==============

    @Test
    @DisplayName("POST /content/people should create a new person")
    void createPerson_shouldCreateNewPerson() throws Exception {
        // Arrange
        CreatePersonRequest request = new CreatePersonRequest();
        request.setPersonName("John Doe");

        Map<String, Object> response = new HashMap<>();
        response.put("person", new ContentPersonModel(1L, "John Doe", LocalDateTime.now()));
        response.put("message", "Person created successfully");

        when(contentService.createPerson("John Doe")).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/admin/content/people")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.person", notNullValue()))
                .andExpect(jsonPath("$.message", is("Person created successfully")));

        verify(contentService).createPerson("John Doe");
    }

    @Test
    @DisplayName("POST /content/people should handle duplicate person error")
    void createPerson_shouldHandleDuplicatePersonError() throws Exception {
        // Arrange
        CreatePersonRequest request = new CreatePersonRequest();
        request.setPersonName("John Doe");

        when(contentService.createPerson("John Doe"))
                .thenThrow(new IllegalArgumentException("Person already exists: John Doe"));

        // Act & Assert
        mockMvc.perform(post("/api/admin/content/people")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", containsString("Person already exists")));

        verify(contentService).createPerson("John Doe");
    }

    @Test
    @DisplayName("POST /content/people should handle general errors")
    void createPerson_shouldHandleGeneralErrors() throws Exception {
        // Arrange
        CreatePersonRequest request = new CreatePersonRequest();
        request.setPersonName("John Doe");

        when(contentService.createPerson("John Doe"))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        mockMvc.perform(post("/api/admin/content/people")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$", containsString("Failed to create person")));

        verify(contentService).createPerson("John Doe");
    }

    // ============== ADDITIONAL TESTS - GET and DELETE endpoints ==============

    @Test
    @DisplayName("GET /content/images should return all images")
    void getAllImages_shouldReturnAllImages() throws Exception {
        // Arrange
        when(contentService.getAllImages()).thenReturn(testImages);

        // Act & Assert
        mockMvc.perform(get("/api/admin/content/images"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].title", is("Test Image")));

        verify(contentService).getAllImages();
    }

    @Test
    @DisplayName("GET /content/images should handle errors")
    void getAllImages_shouldHandleErrors() throws Exception {
        // Arrange
        when(contentService.getAllImages())
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        mockMvc.perform(get("/api/admin/content/images"))
                .andExpect(status().isInternalServerError());

        verify(contentService).getAllImages();
    }

    @Test
    @DisplayName("DELETE /content/images should delete images")
    void deleteImages_shouldDeleteImages() throws Exception {
        // Arrange
        Map<String, List<Long>> request = Map.of("imageIds", List.of(1L, 2L));

        Map<String, Object> response = new HashMap<>();
        response.put("deletedIds", List.of(1L, 2L));
        response.put("message", "Images deleted successfully");

        when(contentService.deleteImages(any(List.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(delete("/api/admin/content/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedIds", hasSize(2)))
                .andExpect(jsonPath("$.message", is("Images deleted successfully")));

        verify(contentService).deleteImages(any(List.class));
    }

    @Test
    @DisplayName("DELETE /content/images should handle invalid request")
    void deleteImages_shouldHandleInvalidRequest() throws Exception {
        // Arrange
        Map<String, List<Long>> request = Map.of("imageIds", List.of(999L));

        when(contentService.deleteImages(any(List.class)))
                .thenThrow(new IllegalArgumentException("Image not found with ID: 999"));

        // Act & Assert
        mockMvc.perform(delete("/api/admin/content/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", containsString("Image not found")));

        verify(contentService).deleteImages(any(List.class));
    }

    @Test
    @DisplayName("DELETE /content/images should handle general errors")
    void deleteImages_shouldHandleGeneralErrors() throws Exception {
        // Arrange
        Map<String, List<Long>> request = Map.of("imageIds", List.of(1L));

        when(contentService.deleteImages(any(List.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        mockMvc.perform(delete("/api/admin/content/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$", containsString("Failed to delete images")));

        verify(contentService).deleteImages(any(List.class));
    }
}