package edens.zac.portfolio.backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  /** Minimal controller that throws specific exceptions for each test path. */
  @RestController
  @RequestMapping("/test")
  static class StubController {

    @GetMapping("/not-found")
    String notFound() {
      throw new ResourceNotFoundException("Collection not found with ID: 99");
    }

    @GetMapping("/bad-arg")
    String badArg() {
      throw new IllegalArgumentException("Invalid argument value");
    }

    @GetMapping("/bad-arg-null-message")
    String badArgNullMessage() {
      throw new IllegalArgumentException((String) null);
    }

    @GetMapping("/illegal-state")
    String illegalState() {
      throw new IllegalStateException("Operation not allowed in current state");
    }

    @GetMapping("/data-integrity")
    String dataIntegrity() {
      throw new DataIntegrityViolationException("duplicate key value");
    }

    @GetMapping("/type-mismatch")
    String typeMismatch(@RequestParam Long id) {
      return id.toString();
    }

    @GetMapping("/constraint-violation")
    String constraintViolation() {
      // Empty set — ConstraintViolationException still maps to 400
      Set<ConstraintViolation<?>> violations = Set.of();
      throw new ConstraintViolationException("constraint violated", violations);
    }

    @GetMapping("/access-denied")
    String accessDenied() {
      throw new AccessDeniedException("No gallery access for collection 112");
    }

    @GetMapping("/generic")
    String generic() {
      throw new RuntimeException("unexpected failure");
    }

    @GetMapping("/no-route")
    String noRoute() throws NoResourceFoundException {
      throw new NoResourceFoundException(HttpMethod.GET, "/test/no-route");
    }

    @PostMapping("/echo")
    String echo(@RequestBody EchoRequest body) {
      return body.value();
    }

    /** Minimal body type so {@code /echo} has something to deserialize a request into. */
    record EchoRequest(String value) {}
  }

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new StubController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void handleNotFound_returnsStatus404() throws Exception {
    mockMvc
        .perform(get("/test/not-found").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.error").value("Not Found"))
        .andExpect(jsonPath("$.message").value("Collection not found with ID: 99"))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  void handleIllegalArgument_returnsStatus400() throws Exception {
    mockMvc
        .perform(get("/test/bad-arg").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value("Invalid argument value"));
  }

  @Test
  void handleIllegalArgument_nullMessage_returnsStatus400() throws Exception {
    mockMvc
        .perform(get("/test/bad-arg-null-message").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void handleIllegalState_returnsStatus400() throws Exception {
    mockMvc
        .perform(get("/test/illegal-state").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value("Operation not allowed in current state"));
  }

  @Test
  void handleDataIntegrity_returnsStatus409() throws Exception {
    mockMvc
        .perform(get("/test/data-integrity").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(
            jsonPath("$.message").value("Data integrity violation: duplicate or invalid data"));
  }

  @Test
  void handleTypeMismatch_returnsStatus400WithParameterName() throws Exception {
    mockMvc
        .perform(
            get("/test/type-mismatch")
                .param("id", "not-a-number")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value("Parameter 'id' must be of type Long"));
  }

  @Test
  void handleTypeMismatch_nullRequiredType_returnsStatus400WithUnknownType() throws Exception {
    // Build a MethodArgumentTypeMismatchException with null requiredType directly
    MethodArgumentTypeMismatchException ex =
        new MethodArgumentTypeMismatchException("bad", null, "myParam", null, null);

    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    var response = handler.handleTypeMismatch(ex);

    assert response.getStatusCode().value() == 400;
    assert response.getBody() != null;
    assert response.getBody().message().contains("myParam");
    assert response.getBody().message().contains("unknown");
  }

  @Test
  void handleConstraintViolation_returnsStatus400() throws Exception {
    mockMvc
        .perform(get("/test/constraint-violation").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void handleValidation_aggregatesFieldErrors() throws Exception {
    // Build the exception directly and invoke the handler
    var bindingResult = new BeanPropertyBindingResult(new Object(), "target");
    bindingResult.addError(new FieldError("target", "title", "must not be blank"));
    bindingResult.addError(new FieldError("target", "type", "must not be null"));

    MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    var response = handler.handleValidation(ex);

    assert response.getStatusCode().value() == 400;
    assert response.getBody() != null;
    String msg = response.getBody().message();
    assert msg.contains("title: must not be blank") : "Expected title error in: " + msg;
    assert msg.contains("type: must not be null") : "Expected type error in: " + msg;
  }

  @Test
  void handleAccessDenied_returnsStatus403() throws Exception {
    // A non-member viewer's per-render selects seed read throws AccessDeniedException from the
    // controller method, after the security filter chain. Without a dedicated handler it is caught
    // by the catch-all Exception handler and mis-reported as 500 with a full stack trace; an
    // authorization denial must be a quiet 403.
    mockMvc
        .perform(get("/test/access-denied").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.error").value("Forbidden"))
        .andExpect(jsonPath("$.message").value("No gallery access for collection 112"))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  void handleGeneric_returnsStatus500() throws Exception {
    mockMvc
        .perform(get("/test/generic").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
  }

  @Test
  void handleNoResourceFound_returnsStatus404() throws Exception {
    // An unmatched path (e.g. a retired or typo'd route) is a NoResourceFoundException raised by
    // the dispatcher. Without a dedicated handler it is caught by the catch-all Exception handler
    // and mis-reported as 500 -- this was the 0207 prod bug's proximate symptom once
    // AdminController was promoted out of @Profile("dev"): unknown paths must be 404, not 500.
    mockMvc
        .perform(get("/test/no-route").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.error").value("Not Found"))
        .andExpect(jsonPath("$.message").value("No route found for /test/no-route"))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  void handleMessageNotReadable_returnsStatus400() throws Exception {
    // A malformed request body fails Jackson deserialization with
    // HttpMessageNotReadableException. Without a dedicated handler it is caught by the
    // catch-all Exception handler and mis-reported as 500; it must be a 400.
    mockMvc
        .perform(
            post("/test/echo").contentType(MediaType.APPLICATION_JSON).content("{ invalid json }"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Bad Request"))
        .andExpect(jsonPath("$.message").value("Malformed or unreadable request body"))
        .andExpect(jsonPath("$.timestamp").exists());
  }
}
