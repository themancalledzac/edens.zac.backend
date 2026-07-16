package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.model.DownloadResolution;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Produces short-lived, self-authenticating S3 presigned GET URLs for downloads. Download endpoints
 * redirect (302) to these instead of streaming bytes through the response.
 *
 * <p><strong>Why:</strong> the frontend runs on AWS Amplify Web Compute, which caps any HTTP
 * response proxied through its Next.js BFF at 5.72 MB — a single full-resolution JPEG, or any
 * multi-image ZIP, blows past that and the request dies at the CloudFront/compute layer (the
 * client-reported {@code 413}). A 302 to a presigned URL is a few hundred bytes, so it passes the
 * cap; the browser then pulls the actual bytes straight from S3, which has no such limit.
 *
 * <p>For a multi-image download we can't presign a not-yet-existing archive, so we build the ZIP
 * into a temporary S3 object (streamed via {@link S3MultipartOutputStream}, memory-flat) and
 * presign that. Temp objects live under {@link #TMP_PREFIX} and are reaped by an S3 lifecycle rule.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadUrlService {

  /** Presigned URL lifetime — long enough for the browser to follow the redirect and finish. */
  private static final Duration URL_TTL = Duration.ofMinutes(15);

  /**
   * Key prefix for generated ZIP archives; a bucket lifecycle rule expires these (see terraform).
   */
  static final String TMP_PREFIX = "downloads-tmp/";

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;

  @Value("${aws.portfolio.s3.bucket}")
  private String bucketName;

  /**
   * Presign a GET for an existing S3 object, forcing a browser download with the given filename and
   * content type.
   */
  public URI presignObject(String s3Key, String contentType, String filename) {
    GetObjectRequest getObject =
        GetObjectRequest.builder()
            .bucket(bucketName)
            .key(s3Key)
            .responseContentType(contentType)
            .responseContentDisposition(contentDisposition(filename))
            .build();
    GetObjectPresignRequest presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(URL_TTL)
            .getObjectRequest(getObject)
            .build();
    return URI.create(s3Presigner.presignGetObject(presignRequest).url().toString());
  }

  /**
   * Build a ZIP of {@code entries} into a temporary S3 object and return a presigned URL to it. The
   * archive is streamed to S3 part-by-part, so peak memory is one ~5 MB part regardless of gallery
   * size. A per-image S3 failure is written into the ZIP as a small {@code .error.txt} placeholder
   * rather than aborting the whole download.
   */
  public URI zipToS3AndPresign(List<DownloadResolution> entries, String zipFilename)
      throws IOException {
    String key = TMP_PREFIX + UUID.randomUUID() + "/" + zipFilename;
    S3MultipartOutputStream s3Out = new S3MultipartOutputStream(s3Client, bucketName, key);
    try {
      ZipOutputStream zos = new ZipOutputStream(s3Out);
      writeZipEntries(zos, entries);
      zos.finish();
      zos.close(); // completes the multipart upload via S3MultipartOutputStream.close()
    } catch (IOException | RuntimeException e) {
      s3Out.abort(); // never complete a truncated archive
      throw e;
    }
    log.info("Built ZIP download to S3 (key={}, count={})", key, entries.size());
    return presignObject(key, "application/zip", zipFilename);
  }

  private void writeZipEntries(ZipOutputStream zos, List<DownloadResolution> entries)
      throws IOException {
    int seq = 0;
    for (DownloadResolution entry : entries) {
      // S3 keys are unique but original_filenames are not — prefix with a sequence number so
      // ZipOutputStream never throws on duplicate entry names.
      GetObjectRequest req =
          GetObjectRequest.builder().bucket(bucketName).key(entry.s3Key()).build();
      try (ResponseInputStream<GetObjectResponse> in = s3Client.getObject(req)) {
        zos.putNextEntry(new ZipEntry(String.format("%03d_%s", seq++, entry.filename())));
        in.transferTo(zos);
        zos.closeEntry();
      } catch (SdkException e) {
        // Per-image failure must not corrupt the rest of the ZIP: write a placeholder so the
        // recipient sees something is missing without tearing down the whole download.
        log.warn(
            "Failed to fetch S3 object for ZIP entry (key={}): {}", entry.s3Key(), e.getMessage());
        zos.putNextEntry(new ZipEntry(String.format("%03d_%s.error.txt", seq++, entry.filename())));
        zos.write(
            ("Could not include this image: " + e.getClass().getSimpleName())
                .getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
      }
    }
  }

  private static String contentDisposition(String filename) {
    return "attachment; filename=\"" + filename + "\"";
  }
}
