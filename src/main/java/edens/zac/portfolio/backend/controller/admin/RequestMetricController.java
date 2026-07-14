package edens.zac.portfolio.backend.controller.admin;

import edens.zac.portfolio.backend.dao.RequestMetricRepository;
import edens.zac.portfolio.backend.dao.RequestMetricRepository.RequestMetricRow;
import edens.zac.portfolio.backend.model.RequestMetrics;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin REST controller for reading the private, aggregate-only request metrics.
 *
 * <p>Runs in dev and prod (no {@code @Profile} gating). Authorization is enforced by {@link
 * edens.zac.portfolio.backend.config.SecurityConfig}, which gates {@code /api/admin/**} behind
 * {@code hasRole("ADMIN")} (the {@code app.admin.enforce-authz} toggle, on by default). In prod
 * this also sits inside the {@link edens.zac.portfolio.backend.config.InternalSecretFilter}
 * perimeter. The returned rows carry only aggregate counts — no PII.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/metrics/requests")
public class RequestMetricController {

  private static final int DEFAULT_RANGE_DAYS = 30;

  private final RequestMetricRepository requestMetricRepository;

  /**
   * List aggregated request counts for the inclusive {@code [from, to]} day range. Missing bounds
   * default to the last {@value #DEFAULT_RANGE_DAYS} days ending today; an inverted range is
   * normalized by swapping the bounds.
   */
  @GetMapping
  public ResponseEntity<RequestMetrics.RequestMetricList> list(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to) {
    LocalDate resolvedTo = to != null ? to : LocalDate.now(ZoneOffset.UTC);
    LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(DEFAULT_RANGE_DAYS);
    if (resolvedFrom.isAfter(resolvedTo)) {
      LocalDate swap = resolvedFrom;
      resolvedFrom = resolvedTo;
      resolvedTo = swap;
    }

    List<RequestMetricRow> rows = requestMetricRepository.findByDayRange(resolvedFrom, resolvedTo);
    List<RequestMetrics.RequestMetricView> view =
        rows.stream()
            .map(r -> new RequestMetrics.RequestMetricView(r.day(), r.route(), r.slug(), r.count()))
            .toList();
    long total = view.stream().mapToLong(RequestMetrics.RequestMetricView::count).sum();

    return ResponseEntity.ok(
        new RequestMetrics.RequestMetricList(view, resolvedFrom, resolvedTo, total));
  }
}
