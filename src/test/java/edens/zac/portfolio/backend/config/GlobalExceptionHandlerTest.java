package edens.zac.portfolio.backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @GetMapping("/generic")
    String generic() {
      throw new RuntimeException("unexpected failure");
    }
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
  void handleGeneric_returnsStatus500() throws Exception {
    mockMvc
        .perform(get("/test/generic").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
  }
}
