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


    public GroqLlmProvider(
            RestClient aiRestClient,
            AiProperties aiProperties
    ) {
        this.restClient      = aiRestClient;
        this.aiProperties = aiProperties;
    }

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

        int maxRetries = aiProperties.getLlm().getMaxRetries();
        int attempts = 0;
        long start = System.currentTimeMillis();

        while (true) {
            attempts++;
            try {
                GroqResponse groqResponse = callApi(messages, resolvedModel);
                long latency = System.currentTimeMillis() - start;
                return toLlmResponse(groqResponse, latency);
            } catch (LlmException ex) {
                if (attempts > maxRetries || !isRetryable(ex)) {
                    if (attempts > 1) {
                        log.warn("[{}] All {} retries exhausted or non-retryable error.", PROVIDER, attempts - 1);
                    }
                    throw ex;
                }
                
                long backoffMs = (long) (Math.pow(2, attempts - 1) * 1000); // 1s, 2s, 4s...
                log.warn("[{}] Attempt {} failed ({}). Retrying in {} ms...", PROVIDER, attempts, ex.getMessage(), backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LlmException("[" + PROVIDER + "] Interrupted during retry backoff", ie);
                }
            }
        }
    }

    private boolean isRetryable(LlmException ex) {
        // Retry on timeout or network errors
        if (ex.getMessage() != null && (ex.getMessage().contains("timed out") || ex.getMessage().contains("Network error"))) {
            return true;
        }
        // Retry on HTTP 429 or 5xx
        if (ex.getStatusCode() != null) {
            int code = ex.getStatusCode();
            return code == 429 || code >= 500;
        }
        return false;
    }

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
                int readTimeout = aiProperties.getLlm().getReadTimeout();
                log.warn("[{}] Request timed out after {} ms", PROVIDER, readTimeout);
                throw LlmException.timeout(PROVIDER, readTimeout);
            }
            log.warn("[{}] Network error: {}", PROVIDER, ex.getMessage());
            throw new LlmException("[" + PROVIDER + "] Network error: " + ex.getMessage(), ex);
        }
    }

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
