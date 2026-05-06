package edens.zac.portfolio.backend.model;

/**
 * Resolved download target for a single image. Carries everything the controller needs to stream
 * the response after the service layer has decided which S3 object to serve and what
 * Content-Type/filename to use.
 *
 * @param s3Key resolved S3 object key (already extracted from the CloudFront URL)
 * @param extension canonical file extension including the dot (e.g. {@code .jpg}, {@code .webp})
 * @param contentType MIME type to set on the HTTP response
 * @param filename sanitized {@code Content-Disposition} filename
 */
public record DownloadResolution(
    String s3Key, String extension, String contentType, String filename) {}
