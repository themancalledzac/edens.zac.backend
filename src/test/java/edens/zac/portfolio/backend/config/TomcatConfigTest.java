package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;

class TomcatConfigTest {

  /**
   * Drives the customizer the way Spring Boot does: register it on the factory, then apply every
   * registered customizer to a real connector.
   *
   * @return the protocol handler the customizer configured
   */
  private static Http11NioProtocol configuredProtocol() {
    TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
    new TomcatConfig().customize(factory);

    Connector connector = new Connector(Http11NioProtocol.class.getName());
    for (TomcatConnectorCustomizer customizer : factory.getTomcatConnectorCustomizers()) {
      customizer.customize(connector);
    }
    return (Http11NioProtocol) connector.getProtocolHandler();
  }

  @Test
  @DisplayName("maxSwallowSize stays positive, so Tomcat actually applies the cap")
  void customize_maxSwallowSize_isPositive() {
    assertThat(configuredProtocol().getMaxSwallowSize())
        .as(
            "Tomcat guards this setting with maxSwallowSize > -1; a negative value disables the cap")
        .isPositive();
  }

  @Test
  @DisplayName("maxSwallowSize is the largest int an aborted body can be capped at")
  void customize_maxSwallowSize_isIntegerMaxValue() {
    assertThat(configuredProtocol().getMaxSwallowSize()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  @DisplayName("connection timeout is the 5 minutes large batch uploads need")
  void customize_connectionTimeout_isFiveMinutes() {
    assertThat(configuredProtocol().getConnectionTimeout()).isEqualTo(300_000);
  }
}
