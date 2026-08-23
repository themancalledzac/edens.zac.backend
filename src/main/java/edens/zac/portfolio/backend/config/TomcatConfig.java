package edens.zac.portfolio.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Connector tuning for the large batch upload path.
 *
 * <p>{@code maxSwallowSize} caps how much of an aborted or rejected request body Tomcat reads and
 * discards to keep the connection reusable. It is held at or above the multipart ceiling ({@code
 * spring.servlet.multipart.max-request-size=2GB}) so a rejected upload still receives its error
 * response instead of a connection reset part-way through the body.
 *
 * <p>The value is {@link Integer#MAX_VALUE} rather than {@code 2 * 1024 * 1024 * 1024}. The setter
 * takes an int, and that expression is int arithmetic that overflows to -2147483648. Every Tomcat
 * guard on this setting reads {@code maxSwallowSize > -1}, so a negative value switched the cap off
 * altogether rather than raising it.
 *
 * <p>The connection timeout is sized for those same large batch uploads.
 */
@Configuration
@Slf4j
public class TomcatConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

  private static final int MAX_SWALLOW_SIZE_BYTES = Integer.MAX_VALUE;

  private static final int CONNECTION_TIMEOUT_MS = 300_000;

  @Override
  public void customize(TomcatServletWebServerFactory factory) {
    factory.addConnectorCustomizers(
        connector -> {
          Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
          protocol.setMaxSwallowSize(MAX_SWALLOW_SIZE_BYTES);
          protocol.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
          log.info(
              "Configured Tomcat: maxSwallowSize={} bytes, connectionTimeout={} ms",
              MAX_SWALLOW_SIZE_BYTES,
              CONNECTION_TIMEOUT_MS);
        });
  }
}
