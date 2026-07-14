package edens.zac.portfolio.backend.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.dao.RequestMetricRepository;
import edens.zac.portfolio.backend.dao.RequestMetricRepository.RequestMetricRow;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RequestMetricControllerTest {

  private MockMvc mockMvc;

  @Mock private RequestMetricRepository repository;

  @InjectMocks private RequestMetricController controller;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Nested
  class ListMetrics {

    @Test
    void returns200WithAggregatedRowsAndTotal() throws Exception {
      when(repository.findByDayRange(any(LocalDate.class), any(LocalDate.class)))
          .thenReturn(
              List.of(
                  new RequestMetricRow(
                      LocalDate.of(2026, 7, 6), "/api/read/collections/{slug}", "iceland", 40L),
                  new RequestMetricRow(
                      LocalDate.of(2026, 7, 6), "/api/read/collections", null, 2L)));

      mockMvc
          .perform(
              get("/api/admin/metrics/requests")
                  .param("from", "2026-07-01")
                  .param("to", "2026-07-07"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.metrics.length()").value(2))
          .andExpect(jsonPath("$.metrics[0].route").value("/api/read/collections/{slug}"))
          .andExpect(jsonPath("$.metrics[0].slug").value("iceland"))
          .andExpect(jsonPath("$.metrics[0].count").value(40))
          .andExpect(jsonPath("$.metrics[1].slug").doesNotExist())
          .andExpect(jsonPath("$.total").value(42));

      verify(repository).findByDayRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7));
    }

    @Test
    void responseCarriesNoPiiFields() throws Exception {
      when(repository.findByDayRange(any(LocalDate.class), any(LocalDate.class)))
          .thenReturn(
              List.of(
                  new RequestMetricRow(
                      LocalDate.of(2026, 7, 6), "/api/read/collections", null, 5L)));

      String body =
          mockMvc
              .perform(get("/api/admin/metrics/requests"))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // The whole point: aggregate shape only, never a per-request identity signal.
      assertThat(body).doesNotContainIgnoringCase("ip");
      assertThat(body).doesNotContainIgnoringCase("userAgent");
      assertThat(body).doesNotContainIgnoringCase("user_agent");
      assertThat(body).doesNotContainIgnoringCase("userId");
      assertThat(body).doesNotContainIgnoringCase("email");
    }

    @Test
    void defaultsToLast30DaysWhenBoundsAbsent() throws Exception {
      when(repository.findByDayRange(any(LocalDate.class), any(LocalDate.class)))
          .thenReturn(List.of());

      mockMvc.perform(get("/api/admin/metrics/requests")).andExpect(status().isOk());

      ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
      ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
      verify(repository).findByDayRange(fromCaptor.capture(), toCaptor.capture());
      assertThat(fromCaptor.getValue()).isEqualTo(toCaptor.getValue().minusDays(30));
    }

    @Test
    void normalizesInvertedRangeBySwapping() throws Exception {
      when(repository.findByDayRange(any(LocalDate.class), any(LocalDate.class)))
          .thenReturn(List.of());

      mockMvc
          .perform(
              get("/api/admin/metrics/requests")
                  .param("from", "2026-07-07")
                  .param("to", "2026-07-01"))
          .andExpect(status().isOk());

      // The controller normalizes the inverted range before querying.
      verify(repository).findByDayRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7));
    }

    @Test
    void emptyRepoReturnsZeroTotalAndEmptyList() throws Exception {
      when(repository.findByDayRange(any(LocalDate.class), any(LocalDate.class)))
          .thenReturn(List.of());

      mockMvc
          .perform(get("/api/admin/metrics/requests"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.metrics.length()").value(0))
          .andExpect(jsonPath("$.total").value(0));
    }
  }
}
