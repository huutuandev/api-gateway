package com.aigateway.exception;

import com.aigateway.llm.LlmException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/test");
        MDC.put("requestId", "req-1234");
    }

    @Test
    @DisplayName("Validation Error (400) mapping")
    void testValidationError() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(new FieldError("dto", "field", "must not be blank")));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message")).isEqualTo("must not be blank");
        assertThat(response.getBody().get("requestId")).isEqualTo("req-1234");
    }

    @Test
    @DisplayName("Authentication Exception (401) mapping")
    void testAuthException() {
        AuthenticationException ex = new AuthenticationException("Bad credentials") {};

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleAuthenticationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("message")).isEqualTo("Authentication failed: Bad credentials");
        assertThat(response.getBody().get("requestId")).isEqualTo("req-1234");
    }

    @Test
    @DisplayName("Access Denied Exception (403) mapping")
    void testAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("Not allowed");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleAccessDenied(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("message")).isEqualTo("Access denied");
        assertThat(response.getBody().get("requestId")).isEqualTo("req-1234");
    }

    @Test
    @DisplayName("Rate Limit Exceeded (429) mapping")
    void testRateLimitException() {
        RateLimitExceededException ex = new RateLimitExceededException("Rate limit exceeded");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleRateLimitExceeded(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().get("requestId")).isEqualTo("req-1234");
    }

    @Test
    @DisplayName("LLM Timeout (504) mapping")
    void testLlmTimeout() {
        LlmException ex = LlmException.timeout("groq", 5000);

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleLlmError(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody().get("message")).isEqualTo("LLM provider timed out");
        assertThat(response.getBody().get("requestId")).isEqualTo("req-1234");
    }

    @Test
    @DisplayName("LLM Unavailable (502) mapping - hides internal details")
    void testLlmUnavailable() {
        LlmException ex = LlmException.httpError("groq", 500, "Internal Server Error");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleLlmError(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().get("message")).isEqualTo("LLM provider is currently unavailable"); // Ensure no "groq" is exposed
        assertThat(response.getBody().get("requestId")).isEqualTo("req-1234");
    }

    @Test
    @DisplayName("Unexpected Exception (500) mapping")
    void testUnexpectedException() {
        Exception ex = new NullPointerException("Null pointer somewhere");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleGeneric(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().get("message")).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().get("requestId")).isEqualTo("req-1234");
    }
}
