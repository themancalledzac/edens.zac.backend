package edens.zac.portfolio.backend.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-memory job tracker for background disk upload processing. Thread-safe via ConcurrentHashMap
 * and AtomicInteger counters. Jobs auto-expire after 1 hour.
 */
@Component
@Slf4j
public class JobTrackingService {

  private final ConcurrentHashMap<UUID, JobStatus> jobs = new ConcurrentHashMap<>();

  /** Mutable job status with thread-safe counters. */
  public static class JobStatus {
    private final UUID jobId;
    private volatile String status; // PENDING, PROCESSING, COMPLETED, FAILED
    private final int totalFiles;
    private final AtomicInteger processed = new AtomicInteger(0);
    private final AtomicInteger created = new AtomicInteger(0);
    private final AtomicInteger updated = new AtomicInteger(0);
    private final AtomicInteger skipped = new AtomicInteger(0);
    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final LocalDateTime startedAt;
    private volatile LocalDateTime completedAt;

    JobStatus(UUID jobId, int totalFiles) {
      this.jobId = jobId;
      this.status = "PENDING";
      this.totalFiles = totalFiles;
      this.startedAt = LocalDateTime.now();
    }

    public UUID jobId() {
      return jobId;
    }

    public String status() {
      return status;
    }

    public int totalFiles() {
      return totalFiles;
    }

    public AtomicInteger processed() {
      return processed;
    }

    public AtomicInteger created() {
      return created;
    }

    public AtomicInteger updated() {
      return updated;
    }

    public AtomicInteger skipped() {
      return skipped;
    }

    public List<String> errors() {
      return errors;
    }

    public LocalDateTime startedAt() {
      return startedAt;
    }

    public void markProcessing() {
      this.status = "PROCESSING";
    }

    public void markCompleted() {
      this.status = errors.isEmpty() ? "COMPLETED" : "FAILED";
      this.completedAt = LocalDateTime.now();
    }
  }

  /** Response DTO -- snapshot of current job state with plain int fields. */
  public record JobStatusResponse(
      UUID jobId,
      String status,
      int totalFiles,
      int processed,
      int created,
      int updated,
      int skipped,
      List<String> errors) {}

  /**
   * Create a new job with PENDING status.
   *
   * @param totalFiles Number of files to process
   * @return The new JobStatus instance
   */
  public JobStatus createJob(int totalFiles) {
    var jobId = UUID.randomUUID();
    var job = new JobStatus(jobId, totalFiles);
    jobs.put(jobId, job);
    return job;
  }

  /**
   * Get a snapshot of the current job state.
   *
   * @param jobId The job ID to look up
   * @return The job status response, or empty if not found
   */
  public Optional<JobStatusResponse> getJob(UUID jobId) {
    var job = jobs.get(jobId);
    if (job == null) {
      return Optional.empty();
    }
    return Optional.of(toResponse(job));
  }

  /** Clean up jobs older than 1 hour. Runs every 10 minutes. */
  @Scheduled(fixedRate = 600_000)
  public void cleanupExpiredJobs() {
    var cutoff = LocalDateTime.now().minusHours(1);
    int removed = 0;
    var it = jobs.entrySet().iterator();
    while (it.hasNext()) {
      if (it.next().getValue().startedAt().isBefore(cutoff)) {
        it.remove();
        removed++;
      }
    }
    if (removed > 0) {
      log.debug("Cleaned up {} expired jobs", removed);
    }
  }

  private JobStatusResponse toResponse(JobStatus job) {
    return new JobStatusResponse(
        job.jobId(),
        job.status(),
        job.totalFiles(),
        job.processed().get(),
        job.created().get(),
        job.updated().get(),
        job.skipped().get(),
        List.copyOf(job.errors()));
  }
}
