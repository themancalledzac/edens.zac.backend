package edens.zac.portfolio.backend.controller.edit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.services.CollectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Delegation and mapping for the /api/edit surface. Authorization is pinned separately by
 * EditAccessWebMvcTest / EditControllerAuthorizationWebMvcTest -- standalone MockMvc registers
 * neither the security chain nor WebMvcConfigurer interceptors, which is exactly why these tests
 * make no auth assertions.
 */
@ExtendWith(MockitoExtension.class)
class EditControllerTest {

  private MockMvc mockMvc;

  @Mock private CollectionService collectionService;

  @InjectMocks private EditController editController;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(editController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void reorderDelegatesAndReturnsCollection() throws Exception {
    when(collectionService.reorderContent(eq(5L), any()))
        .thenReturn(CollectionModel.builder().id(5L).build());

    mockMvc
        .perform(
            post("/api/edit/collections/5/reorder")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reorders\":[{\"contentId\":42,\"newOrderIndex\":0}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(5));
  }

  @Test
  void reorderRejectsEmptyBody() throws Exception {
    mockMvc
        .perform(
            post("/api/edit/collections/5/reorder")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reorders\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void ratingPatchDelegatesAndReturns204() throws Exception {
    mockMvc
        .perform(
            patch("/api/edit/collections/5/rating")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":4}"))
        .andExpect(status().isNoContent());
    verify(collectionService).updateRating(5L, 4);
  }

  @Test
  void ratingPatchRejectsOutOfRange() throws Exception {
    mockMvc
        .perform(
            patch("/api/edit/collections/5/rating")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":9}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateCollectionWidensNarrowDtoAndDelegates() throws Exception {
    when(collectionService.updateContentWithMetadata(eq(5L), any()))
        .thenReturn(
            new CollectionRequests.UpdateResponse(CollectionModel.builder().id(5L).build(), null));

    mockMvc
        .perform(
            put("/api/edit/collections/5")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":5,\"title\":\"Renamed Gallery\"}"))
        .andExpect(status().isOk());

    var captor = org.mockito.ArgumentCaptor.forClass(CollectionRequests.Update.class);
    verify(collectionService).updateContentWithMetadata(eq(5L), captor.capture());
    assertThat(captor.getValue().title()).isEqualTo("Renamed Gallery");
    assertThat(captor.getValue().slug()).isNull();
    assertThat(captor.getValue().visibility()).isNull();
  }

  @Test
  void updateCollectionRejectsBodyIdMismatchWith400() throws Exception {
    mockMvc
        .perform(
            put("/api/edit/collections/5")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":6,\"title\":\"Renamed Gallery\"}"))
        .andExpect(status().isBadRequest());
    verify(collectionService, never()).updateContentWithMetadata(any(), any());
  }
}
