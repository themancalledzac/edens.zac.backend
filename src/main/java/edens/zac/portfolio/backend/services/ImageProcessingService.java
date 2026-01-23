// package edens.zac.portfolio.backend.services;
//
// import lombok.extern.slf4j.Slf4j;
// import net.coobird.thumbnailator.Thumbnails;
// import org.springframework.stereotype.Service;
// import org.springframework.web.multipart.MultipartFile;
//
// import javax.imageio.ImageIO;
// import java.awt.image.BufferedImage;
// import java.io.ByteArrayOutputStream;
// import java.io.IOException;
//
// @Service
// @Slf4j
// public class ImageProcessingService {
//
//    private static final int THUMBNAIL_MAX_SIZE = 500;
//
//    public byte[] createThumbnail(MultipartFile originalFile) throws IOException {
//        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
//
//            // read original image
//            BufferedImage originalImage = ImageIO.read(originalFile.getInputStream());
//
//            // calculate new dimensions, while maintaining aspect ratio
//            int originalWidth = originalImage.getWidth();
//            int originalHeight = originalImage.getHeight();
//
//            double scale = (double) THUMBNAIL_MAX_SIZE / Math.max(originalWidth, originalHeight);
//            int targetWidth = (int) (originalWidth * scale);
//            int targetHeight = (int) (originalHeight * scale);
//
//            // create thumbnail
//            Thumbnails.of(originalImage)
//                    .size(targetWidth, targetHeight)
//                    .outputQuality(0.85)
//                    .outputFormat("webp")
//                    .toOutputStream(outputStream);
//
//            return outputStream.toByteArray();
//        }
//    }
// }
