package com.aigateway.llm;

import com.aigateway.config.AiProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * LlmProvider implementation for the Groq API (which is OpenAI compatible).
 *
 * Endpoint: POST /openai/v1/chat/completions
 * Docs:     https://console.groq.com/docs/api-reference
 *
 * API key is never logged. Token usage fields are null-safe.
 */
@Component
@Slf4j
public class GroqLlmProvider implements LlmProvider {

    private static final String PROVIDER  = "groq";
    // Groq's chat completions endpoint (base url already has /openai/v1 if configured that way, 
    // but the standard OpenAI compatible path is usually /chat/completions)
    // If baseUrl in AiProperties is "https://api.groq.com/openai/v1", then we just append "/chat/completions"
    private static final String CHAT_PATH = "/chat/completions";

    private final RestClient        restClient;
    private final AiProperties      aiProperties;

    /**
     * Explicit constructor — allows Mockito @InjectMocks to work without @Qualifier.
     * The RestClient bean named "aiRestClient" is injected by Spring via LlmConfig.
     */
    public GroqLlmProvider(
            RestClient aiRestClient,
            AiProperties aiProperties
    ) {
        this.restClient      = aiRestClient;
        this.aiProperties = aiProperties;
    }

    // ── LlmProvider ───────────────────────────────────────────────────────────

    @Override
    public String providerName() {
        return PROVIDER;
    }

    @Override
    public LlmResponse complete(List<LlmMessage> messages, String model) {
        String resolvedModel = (model != null && !model.isBlank())
                ? model
                : aiProperties.getDefaultModel();

        log.debug("[{}] Sending {} messages, model={}", PROVIDER, messages.size(), resolvedModel);

        long start = System.currentTimeMillis();
        GroqResponse groqResponse = callApi(messages, resolvedModel);
        long latency = System.currentTimeMillis() - start;

        return toLlmResponse(groqResponse, latency);
    }

    // ── HTTP call ─────────────────────────────────────────────────────────────

    private GroqResponse callApi(List<LlmMessage> messages, String model) {
        List<Map<String, String>> messagePayload = messages.stream()
                .map(m -> Map.of("role", m.roleAsString(), "content", m.content()))
                .toList();

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messagePayload,
                "max_tokens", 512
        );

        try {
            GroqResponse response = restClient.post()
                    .uri(CHAT_PATH)
                    .body(requestBody)
                    .retrieve()
                    .body(GroqResponse.class);

            if (response == null) {
                throw LlmException.parseError(PROVIDER,
                        new IllegalStateException("Empty response body from Groq"));
            }
            return response;

        } catch (RestClientResponseException ex) {
            log.warn("[{}] API error: status={}", PROVIDER, ex.getStatusCode().value());
            throw LlmException.httpError(PROVIDER, ex.getStatusCode().value(), ex.getStatusText());

        } catch (ResourceAccessException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("timeout")) {
                log.warn("[{}] Request timed out after {} ms", PROVIDER, aiProperties.getTimeoutMs());
                throw LlmException.timeout(PROVIDER, aiProperties.getTimeoutMs());
            }
            log.warn("[{}] Network error: {}", PROVIDER, ex.getMessage());
            throw new LlmException("[" + PROVIDER + "] Network error: " + ex.getMessage(), ex);
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private LlmResponse toLlmResponse(GroqResponse r, long latencyMs) {
        if (r.choices() == null || r.choices().isEmpty()) {
            throw LlmException.parseError(PROVIDER,
                    new IllegalStateException("Groq response contained no choices"));
        }

        String content = r.choices().get(0).message() != null
                ? r.choices().get(0).message().content()
                : "";

        Integer promptTokens     = null;
        Integer completionTokens = null;
        Integer totalTokens      = null;

        if (r.usage() != null) {
            promptTokens     = r.usage().promptTokens();
            completionTokens = r.usage().completionTokens();
            totalTokens      = r.usage().totalTokens();
            log.debug("[{}] Token usage — prompt={}, completion={}, total={}",
                    PROVIDER, promptTokens, completionTokens, totalTokens);
        } else {
            log.debug("[{}] No token usage returned by provider", PROVIDER);
        }

        return new LlmResponse(r.model(), content, promptTokens, completionTokens, totalTokens, latencyMs);
    }

    // ── Internal response DTOs (Groq/OpenAI wire format) ──────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GroqResponse(String model, List<Choice> choices, Usage usage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(@JsonProperty("finish_reason") String finishReason, Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(
            @JsonProperty("prompt_tokens")     Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens")      Integer totalTokens
    ) {}
}
