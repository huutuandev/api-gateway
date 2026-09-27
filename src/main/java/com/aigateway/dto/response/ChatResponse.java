package com.aigateway.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** The conversation this message belongs to. */
    private Long conversationId;

    /** The generated assistant message. */
    private MessageResponse message;

    /** Token usage details. Null if the provider did not return usage. */
    private TokenUsage usage;

    /** Unique request ID for traceability. */
    private String requestId;

    /** The model that actually generated the response. */
    private String model;

    /** End-to-end latency from gateway to LLM and back, in milliseconds. */
    private long latencyMs;

    /**
     * Token usage breakdown.
     * All fields are nullable — never fabricate token counts.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}
