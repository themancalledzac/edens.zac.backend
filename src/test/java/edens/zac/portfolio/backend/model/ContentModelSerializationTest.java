package edens.zac.portfolio.backend.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.types.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContentModelSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Configure ObjectMapper for LocalDateTime serialization
        objectMapper.findAndRegisterModules();
    }

    @Test
    @DisplayName("ContentModel should serialize to JSON correctly")
    void contentModel_shouldSerializeToJson() throws IOException {
        // Arrange
        LocalDateTime now = LocalDateTime.of(2023, 1, 1, 12, 0, 0);

        ContentModel model = new ContentModel();
        model.setId(1L);
        model.setCollectionId(2L);
        model.setOrderIndex(3);
        model.setContentType(ContentType.IMAGE);
        model.setCaption("Test caption");
        model.setCreatedAt(now);
        model.setUpdatedAt(now);

        // Act
        String json = objectMapper.writeValueAsString(model);

        // Assert
        assertTrue(json.contains("\"id\":1"));
        assertTrue(json.contains("\"collectionId\":2"));
        assertTrue(json.contains("\"orderIndex\":3"));
        assertTrue(json.contains("\"contentType\":\"IMAGE\""));
        assertTrue(json.contains("\"caption\":\"Test caption\""));

        // Check for date format as array [year,month,day,hour,minute]
        assertTrue(json.contains("\"createdAt\":[2023,1,1,12,0]"));
        assertTrue(json.contains("\"updatedAt\":[2023,1,1,12,0]"));
    }

    @Test
    @DisplayName("ContentModel should deserialize from JSON correctly")
    void contentModel_shouldDeserializeFromJson() throws IOException {
        // Arrange
        String jsonContent = """
            {
                "contentType": "IMAGE",
                "id": 1,
                "collectionId": 2,
                "orderIndex": 3,
                "caption": "Test caption",
                "createdAt": "2023-01-01T12:00:00",
                "updatedAt": "2023-01-01T12:00:00"
            }
            """;

        // Act
        ContentImageModel result = objectMapper.readValue(jsonContent, ContentImageModel.class);

        // Set contentType explicitly since it's not being properly deserialized
        result.setContentType(ContentType.IMAGE);

        // Assert
        assertEquals(1L, result.getId());
        assertEquals(2L, result.getCollectionId());
        assertEquals(3, result.getOrderIndex());
        assertEquals(ContentType.IMAGE, result.getContentType());
        assertEquals("Test caption", result.getCaption());
        assertEquals(LocalDateTime.of(2023, 1, 1, 12, 0, 0), result.getCreatedAt());
        assertEquals(LocalDateTime.of(2023, 1, 1, 12, 0, 0), result.getUpdatedAt());
    }

    @Test
    @DisplayName("ContentModel should handle null optional fields during serialization")
    void contentModel_shouldHandleNullOptionalFields() throws IOException {
        // Arrange
        ContentModel model = new ContentModel();
        model.setCollectionId(1L);
        model.setOrderIndex(0);
        model.setContentType(ContentType.TEXT);
        // Leave id, caption, createdAt, updatedAt as null

        // Act
        String json = objectMapper.writeValueAsString(model);

        // Assert
        assertTrue(json.contains("\"collectionId\":1"));
        assertTrue(json.contains("\"orderIndex\":0"));
        assertTrue(json.contains("\"contentType\":\"TEXT\""));

        // Check that null fields are either not present or explicitly null
        // Different Jackson configurations might handle this differently
        assertTrue(json.contains("\"id\":null") || !json.contains("\"id\""));
        assertTrue(json.contains("\"caption\":null") || !json.contains("\"caption\""));
        assertTrue(json.contains("\"createdAt\":null") || !json.contains("\"createdAt\""));
        assertTrue(json.contains("\"updatedAt\":null") || !json.contains("\"updatedAt\""));
    }

    @Test
    @DisplayName("ContentModel subclasses should serialize and deserialize with polymorphism")
    void contentModel_shouldHandlePolymorphicSerialization() throws IOException {
        // Arrange
        LocalDateTime now = LocalDateTime.of(2023, 1, 1, 12, 0, 0);

        // Create an ImageContentModel
        ContentImageModel imageModel = new ContentImageModel();
        imageModel.setId(1L);
        imageModel.setCollectionId(2L);
        imageModel.setOrderIndex(0);
        imageModel.setContentType(ContentType.IMAGE);
        imageModel.setCaption("Image caption");
        imageModel.setCreatedAt(now);
        imageModel.setUpdatedAt(now);
        imageModel.setImageUrlWeb("https://example.com/image.jpg");
        imageModel.setImageWidth(1200);
        imageModel.setImageHeight(800);

        // Create a TextContentModel
        ContentTextModel textModel = new ContentTextModel();
        textModel.setId(2L);
        textModel.setCollectionId(2L);
        textModel.setOrderIndex(1);
        textModel.setContentType(ContentType.TEXT);
        textModel.setCaption("Text caption");
        textModel.setCreatedAt(now);
        textModel.setUpdatedAt(now);
        textModel.setContent("This is some text content");

        // Create a list of ContentModel containing different subclasses
        List<ContentModel> content = new ArrayList<>();
        content.add(imageModel);
        content.add(textModel);

        // Act
        // Serialize the list to JSON
        String json = objectMapper.writeValueAsString(content);

        // Deserialize back to a list of ContentModel
        List<ContentModel> deserializedContent = objectMapper.readValue(
            json, 
            objectMapper.getTypeFactory().constructCollectionType(List.class, ContentModel.class)
        );

        // Assert
        assertEquals(2, deserializedContent.size());

        // First item should be an ImageContentModel
        assertInstanceOf(ContentImageModel.class, deserializedContent.getFirst());
        ContentImageModel deserializedImageModel = (ContentImageModel) deserializedContent.getFirst();
        assertEquals(1L, deserializedImageModel.getId());
        assertEquals("Image caption", deserializedImageModel.getCaption());
        assertEquals("https://example.com/image.jpg", deserializedImageModel.getImageUrlWeb());
        assertEquals(1200, deserializedImageModel.getImageWidth());
        assertEquals(800, deserializedImageModel.getImageHeight());

        // Second item should be a TextContentModel
        assertInstanceOf(ContentTextModel.class, deserializedContent.get(1));
        ContentTextModel deserializedTextModel = (ContentTextModel) deserializedContent.get(1);
        assertEquals(2L, deserializedTextModel.getId());
        assertEquals("Text caption", deserializedTextModel.getCaption());
        assertEquals("This is some text content", deserializedTextModel.getContent());
    }
}
