package com.aigateway.llm;

import java.util.List;

/**
 * Abstraction over any LLM provider.
 *
 * Implementations MUST:
 *   - Never log the API key
 *   - Return null token fields when the provider does not supply usage
 *   - Throw {@link LlmException} on provider error or timeout
 */
public interface LlmProvider {

    /**
     * Send a sequence of messages to the LLM and return the completion.
     *
     * @param messages ordered chat history (SYSTEM → USER → ASSISTANT → ...)
     * @param model    model name requested by caller
     * @return LlmResponse with content and optional token usage
     * @throws LlmException on HTTP error, timeout, or parse failure
     */
    LlmResponse complete(List<LlmMessage> messages, String model);

    /**
     * Human-readable name of this provider, e.g. "openai".
     * Used for logging and metrics — must NOT contain credentials.
     */
    String providerName();
}
