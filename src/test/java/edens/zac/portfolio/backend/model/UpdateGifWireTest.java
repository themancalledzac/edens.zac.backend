package edens.zac.portfolio.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wire-contract guard for the GIF/MP4 update payload. Asserts the keys the frontend sends today
 * (title, rating, tags, collections) deserialize. NOTE: people/locations are intentionally NOT part
 * of UpdateGif today (see audit 4.3). If a later plan adds them, extend this test.
 */
class UpdateGifWireTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  @DisplayName("FE gif update payload keys deserialize into populated fields")
  void shouldDeserializeFrontendGifUpdateKeys() throws Exception {
    String json =
        "{\"title\":\"Loop\",\"rating\":3,"
            + "\"tags\":{\"newValue\":[\"motion\"]},"
            + "\"collections\":{\"remove\":[9]}}";

    ContentRequests.UpdateGif dto = mapper.readValue(json, ContentRequests.UpdateGif.class);

    assertEquals("Loop", dto.title());
    assertEquals(3, dto.rating());
    assertNotNull(dto.tags());
    assertEquals(List.of("motion"), dto.tags().newValue());
    assertNotNull(dto.collections());
    assertEquals(List.of(9L), dto.collections().remove());
  }
}
