package edens.zac.portfolio.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wire-contract guard for the GIF/MP4 update payload. Asserts the keys the frontend sends today
 * (title, rating, tags, people, locations, collections) deserialize. People and locations are
 * content-level edits (audit 4.3) and use the same prev/newValue/remove pattern as tags.
 */
class UpdateGifWireTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  @DisplayName("FE gif update payload keys deserialize into populated fields")
  void shouldDeserializeFrontendGifUpdateKeys() throws Exception {
    String json =
        "{\"title\":\"Loop\",\"rating\":3,"
            + "\"tags\":{\"newValue\":[\"motion\"]},"
            + "\"people\":{\"prev\":[7]},"
            + "\"locations\":{\"prev\":[3]},"
            + "\"collections\":{\"remove\":[9]}}";

    ContentRequests.UpdateGif dto = mapper.readValue(json, ContentRequests.UpdateGif.class);

    assertEquals("Loop", dto.title());
    assertEquals(3, dto.rating());
    assertNotNull(dto.tags());
    assertEquals(List.of("motion"), dto.tags().newValue());
    assertNotNull(dto.people());
    assertEquals(List.of(7L), dto.people().prev());
    assertNotNull(dto.locations());
    assertEquals(List.of(3L), dto.locations().prev());
    assertNotNull(dto.collections());
    assertEquals(List.of(9L), dto.collections().remove());
  }
}
