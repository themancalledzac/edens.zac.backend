package edens.zac.portfolio.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class TomcatConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addConnectorCustomizers(connector -> {
            Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
            protocol.setMaxSwallowSize(2 * 1024 * 1024 * 1024); // 2GB
            protocol.setConnectionTimeout(60000); // 60 seconds
            log.info("Configured Tomcat: maxSwallowSize=2GB, connectionTimeout=60s");
        });
    }
}

