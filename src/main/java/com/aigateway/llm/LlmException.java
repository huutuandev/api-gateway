package com.aigateway.llm;

/**
 * Thrown when communication with the LLM provider fails.
 * Wraps provider-specific errors into a single, provider-agnostic exception.
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Provider returned a non-2xx HTTP status. */
    public static LlmException httpError(String provider, int statusCode, String body) {
        return new LlmException(
                String.format("[%s] LLM API returned HTTP %d: %s", provider, statusCode, body)
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
