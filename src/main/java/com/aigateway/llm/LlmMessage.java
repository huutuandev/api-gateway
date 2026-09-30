package com.aigateway.llm;

import com.aigateway.enums.MessageRole;

/**
 * Internal representation of a single message sent to / received from an LLM.
 * Decoupled from both the HTTP-facing DTOs and the JPA entity.
 */
public record LlmMessage(MessageRole role, String content) {

    /** Convenience factory for a USER turn. */
    public static LlmMessage user(String content) {
        return new LlmMessage(MessageRole.USER, content);
    }

    /** Convenience factory for a SYSTEM prompt. */
    public static LlmMessage system(String content) {
        return new LlmMessage(MessageRole.SYSTEM, content);
    }

    /** Convenience factory for an ASSISTANT turn. */
    public static LlmMessage assistant(String content) {
        return new LlmMessage(MessageRole.ASSISTANT, content);
    }

    /**
     * Convert role to the lowercase string that OpenAI expects.
     * e.g. MessageRole.USER → "user"
     */
    public String roleAsString() {
        return role.name().toLowerCase();
    }
}
