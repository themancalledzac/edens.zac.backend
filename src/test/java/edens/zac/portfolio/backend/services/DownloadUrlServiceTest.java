package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.model.DownloadResolution;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/** Unit tests for {@link DownloadUrlService}. */
@ExtendWith(MockitoExtension.class)
class DownloadUrlServiceTest {

  private static final String BUCKET = "portfolio-bucket";

  @Mock private S3Client s3Client;
  @Mock private S3Presigner s3Presigner;
  @Mock private PresignedGetObjectRequest presigned;

  @InjectMocks private DownloadUrlService service;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "bucketName", BUCKET);
  }

  private void stubPresign(String url) throws Exception {
    when(presigned.url()).thenReturn(URI.create(url).toURL());
    when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
  }

  private static ResponseInputStream<GetObjectResponse> fakeS3Stream(byte[] bytes) {
    return new ResponseInputStream<>(
        GetObjectResponse.builder().contentLength((long) bytes.length).build(),
        AbortableInputStream.create(new ByteArrayInputStream(bytes)));
  }

  @Test
  void presignObject_setsBucketKeyDispositionAndContentType() throws Exception {
    stubPresign("https://portfolio-bucket.s3.amazonaws.com/Image/Original/x.jpg?sig=abc");

    URI result = service.presignObject("Image/Original/x.jpg", "image/jpeg", "sunset.jpg");

    assertThat(result.toString()).startsWith("https://portfolio-bucket.s3.amazonaws.com/");

    ArgumentCaptor<GetObjectPresignRequest> captor =
        ArgumentCaptor.forClass(GetObjectPresignRequest.class);
    verify(s3Presigner).presignGetObject(captor.capture());
    GetObjectRequest get = captor.getValue().getObjectRequest();
    assertThat(get.bucket()).isEqualTo(BUCKET);
    assertThat(get.key()).isEqualTo("Image/Original/x.jpg");
    assertThat(get.responseContentType()).isEqualTo("image/jpeg");
    assertThat(get.responseContentDisposition()).isEqualTo("attachment; filename=\"sunset.jpg\"");
    assertThat(captor.getValue().signatureDuration().toMinutes()).isEqualTo(15);
  }

  @Test
  @SuppressWarnings("unchecked")
  void zipToS3AndPresign_streamsEachEntryAndPresignsTempKey() throws Exception {
    // Multipart plumbing for the streamed ZIP.
    when(s3Client.createMultipartUpload(any(Consumer.class)))
        .thenReturn(CreateMultipartUploadResponse.builder().uploadId("up-1").build());
    when(s3Client.uploadPart(any(Consumer.class), any(RequestBody.class)))
        .thenReturn(UploadPartResponse.builder().eTag("etag-1").build());
    when(s3Client.completeMultipartUpload(any(Consumer.class)))
        .thenReturn(CompleteMultipartUploadResponse.builder().build());
    // One object stream per entry.
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(fakeS3Stream("first-bytes".getBytes()))
        .thenReturn(fakeS3Stream("second-bytes".getBytes()));
    stubPresign("https://portfolio-bucket.s3.amazonaws.com/downloads-tmp/zzz/gallery.zip?sig=abc");

    List<DownloadResolution> entries =
        List.of(
            new DownloadResolution("Image/Original/a.jpg", ".jpg", "image/jpeg", "a.jpg"),
            new DownloadResolution("Image/Original/b.jpg", ".jpg", "image/jpeg", "b.jpg"));

    URI result = service.zipToS3AndPresign(entries, "gallery.zip");

    assertThat(result.toString()).contains("downloads-tmp/");
    // Each entry's object was fetched from S3.
    verify(s3Client, times(2)).getObject(any(GetObjectRequest.class));
    // The ZIP was completed (streamed to S3) and then presigned under the temp prefix.
    verify(s3Client).completeMultipartUpload(any(Consumer.class));

    ArgumentCaptor<GetObjectPresignRequest> captor =
        ArgumentCaptor.forClass(GetObjectPresignRequest.class);
    verify(s3Presigner).presignGetObject(captor.capture());
    GetObjectRequest get = captor.getValue().getObjectRequest();
    assertThat(get.key()).startsWith(DownloadUrlService.TMP_PREFIX).endsWith("/gallery.zip");
    assertThat(get.responseContentType()).isEqualTo("application/zip");
    assertThat(get.responseContentDisposition()).isEqualTo("attachment; filename=\"gallery.zip\"");
  }
}
