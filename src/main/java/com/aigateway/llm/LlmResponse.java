package com.aigateway.llm;

/**
 * Internal representation of the result from an LLM call.
 *
 * Token fields are nullable — some providers do not return usage.
 * Callers MUST check for null before recording; never fabricate token counts.
 */
public record LlmResponse(
        /** The model name as reported by the provider (may differ from requested). */
        String model,

        /** The generated text content. */
        String content,

        /**
         * Input/prompt token count.
         * Null if the provider did not return usage information.
         */
        Integer promptTokens,

        /**
         * Output/completion token count.
         * Null if the provider did not return usage information.
         */
        Integer completionTokens,

        /**
         * Total token count (prompt + completion).
         * Null if the provider did not return usage information.
         */
        Integer totalTokens,

        /** Latency from request send to response receive, in milliseconds. */
        long latencyMs
) {

    /** True only when all three token counts are available. */
    public boolean hasUsage() {
        return promptTokens != null && completionTokens != null && totalTokens != null;
    }
}
