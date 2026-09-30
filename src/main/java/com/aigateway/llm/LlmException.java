package com.aigateway.llm;

/**
 * Thrown when communication with the LLM provider fails.
 * Wraps provider-specific errors into a single, provider-agnostic exception.
 */
public class LlmException extends RuntimeException {

    private Integer statusCode;

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }

    public LlmException(String message, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    /** Provider returned a non-2xx HTTP status. */
    public static LlmException httpError(String provider, int statusCode, String body) {
        return new LlmException(
                String.format("[%s] LLM API returned HTTP %d: %s", provider, statusCode, body),
                statusCode
        );
    }

    /** Request exceeded the configured timeout. */
    public static LlmException timeout(String provider, int timeoutMs) {
        return new LlmException(
                String.format("[%s] LLM API timed out after %d ms", provider, timeoutMs)
        );
    }

    /** Response could not be parsed. */
    public static LlmException parseError(String provider, Throwable cause) {
        return new LlmException(
                String.format("[%s] Failed to parse LLM response", provider), cause
        );
    }
}
