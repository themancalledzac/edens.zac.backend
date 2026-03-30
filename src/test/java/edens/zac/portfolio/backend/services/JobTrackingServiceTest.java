package edens.zac.portfolio.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobTrackingServiceTest {

  @Test
  void createJob_returnsJobWithPendingStatus() {
    var service = new JobTrackingService();
    var job = service.createJob(35);
    assertNotNull(job.jobId());
    assertEquals("PENDING", job.status());
    assertEquals(35, job.totalFiles());
  }

  @Test
  void getJob_returnsSnapshotOfCurrentState() {
    var service = new JobTrackingService();
    var job = service.createJob(10);
    job.processed().incrementAndGet();
    job.created().incrementAndGet();

    var response = service.getJob(job.jobId());
    assertTrue(response.isPresent());
    assertEquals(1, response.get().processed());
    assertEquals(1, response.get().created());
  }

  @Test
  void getJob_returnsEmptyForUnknownId() {
    var service = new JobTrackingService();
    assertTrue(service.getJob(UUID.randomUUID()).isEmpty());
  }

  @Test
  void markProcessing_updatesStatus() {
    var service = new JobTrackingService();
    var job = service.createJob(5);
    job.markProcessing();

    var response = service.getJob(job.jobId());
    assertTrue(response.isPresent());
    assertEquals("PROCESSING", response.get().status());
  }

  @Test
  void markCompleted_setsCompletedStatus() {
    var service = new JobTrackingService();
    var job = service.createJob(5);
    job.markProcessing();
    job.markCompleted();

    var response = service.getJob(job.jobId());
    assertTrue(response.isPresent());
    assertEquals("COMPLETED", response.get().status());
  }

  @Test
  void markCompleted_setsFailedStatusWhenErrorsExist() {
    var service = new JobTrackingService();
    var job = service.createJob(5);
    job.markProcessing();
    job.errors().add("some error");
    job.markCompleted();

    var response = service.getJob(job.jobId());
    assertTrue(response.isPresent());
    assertEquals("FAILED", response.get().status());
    assertEquals(1, response.get().errors().size());
  }
}
