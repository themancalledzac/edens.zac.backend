package edens.zac.portfolio.backend.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Response shapes for the private, admin-only request-metrics endpoint. Carries only aggregate
 * counts per {@code (day, route, slug)} — no PII of any kind.
 */
public final class RequestMetrics {

  private RequestMetrics() {}

  /** A single aggregated request-count row. {@code slug} is {@code null} for slug-less routes. */
  public record RequestMetricView(LocalDate day, String route, String slug, long count) {}

  /**
   * The aggregated rows for the queried inclusive {@code [from, to]} range, plus the resolved range
   * bounds so the caller can confirm the defaults that were applied.
   */
  public record RequestMetricList(
      List<RequestMetricView> metrics, LocalDate from, LocalDate to, long total) {}
}
