package edens.zac.portfolio.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;

import org.springframework.context.annotation.Configuration;

import java.util.Iterator;

@Configuration
@Slf4j
class ImageIoConfig {

    @PostConstruct
    void registerPlugins() {
        // Ensure all ImageIO plugins on the classpath (e.g., WebP) are discovered,
        // especially important in packaged/Docker environments.
        ImageIO.scanForPlugins();

        // Diagnostics to confirm WebP availability at startup
        String os = System.getProperty("os.name");
        String arch = System.getProperty("os.arch");
        log.info("ImageIO environment: OS='{}', Arch='{}'", os, arch);

        boolean hasWebpReader = false;
        Iterator<ImageReader> webpReaders = ImageIO.getImageReadersByFormatName("webp");
        while (webpReaders.hasNext()) {
            ImageReader r = webpReaders.next();
            hasWebpReader = true;
            log.info("Discovered WebP reader: {}", r.getClass().getName());
        }
        if (!hasWebpReader) {
            log.warn("No ImageIO WebP reader found. WebP inputs will fail to decode.");
        }

        boolean hasWebpWriter = false;
        Iterator<ImageWriter> webpWriters = ImageIO.getImageWritersByFormatName("webp");
        while (webpWriters.hasNext()) {
            ImageWriter w = webpWriters.next();
            hasWebpWriter = true;
            log.info("Discovered WebP writer: {}", w.getClass().getName());
        }
        if (!hasWebpWriter) {
            log.warn("No ImageIO WebP writer found. WebP outputs may fail.");
        }
    }
}
