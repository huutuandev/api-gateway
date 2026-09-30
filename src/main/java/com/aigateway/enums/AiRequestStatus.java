package com.aigateway.enums;

public enum AiRequestStatus {
    /** LLM returned a valid response. */
    SUCCESS,

    /** LLM returned an error response (4xx/5xx from provider). */
    FAILED,

    /** Request exceeded the configured timeout. */
    TIMEOUT
}
