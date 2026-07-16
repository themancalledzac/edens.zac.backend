package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

/** Unit tests for the streaming multipart-upload {@link java.io.OutputStream}. */
@ExtendWith(MockitoExtension.class)
class S3MultipartOutputStreamTest {

  private static final int PART_SIZE = 5 * 1024 * 1024;

  @Mock private S3Client s3;

  /** Concatenation of every part body, in upload order — should equal everything written. */
  private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

  private int uploadPartCalls = 0;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    when(s3.createMultipartUpload(any(Consumer.class)))
        .thenReturn(CreateMultipartUploadResponse.builder().uploadId("up-1").build());
    lenient()
        .when(s3.uploadPart(any(Consumer.class), any(RequestBody.class)))
        .thenAnswer(
            inv -> {
              RequestBody body = inv.getArgument(1);
              captured.write(body.contentStreamProvider().newStream().readAllBytes());
              uploadPartCalls++;
              return UploadPartResponse.builder().eTag("etag-" + uploadPartCalls).build();
            });
    lenient()
        .when(s3.completeMultipartUpload(any(Consumer.class)))
        .thenReturn(CompleteMultipartUploadResponse.builder().build());
  }

  private byte[] pattern(int n) {
    byte[] b = new byte[n];
    for (int i = 0; i < n; i++) {
      b[i] = (byte) (i % 251); // 251 is prime → no alignment with part boundaries
    }
    return b;
  }

  @Test
  @SuppressWarnings("unchecked")
  void splitsIntoFiveMbPartsAndPreservesBytes() throws IOException {
    byte[] data = pattern(2 * PART_SIZE + 123); // two full parts + a small tail
    try (S3MultipartOutputStream out = new S3MultipartOutputStream(s3, "bucket", "key")) {
      // Write in oddly-sized chunks to exercise the buffer-fill boundary logic.
      int off = 0;
      int[] chunks = {1, 7, 4096, 3 * 1024 * 1024, PART_SIZE, 999};
      for (int c : chunks) {
        int len = Math.min(c, data.length - off);
        out.write(data, off, len);
        off += len;
      }
      out.write(data, off, data.length - off);
    }
    assertThat(uploadPartCalls).isEqualTo(3); // 5MB + 5MB + tail
    assertThat(captured.toByteArray()).isEqualTo(data);
    verify(s3, times(1)).completeMultipartUpload(any(Consumer.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void singleSmallPart_completesWithOnePart() throws IOException {
    byte[] data = pattern(1234);
    try (S3MultipartOutputStream out = new S3MultipartOutputStream(s3, "bucket", "key")) {
      out.write(data);
    }
    assertThat(uploadPartCalls).isEqualTo(1);
    assertThat(captured.toByteArray()).isEqualTo(data);
    verify(s3, times(1)).completeMultipartUpload(any(Consumer.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void exactMultipleOfPartSize_noEmptyTrailingPart() throws IOException {
    byte[] data = pattern(2 * PART_SIZE);
    try (S3MultipartOutputStream out = new S3MultipartOutputStream(s3, "bucket", "key")) {
      out.write(data);
    }
    assertThat(uploadPartCalls).isEqualTo(2);
    assertThat(captured.toByteArray()).isEqualTo(data);
    verify(s3, times(1)).completeMultipartUpload(any(Consumer.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void abort_callsAbortMultipartUpload() {
    S3MultipartOutputStream out = new S3MultipartOutputStream(s3, "bucket", "key");
    out.abort();
    verify(s3).abortMultipartUpload(any(Consumer.class));
    verify(s3, never()).completeMultipartUpload(any(Consumer.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void close_whenCompleteFails_abortsAndRethrows() {
    when(s3.completeMultipartUpload(any(Consumer.class)))
        .thenThrow(AwsServiceException.builder().message("boom").build());

    S3MultipartOutputStream out = new S3MultipartOutputStream(s3, "bucket", "key");
    out.write(pattern(10), 0, 10);

    assertThatThrownBy(out::close).isInstanceOf(AwsServiceException.class);
    verify(s3).abortMultipartUpload(any(Consumer.class));
  }
}
