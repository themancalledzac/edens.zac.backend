package edens.zac.portfolio.backend.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.EmailService;
import edens.zac.portfolio.backend.types.CollectionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CollectionAdminEmailControllerTest {

  private MockMvc mockMvc;

  @Mock private CollectionService collectionService;
  @Mock private EmailService emailService;

  @InjectMocks private CollectionAdminEmailController controller;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private CollectionEntity galleryEntity() {
    return CollectionEntity.builder()
        .id(42L)
        .type(CollectionType.CLIENT_GALLERY)
        .title("Smith Wedding")
        .slug("smith-wedding")
        .visible(false)
        .build();
  }

  @Nested
  class HappyPath {

    @Test
    void hashesPassword_savesEntity_andDelegatesToEmailService() throws Exception {
      CollectionEntity gallery = galleryEntity();
      when(collectionService.findEntityById(42L)).thenReturn(gallery);
      when(emailService.sendGalleryPasswordEmail(
              eq("client@example.com"), eq("Smith Wedding"), eq("smith-wedding"), anyString()))
          .thenReturn(new EmailService.SendResult(true, null));

      mockMvc
          .perform(
              post("/api/admin/collections/42/send-password")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"email\":\"client@example.com\",\"password\":\"correctHorseBattery\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.sent").value(true));

      // Hash is set on the entity (BCrypt prefix).
      ArgumentCaptor<CollectionEntity> savedEntity =
          ArgumentCaptor.forClass(CollectionEntity.class);
      verify(collectionService).saveEntity(savedEntity.capture());
      String hash = savedEntity.getValue().getPasswordHash();
      org.assertj.core.api.Assertions.assertThat(hash).isNotNull();
      org.assertj.core.api.Assertions.assertThat(hash).startsWith("$2");
      org.assertj.core.api.Assertions.assertThat(hash).isNotEqualTo("correctHorseBattery");
    }

    @Test
    void emailDisabledStillReturns200WithReason() throws Exception {
      CollectionEntity gallery = galleryEntity();
      when(collectionService.findEntityById(42L)).thenReturn(gallery);
      when(emailService.sendGalleryPasswordEmail(
              anyString(), anyString(), anyString(), anyString()))
          .thenReturn(new EmailService.SendResult(false, "email-disabled"));

      mockMvc
          .perform(
              post("/api/admin/collections/42/send-password")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"email\":\"client@example.com\",\"password\":\"correctHorseBattery\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.sent").value(false))
          .andExpect(jsonPath("$.reason").value("email-disabled"));

      verify(collectionService).saveEntity(any(CollectionEntity.class));
    }
  }

  @Nested
  class Validation {

    @Test
    void nonClientGalleryReturns400_andDoesNotEmail() throws Exception {
      CollectionEntity portfolio =
          CollectionEntity.builder()
              .id(7L)
              .type(CollectionType.PORTFOLIO)
              .title("Public Portfolio")
              .slug("public-portfolio")
              .visible(true)
              .build();
      when(collectionService.findEntityById(7L)).thenReturn(portfolio);

      mockMvc
          .perform(
              post("/api/admin/collections/7/send-password")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"email\":\"client@example.com\",\"password\":\"correctHorseBattery\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.sent").value(false))
          .andExpect(jsonPath("$.reason").value("not-client-gallery"));

      verify(collectionService, never()).saveEntity(any(CollectionEntity.class));
      verify(emailService, never())
          .sendGalleryPasswordEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void invalidEmailReturns400() throws Exception {
      mockMvc
          .perform(
              post("/api/admin/collections/42/send-password")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"not-an-email\",\"password\":\"correctHorseBattery\"}"))
          .andExpect(status().isBadRequest());

      verify(collectionService, never()).saveEntity(any(CollectionEntity.class));
      verify(emailService, never())
          .sendGalleryPasswordEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shortPasswordReturns400() throws Exception {
      mockMvc
          .perform(
              post("/api/admin/collections/42/send-password")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"client@example.com\",\"password\":\"short\"}"))
          .andExpect(status().isBadRequest());

      verify(collectionService, never()).saveEntity(any(CollectionEntity.class));
      verify(emailService, never())
          .sendGalleryPasswordEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void missingPasswordReturns400() throws Exception {
      mockMvc
          .perform(
              post("/api/admin/collections/42/send-password")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"client@example.com\"}"))
          .andExpect(status().isBadRequest());
    }
  }
}
