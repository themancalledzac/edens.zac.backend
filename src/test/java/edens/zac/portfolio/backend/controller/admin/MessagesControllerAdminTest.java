package edens.zac.portfolio.backend.controller.admin;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.dao.MessageRepository;
import edens.zac.portfolio.backend.entity.MessageEntity;
import java.time.LocalDateTime;
import java.util.List;
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
class MessagesControllerAdminTest {

  private MockMvc mockMvc;

  @Mock private MessageRepository messageRepository;

  @InjectMocks private MessagesControllerAdmin controller;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private MessageEntity sampleMessage(long id, String email, String body) {
    MessageEntity m = new MessageEntity();
    m.setId(id);
    m.setEmail(email);
    m.setMessage(body);
    m.setCreatedAt(LocalDateTime.of(2026, 4, 27, 12, 0));
    return m;
  }

  @Nested
  class GetMessages {

    @Test
    void returns200WithPaginatedShape() throws Exception {
      when(messageRepository.findAll(50, 0))
          .thenReturn(
              List.of(
                  sampleMessage(2L, "two@example.com", "two"),
                  sampleMessage(1L, "one@example.com", "one")));
      when(messageRepository.count()).thenReturn(2L);

      mockMvc
          .perform(get("/api/admin/messages").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.messages.length()").value(2))
          .andExpect(jsonPath("$.messages[0].id").value(2))
          .andExpect(jsonPath("$.messages[0].email").value("two@example.com"))
          .andExpect(jsonPath("$.messages[0].message").value("two"))
          .andExpect(jsonPath("$.messages[0].createdAt").isNotEmpty())
          .andExpect(jsonPath("$.total").value(2))
          .andExpect(jsonPath("$.limit").value(50))
          .andExpect(jsonPath("$.offset").value(0));
    }

    @Test
    void defaultsToLimit50AndOffset0WhenAbsent() throws Exception {
      when(messageRepository.findAll(50, 0)).thenReturn(List.of());
      when(messageRepository.count()).thenReturn(0L);

      mockMvc
          .perform(get("/api/admin/messages"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.limit").value(50))
          .andExpect(jsonPath("$.offset").value(0));

      verify(messageRepository).findAll(50, 0);
    }

    @Test
    void clampsExcessiveLimitTo200() throws Exception {
      when(messageRepository.findAll(200, 0)).thenReturn(List.of());
      when(messageRepository.count()).thenReturn(0L);

      mockMvc
          .perform(get("/api/admin/messages").param("limit", "999999"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.limit").value(200));

      ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
      verify(messageRepository).findAll(limitCaptor.capture(), anyInt());
      org.assertj.core.api.Assertions.assertThat(limitCaptor.getValue()).isEqualTo(200);
    }

    @Test
    void clampsNegativeOffsetToZero() throws Exception {
      when(messageRepository.findAll(50, 0)).thenReturn(List.of());
      when(messageRepository.count()).thenReturn(0L);

      mockMvc
          .perform(get("/api/admin/messages").param("offset", "-5"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.offset").value(0));

      verify(messageRepository).findAll(50, 0);
    }

    @Test
    void emptyRepoReturnsEmptyShape() throws Exception {
      when(messageRepository.findAll(50, 0)).thenReturn(List.of());
      when(messageRepository.count()).thenReturn(0L);

      mockMvc
          .perform(get("/api/admin/messages"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.messages.length()").value(0))
          .andExpect(jsonPath("$.total").value(0))
          .andExpect(jsonPath("$.limit").value(50))
          .andExpect(jsonPath("$.offset").value(0));
    }
  }
}
