package edens.zac.portfolio.backend.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.model.CollectionRequests.GalleryAccessRequest;
import edens.zac.portfolio.backend.model.CollectionRequests.GalleryAccessResponse;
import edens.zac.portfolio.backend.services.CollectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CollectionAdminControllerTest {

  private MockMvc mockMvc;

  @Mock private CollectionService collectionService;

  @InjectMocks private CollectionAdminController controller;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Nested
  class SetAndSend {

    @Test
    void savesPasswordAndSendsEmailPerRecipient() throws Exception {
      when(collectionService.updateGalleryAccess(eq(42L), any(GalleryAccessRequest.class)))
          .thenReturn(new GalleryAccessResponse(true, true, null));

      mockMvc
          .perform(
              post("/api/admin/collections/42/gallery-access")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"password\":\"sunshine\",\"emails\":[\"a@example.com\",\"b@example.com\"]}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.saved").value(true))
          .andExpect(jsonPath("$.emailsSent").value(true));
    }

    @Test
    void emailDisabledReturnsSavedTrueEmailsSentFalse() throws Exception {
      when(collectionService.updateGalleryAccess(eq(42L), any(GalleryAccessRequest.class)))
          .thenReturn(new GalleryAccessResponse(true, false, "email-disabled"));

      mockMvc
          .perform(
              post("/api/admin/collections/42/gallery-access")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"password\":\"sunshine\",\"emails\":[\"a@example.com\"]}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.saved").value(true))
          .andExpect(jsonPath("$.emailsSent").value(false))
          .andExpect(jsonPath("$.reason").value("email-disabled"));
    }
  }

  @Nested
  class SetOnly {

    @Test
    void nullEmailsStoresPasswordWithoutSendingEmail() throws Exception {
      when(collectionService.updateGalleryAccess(eq(42L), any(GalleryAccessRequest.class)))
          .thenReturn(new GalleryAccessResponse(true, false, null));

      mockMvc
          .perform(
              post("/api/admin/collections/42/gallery-access")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"password\":\"sunshine\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.saved").value(true))
          .andExpect(jsonPath("$.emailsSent").value(false));
    }

    @Test
    void emptyEmailListStoresPasswordWithoutSendingEmail() throws Exception {
      when(collectionService.updateGalleryAccess(eq(42L), any(GalleryAccessRequest.class)))
          .thenReturn(new GalleryAccessResponse(true, false, null));

      mockMvc
          .perform(
              post("/api/admin/collections/42/gallery-access")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"password\":\"sunshine\",\"emails\":[]}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.saved").value(true))
          .andExpect(jsonPath("$.emailsSent").value(false));
    }
  }

  @Nested
  class ClearPassword {

    @Test
    void nullPasswordClearsGalleryAccess() throws Exception {
      when(collectionService.updateGalleryAccess(eq(42L), any(GalleryAccessRequest.class)))
          .thenReturn(new GalleryAccessResponse(true, false, null));

      mockMvc
          .perform(
              post("/api/admin/collections/42/gallery-access")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.saved").value(true))
          .andExpect(jsonPath("$.emailsSent").value(false));
    }
  }

  @Nested
  class Validation {

    @Test
    void nonClientGalleryReturns400() throws Exception {
      when(collectionService.updateGalleryAccess(eq(7L), any(GalleryAccessRequest.class)))
          .thenReturn(new GalleryAccessResponse(false, false, "not-client-gallery"));

      mockMvc
          .perform(
              post("/api/admin/collections/7/gallery-access")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"password\":\"sunshine\",\"emails\":[\"a@example.com\"]}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.saved").value(false))
          .andExpect(jsonPath("$.reason").value("not-client-gallery"));
    }

    @Test
    void invalidEmailInListReturns400() throws Exception {
      mockMvc
          .perform(
              post("/api/admin/collections/42/gallery-access")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"password\":\"sunshine\",\"emails\":[\"not-an-email\"]}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    void shortPasswordReturns400() throws Exception {
      mockMvc
          .perform(
              post("/api/admin/collections/42/gallery-access")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"password\":\"abc\",\"emails\":[\"a@example.com\"]}"))
          .andExpect(status().isBadRequest());
    }
  }
}
