package edens.zac.portfolio.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Locks the exact JSON property names of the client/blog/password-protected booleans on every
 * collection-bearing payload. Guards against the Lombok/Jackson boolean-getter trap where a boolean
 * {@code isClient} getter {@code isClient()} silently serializes as {@code "client"}.
 */
class CollectionFlagSerializationTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    // A bespoke mapper is equivalent to Boot's here because src/main declares no property
    // naming strategy; if one is ever added, switch to @JsonTest.
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();
  }

  @Test
  @DisplayName("CollectionModel serializes isClient/isBlog under exactly those names")
  void collectionModel_serializesExactFlagNames() throws Exception {
    CollectionModel model =
        CollectionModel.builder()
            .id(1L)
            .isClient(true)
            .isBlog(false)
            .title("Smith Wedding")
            .slug("smith-wedding")
            .build();

    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(model));

    assertThat(json.has("isClient")).isTrue();
    assertThat(json.get("isClient").booleanValue()).isTrue();
    assertThat(json.has("isBlog")).isTrue();
    assertThat(json.get("isBlog").booleanValue()).isFalse();
    // The boolean-getter rename trap would emit these instead:
    assertThat(json.has("client")).isFalse();
    assertThat(json.has("blog")).isFalse();
  }

  @Test
  @DisplayName("Records.CollectionList serializes isClient/isBlog under exactly those names")
  void collectionList_serializesExactFlagNames() throws Exception {
    Records.CollectionList list =
        new Records.CollectionList(2L, "Daily Blog", "daily-blog", null, null, false, true);

    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(list));

    assertThat(json.has("isClient")).isTrue();
    assertThat(json.get("isClient").booleanValue()).isFalse();
    assertThat(json.has("isBlog")).isTrue();
    assertThat(json.get("isBlog").booleanValue()).isTrue();
    assertThat(json.has("client")).isFalse();
    assertThat(json.has("blog")).isFalse();
  }

  @Test
  @DisplayName("ContentModels.Collection serializes locations as {id, name, slug}")
  void contentModelsCollection_serializesLocationShape() throws Exception {
    // Pins the contract the frontend's LocationModel reads. collectionRefMatchesCriteria matches on
    // `name` and the chip links use `slug`, so a rename on either side silently empties the filter
    // rather than failing.
    ContentModels.Collection block =
        new ContentModels.Collection(
            4L,
            edens.zac.portfolio.backend.types.ContentType.COLLECTION,
            "Chamonix",
            null,
            null,
            0,
            true,
            null,
            null,
            40L,
            "chamonix",
            false,
            false,
            false,
            null,
            null,
            null,
            List.of(),
            List.of(new Records.Location(11L, "Chamonix, France", "chamonix-france")),
            edens.zac.portfolio.backend.types.CollectionVisibility.LISTED);

    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(block));

    assertThat(json.has("locations")).isTrue();
    assertThat(json.get("locations").isArray()).isTrue();
    JsonNode location = json.get("locations").get(0);
    assertThat(location.get("id").longValue()).isEqualTo(11L);
    assertThat(location.get("name").textValue()).isEqualTo("Chamonix, France");
    assertThat(location.get("slug").textValue()).isEqualTo("chamonix-france");
  }

  @Test
  @DisplayName(
      "ContentModels.Collection serializes isClient/isBlog/isPasswordProtected under exactly those"
          + " names")
  void contentModelsCollection_serializesExactFlagNames() throws Exception {
    ContentModels.Collection block =
        new ContentModels.Collection(
            3L,
            edens.zac.portfolio.backend.types.ContentType.COLLECTION,
            "Wrapped Gallery",
            null,
            null,
            0,
            true,
            null,
            null,
            30L,
            "wrapped-gallery",
            true,
            false,
            true,
            null,
            null,
            null,
            List.of(),
            List.of(),
            edens.zac.portfolio.backend.types.CollectionVisibility.LISTED);

    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(block));

    assertThat(json.has("isClient")).isTrue();
    assertThat(json.get("isClient").booleanValue()).isTrue();
    assertThat(json.has("isBlog")).isTrue();
    assertThat(json.get("isBlog").booleanValue()).isFalse();
    assertThat(json.has("isPasswordProtected")).isTrue();
    assertThat(json.get("isPasswordProtected").booleanValue()).isTrue();
    assertThat(json.has("client")).isFalse();
    assertThat(json.has("blog")).isFalse();
    assertThat(json.has("passwordProtected")).isFalse();
  }

  @Test
  @DisplayName("Create request deserializes isClient/isBlog from exactly those names")
  void createRequest_deserializesFlags() throws Exception {
    String json =
        """
        {"title": "New Gallery", "isClient": true, "isBlog": false}
        """;

    CollectionRequests.Create create =
        objectMapper.readValue(json, CollectionRequests.Create.class);

    assertThat(create.isClient()).isTrue();
    assertThat(create.isBlog()).isFalse();
    assertThat(create.title()).isEqualTo("New Gallery");
  }

  @Test
  @DisplayName("SaveAsCollectionRequest deserializes isClient/isBlog from exactly those names")
  void saveAsCollectionRequest_deserializesFlags() throws Exception {
    String json =
        """
        {"isClient": true, "isBlog": false}
        """;

    SaveAsCollectionRequest request = objectMapper.readValue(json, SaveAsCollectionRequest.class);

    assertThat(request.isClient()).isTrue();
    assertThat(request.isBlog()).isFalse();
  }

  @Test
  @DisplayName("Update request deserializes isClient/isBlog from exactly those names")
  void updateRequest_deserializesFlags() throws Exception {
    String json =
        """
        {"id": 9, "isClient": false, "isBlog": true}
        """;

    CollectionRequests.Update update =
        objectMapper.readValue(json, CollectionRequests.Update.class);

    assertThat(update.id()).isEqualTo(9L);
    assertThat(update.isClient()).isFalse();
    assertThat(update.isBlog()).isTrue();
  }
}
