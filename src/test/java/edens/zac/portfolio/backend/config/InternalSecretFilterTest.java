package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalSecretFilterTest {

  private static final String SHARE_TOKEN = "shareTok3nABC123";
  private static final String INVITE_TOKEN = "inviteTok3nXYZ789";

  private final InternalSecretFilter filter = new InternalSecretFilter("the-secret", "");
  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

  @BeforeEach
  void attachAppender() {
    logger = (Logger) LoggerFactory.getLogger(InternalSecretFilter.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(appender);
  }

  private String rejectAndCollectLog(String uri) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, mock(FilterChain.class));
    assertThat(response.getStatus()).isEqualTo(403);
    List<ILoggingEvent> events = appender.list;
    return String.join("\n", events.stream().map(ILoggingEvent::getFormattedMessage).toList());
  }

  @Test
  void loggablePathMasksShareToken() {
    assertThat(InternalSecretFilter.loggablePath("/api/read/share/abc123"))
        .isEqualTo("/api/read/share/***");
  }

  @Test
  void loggablePathMasksInviteToken() {
    assertThat(InternalSecretFilter.loggablePath("/api/auth/invite/abc123"))
        .isEqualTo("/api/auth/invite/***");
  }

  @Test
  void loggablePathLeavesOtherUrisUnchanged() {
    assertThat(InternalSecretFilter.loggablePath("/api/read/ping")).isEqualTo("/api/read/ping");
    assertThat(InternalSecretFilter.loggablePath("/api/read/collections/slug"))
        .isEqualTo("/api/read/collections/slug");
  }

  @Test
  void rejectLogForShareUriMasksTokenAndKeepsPath() throws Exception {
    String logged = rejectAndCollectLog("/api/read/share/" + SHARE_TOKEN);
    assertThat(logged).doesNotContain(SHARE_TOKEN).contains("/api/read/share/***");
  }

  @Test
  void rejectLogForInviteUriMasksTokenAndKeepsPath() throws Exception {
    String logged = rejectAndCollectLog("/api/auth/invite/" + INVITE_TOKEN);
    assertThat(logged).doesNotContain(INVITE_TOKEN).contains("/api/auth/invite/***");
  }

  @Test
  void rejectLogForOrdinaryUriIsUnchanged() throws Exception {
    String logged = rejectAndCollectLog("/api/read/ping");
    assertThat(logged).contains("/api/read/ping");
  }

  @Test
  void validSecretStillPassesOnShareUri() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/api/read/share/" + SHARE_TOKEN);
    request.addHeader("X-Internal-Secret", "the-secret");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(appender.list).isEmpty();
  }

  @Test
  void twinRejectLogLeaksShareTokenWhenLoggablePathIsOff() throws Exception {
    try (MockedStatic<InternalSecretFilter> mocked =
        Mockito.mockStatic(
            InternalSecretFilter.class,
            invocation ->
                invocation.getMethod().getName().equals("loggablePath")
                    ? invocation.getArgument(0)
                    : invocation.callRealMethod())) {
      String logged = rejectAndCollectLog("/api/read/share/" + SHARE_TOKEN);
      assertThat(logged).contains(SHARE_TOKEN);
    }
  }

  @Test
  void twinRejectLogLeaksInviteTokenWhenLoggablePathIsOff() throws Exception {
    try (MockedStatic<InternalSecretFilter> mocked =
        Mockito.mockStatic(
            InternalSecretFilter.class,
            invocation ->
                invocation.getMethod().getName().equals("loggablePath")
                    ? invocation.getArgument(0)
                    : invocation.callRealMethod())) {
      String logged = rejectAndCollectLog("/api/auth/invite/" + INVITE_TOKEN);
      assertThat(logged).contains(INVITE_TOKEN);
    }
  }

  @Test
  void orderIsBelowSpringSecurityChain() {
    Order order = InternalSecretFilter.class.getAnnotation(Order.class);
    assertThat(order).isNotNull();
    assertThat(order.value()).isEqualTo(-200);
  }

  @Test
  void rejectsMissingSecretWith403() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/read/ping");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  void passesWithValidSecret() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/read/ping");
    request.addHeader("X-Internal-Secret", "the-secret");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  void healthEndpointBypassesGate() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }
}
