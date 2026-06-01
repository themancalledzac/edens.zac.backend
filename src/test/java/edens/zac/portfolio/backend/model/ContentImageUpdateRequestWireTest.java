package edens.zac.portfolio.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wire-contract guard: deserialize the literal JSON the frontend sends for an image update and
 * assert each relational key populates its field. Guards against the silent-drop class of bug (FE
 * key not declared on the DTO -> Jackson discards it -> 200 OK, no persistence).
 */
class ContentImageUpdateRequestWireTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  @DisplayName("FE image update payload keys deserialize into populated fields")
  void shouldDeserializeFrontendImageUpdateKeys() throws Exception {
    String json =
        "{"
            + "\"id\":42,"
            + "\"title\":\"Sunset\","
            + "\"caption\":\"On the ridge\","
            + "\"alt\":\"hiker on a ridge\","
            + "\"rating\":4,"
            + "\"locations\":{\"prev\":[3]},"
            + "\"people\":{\"prev\":[7]},"
            + "\"tags\":{\"newValue\":[\"landscape\"]},"
            + "\"collections\":{\"remove\":[9]},"
            + "\"camera\":{\"prev\":5},"
            + "\"lens\":{\"newValue\":\"50mm\"}"
            + "}";

    ContentImageUpdateRequest dto = mapper.readValue(json, ContentImageUpdateRequest.class);

    assertEquals(42L, dto.getId());
    assertEquals("On the ridge", dto.getCaption(), "FE 'caption' key must populate");
    assertEquals("hiker on a ridge", dto.getAlt(), "FE 'alt' key must populate");
    assertNotNull(dto.getLocations(), "plural 'locations' key must deserialize");
    assertEquals(List.of(3L), dto.getLocations().prev());
    assertNotNull(dto.getPeople(), "'people' key must deserialize");
    assertEquals(List.of(7L), dto.getPeople().prev());
    assertNotNull(dto.getTags());
    assertEquals(List.of("landscape"), dto.getTags().newValue());
    assertNotNull(dto.getCollections());
    assertEquals(List.of(9L), dto.getCollections().remove());
    assertNotNull(dto.getCamera());
    assertEquals(5L, dto.getCamera().getPrev());
    assertNotNull(dto.getLens());
    assertEquals("50mm", dto.getLens().getNewValue());
  }
}
