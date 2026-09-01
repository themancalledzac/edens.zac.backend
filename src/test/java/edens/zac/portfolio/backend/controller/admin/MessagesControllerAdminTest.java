package edens.zac.portfolio.backend.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.dao.MessageRepository;
import edens.zac.portfolio.backend.entity.MessageEntity;
import edens.zac.portfolio.backend.services.MessageService;
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

  @Mock private MessageService messageService;

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
      when(messageRepository.findAll(50, 0, null, null))
          .thenReturn(
              List.of(
                  sampleMessage(2L, "two@example.com", "two"),
                  sampleMessage(1L, "one@example.com", "one")));
      when(messageRepository.count(null, null)).thenReturn(2L);

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
      when(messageRepository.findAll(50, 0, null, null)).thenReturn(List.of());
      when(messageRepository.count(null, null)).thenReturn(0L);

      mockMvc
          .perform(get("/api/admin/messages"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.limit").value(50))
          .andExpect(jsonPath("$.offset").value(0));

      verify(messageRepository).findAll(50, 0, null, null);
    }

    @Test
    void clampsExcessiveLimitTo200() throws Exception {
      when(messageRepository.findAll(200, 0, null, null)).thenReturn(List.of());
      when(messageRepository.count(null, null)).thenReturn(0L);

      mockMvc
          .perform(get("/api/admin/messages").param("limit", "999999"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.limit").value(200));

      ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
      verify(messageRepository).findAll(limitCaptor.capture(), anyInt(), any(), any());
      org.assertj.core.api.Assertions.assertThat(limitCaptor.getValue()).isEqualTo(200);
    }

    @Test
    void clampsNegativeOffsetToZero() throws Exception {
      when(messageRepository.findAll(50, 0, null, null)).thenReturn(List.of());
      when(messageRepository.count(null, null)).thenReturn(0L);

      mockMvc
          .perform(get("/api/admin/messages").param("offset", "-5"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.offset").value(0));

      verify(messageRepository).findAll(50, 0, null, null);
    }

    @Test
    void emptyRepoReturnsEmptyShape() throws Exception {
      when(messageRepository.findAll(50, 0, null, null)).thenReturn(List.of());
      when(messageRepository.count(null, null)).thenReturn(0L);

      mockMvc
          .perform(get("/api/admin/messages"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.messages.length()").value(0))
          .andExpect(jsonPath("$.total").value(0))
          .andExpect(jsonPath("$.limit").value(50))
          .andExpect(jsonPath("$.offset").value(0));
    }
  }

  @Nested
  class DeleteMessage {

    @Test
    void delete_existingId_returns204() throws Exception {
      when(messageService.delete(7L)).thenReturn(1);

      mockMvc.perform(delete("/api/admin/messages/7")).andExpect(status().isNoContent());

      verify(messageService).delete(7L);
    }

    @Test
    void delete_missingId_returns404() throws Exception {
      // Zero rows affected means there was nothing to delete. A 204 here told the caller the
      // delete succeeded on an id that never existed.
      when(messageService.delete(404L)).thenReturn(0);

      mockMvc.perform(delete("/api/admin/messages/404")).andExpect(status().isNotFound());

      verify(messageService).delete(404L);
    }
  }

  @Nested
  class Filters {

    @Test
    void passesUnreadAndQueryStraightThrough() throws Exception {
      when(messageRepository.findAll(50, 0, true, "wedding")).thenReturn(List.of());
      when(messageRepository.count(true, "wedding")).thenReturn(0L);

      mockMvc
          .perform(get("/api/admin/messages").param("unread", "true").param("q", "wedding"))
          .andExpect(status().isOk());

      verify(messageRepository).findAll(50, 0, true, "wedding");
    }

    @Test
    void countsTheSameFilteredSetAsThePage() throws Exception {
      // The admin list prints "N of M". Counting unfiltered while paging filtered would make M a
      // number about a different row set, which reads as a bug in the filter rather than in the
      // count.
      when(messageRepository.findAll(50, 0, true, null))
          .thenReturn(List.of(sampleMessage(1L, "one@example.com", "one")));
      when(messageRepository.count(true, null)).thenReturn(1L);

      mockMvc
          .perform(get("/api/admin/messages").param("unread", "true"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total").value(1));

      verify(messageRepository).count(true, null);
    }

    @Test
    void readAtIsNullOnAnUnreadMessage() throws Exception {
      when(messageRepository.findAll(50, 0, null, null))
          .thenReturn(List.of(sampleMessage(1L, "one@example.com", "one")));
      when(messageRepository.count(null, null)).thenReturn(1L);

      mockMvc
          .perform(get("/api/admin/messages"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.messages[0].readAt").doesNotExist());
    }
  }

  @Nested
  class MarkRead {

    @Test
    void noBodyMeansMarkRead() throws Exception {
      when(messageService.markRead(7L, true)).thenReturn(1);

      mockMvc.perform(patch("/api/admin/messages/7/read")).andExpect(status().isNoContent());

      verify(messageService).markRead(7L, true);
    }

    @Test
    void readFalseMarksUnread() throws Exception {
      when(messageService.markRead(7L, false)).thenReturn(1);

      mockMvc
          .perform(
              patch("/api/admin/messages/7/read")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"read\":false}"))
          .andExpect(status().isNoContent());

      verify(messageService).markRead(7L, false);
    }

    @Test
    void missingIdReturns404() throws Exception {
      // Same rule as delete: zero rows affected means the id never existed, and a 204 would tell
      // the caller a message it cannot see was just marked read.
      when(messageService.markRead(404L, true)).thenReturn(0);

      mockMvc.perform(patch("/api/admin/messages/404/read")).andExpect(status().isNotFound());

      verify(messageService).markRead(404L, true);
    }
  }
}
