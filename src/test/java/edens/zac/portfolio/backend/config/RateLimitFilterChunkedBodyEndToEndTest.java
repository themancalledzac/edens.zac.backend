package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Answers U-2: does Tomcat surface {@code Transfer-Encoding} to {@code getHeader()}?
 *
 * <p>S-5's fix rejects an undeclared-length body on {@code /api/public/**} with 411, and the branch
 * that does it reads {@code request.getHeader("Transfer-Encoding")}. Tomcat consumes that header
 * while installing the chunked input filter, so if it does not also expose it, the branch never
 * fires and {@code Transfer-Encoding: chunked} is still a one-header bypass of the 16KB cap. {@code
 * RateLimitFilterTest} cannot settle this -- it uses {@code MockHttpServletRequest}, which returns
 * whatever the test put in.
 *
 * <p>Requests go over a raw socket rather than through a client library, because the point is what
 * reaches the wire and every HTTP client is free to convert a chunked body to a declared length.
 * Both assertions check the filter's own error message, not just the status: a bare status could
 * come from Tomcat rejecting the request itself, which would leave the branch dead and the test
 * green.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitFilterChunkedBodyEndToEndTest extends AbstractPostgresIntegrationTest {

  private static final String BODY = "{\"email\":\"chunked@example.com\",\"message\":\"hello\"}";

  @LocalServerPort private int port;

  private String send(String request) throws IOException {
    try (Socket socket = new Socket("localhost", port)) {
      socket.setSoTimeout(10_000);
      socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
      socket.getOutputStream().flush();
      return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private String headers(String framing) {
    return "POST /api/public/messages HTTP/1.1\r\n"
        + "Host: localhost:"
        + port
        + "\r\n"
        + "Content-Type: application/json\r\n"
        + framing
        + "Connection: close\r\n"
        + "\r\n";
  }

  @Test
  void chunkedBodyIsRejectedWith411() throws IOException {
    String chunked = Integer.toHexString(BODY.length()) + "\r\n" + BODY + "\r\n" + "0\r\n" + "\r\n";

    String response = send(headers("Transfer-Encoding: chunked\r\n") + chunked);

    assertThat(response).startsWith("HTTP/1.1 411");
    assertThat(response).contains("Chunked encoding is not accepted here.");
  }

  /**
   * The control. Proves the filter is engaged on this path in a booted server and that the 411
   * above is the chunked branch specifically, not the filter refusing every public POST.
   */
  @Test
  void declaredLengthOverTheCapIsRejectedWith413() throws IOException {
    String oversized = "x".repeat(20_000);

    String response = send(headers("Content-Length: " + oversized.length() + "\r\n") + oversized);

    assertThat(response).startsWith("HTTP/1.1 413");
    assertThat(response).contains("Request body exceeds the maximum accepted size.");
  }
}
