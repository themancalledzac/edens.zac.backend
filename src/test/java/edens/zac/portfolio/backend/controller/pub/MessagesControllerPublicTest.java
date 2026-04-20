package edens.zac.portfolio.backend.controller.pub;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.entity.MessageEntity;
import edens.zac.portfolio.backend.services.MessageService;
import java.time.LocalDateTime;
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
class MessagesControllerPublicTest {

  private MockMvc mockMvc;

  @Mock private MessageService messageService;

  @InjectMocks private MessagesControllerPublic controller;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Nested
  class PostMessage {

    @Test
    void validBody_returns201WithIdAndCreatedAt() throws Exception {
      MessageEntity entity = new MessageEntity();
      entity.setId(10L);
      entity.setEmail("user@example.com");
      entity.setMessage("Hello");
      entity.setCreatedAt(LocalDateTime.of(2026, 4, 19, 12, 0));

      when(messageService.create("user@example.com", "Hello")).thenReturn(entity);

      mockMvc
          .perform(
              post("/api/public/messages")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"user@example.com\",\"message\":\"Hello\"}"))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").value(10))
          .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void invalidEmail_returns400() throws Exception {
      mockMvc
          .perform(
              post("/api/public/messages")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"not-an-email\",\"message\":\"Hello\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void missingMessageField_returns400() throws Exception {
      mockMvc
          .perform(
              post("/api/public/messages")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"user@example.com\"}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    void emptyBody_returns400() throws Exception {
      mockMvc
          .perform(
              post("/api/public/messages").contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().isBadRequest());
    }
  }
}
