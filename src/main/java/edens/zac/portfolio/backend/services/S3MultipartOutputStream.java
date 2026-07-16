package edens.zac.portfolio.backend.services;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;

/**
 * An {@link OutputStream} that streams whatever is written to it straight into an S3 object via a
 * multipart upload, holding only one part (~5 MB) in memory at a time. This lets us build a
 * collection ZIP of arbitrary size (a full-resolution "download all") into S3 without buffering the
 * whole archive on disk or in heap.
 *
 * <p>Contract details that matter for correctness:
 *
 * <ul>
 *   <li>S3 requires every part except the last to be at least 5 MB. We flush a part only when the
 *       buffer is completely full (exactly {@link #PART_SIZE}), so every non-final part is exactly
 *       5 MB and the trailing part (flushed on {@link #close()}) is whatever remains.
 *   <li>The stream is single-threaded / not thread-safe — one download builds one ZIP.
 *   <li>{@link #close()} completes the upload. If any write or the completion fails, the multipart
 *       upload is aborted so S3 doesn't retain orphaned parts (which would otherwise accrue storage
 *       cost until a lifecycle rule reaps them).
 * </ul>
 */
@Slf4j
public class S3MultipartOutputStream extends OutputStream {

  /** 5 MiB — the S3 minimum part size for all but the final part. */
  private static final int PART_SIZE = 5 * 1024 * 1024;

  private final S3Client s3Client;
  private final String bucket;
  private final String key;
  private final String uploadId;
  private final List<CompletedPart> completedParts = new ArrayList<>();

  private final byte[] buffer = new byte[PART_SIZE];
  private int bufferLen = 0;
  private int partNumber = 1;
  private boolean closed = false;
  private boolean aborted = false;

  /** Opens the multipart upload immediately so writes can begin streaming parts. */
  public S3MultipartOutputStream(S3Client s3Client, String bucket, String key) {
    this.s3Client = s3Client;
    this.bucket = bucket;
    this.key = key;
    this.uploadId = s3Client.createMultipartUpload(b -> b.bucket(bucket).key(key)).uploadId();
  }

  @Override
  public void write(int b) {
    buffer[bufferLen++] = (byte) b;
    if (bufferLen == PART_SIZE) {
      uploadBufferedPart();
    }
  }

  @Override
  public void write(byte[] src, int off, int len) {
    int remaining = len;
    int pos = off;
    while (remaining > 0) {
      int chunk = Math.min(remaining, PART_SIZE - bufferLen);
      System.arraycopy(src, pos, buffer, bufferLen, chunk);
      bufferLen += chunk;
      pos += chunk;
      remaining -= chunk;
      if (bufferLen == PART_SIZE) {
        uploadBufferedPart();
      }
    }
  }

  /** Uploads the current buffer as the next part (full 5 MB parts, plus the final part). */
  private void uploadBufferedPart() {
    final int number = partNumber;
    String etag =
        s3Client
            .uploadPart(
                b -> b.bucket(bucket).key(key).uploadId(uploadId).partNumber(number),
                RequestBody.fromBytes(Arrays.copyOf(buffer, bufferLen)))
            .eTag();
    completedParts.add(CompletedPart.builder().partNumber(number).eTag(etag).build());
    partNumber++;
    bufferLen = 0;
  }

  /** Aborts the multipart upload, best-effort. Safe to call more than once. */
  public void abort() {
    if (aborted) {
      return;
    }
    aborted = true;
    try {
      s3Client.abortMultipartUpload(b -> b.bucket(bucket).key(key).uploadId(uploadId));
    } catch (RuntimeException e) {
      log.warn(
          "Failed to abort multipart upload (bucket={}, key={}): {}", bucket, key, e.getMessage());
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    try {
      if (bufferLen > 0) {
        uploadBufferedPart();
      }
      // A well-formed ZIP always has an end-of-central-directory record, so completedParts is never
      // empty in practice. Guard anyway: an empty multipart upload cannot be completed.
      if (completedParts.isEmpty()) {
        abort();
        return;
      }
      CompletedMultipartUpload completed =
          CompletedMultipartUpload.builder().parts(completedParts).build();
      s3Client.completeMultipartUpload(
          b -> b.bucket(bucket).key(key).uploadId(uploadId).multipartUpload(completed));
    } catch (RuntimeException e) {
      abort();
      throw e;
    }
  }
}
