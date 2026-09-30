package com.aigateway.llm;

import com.aigateway.config.AiProperties;
import com.aigateway.enums.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GroqLlmProvider.
 * Uses LENIENT strictness because @BeforeEach stubs are not used by every test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroqLlmProviderTest {

    @Mock RestClient                        restClient;
    @Mock RestClient.RequestBodyUriSpec     requestBodyUriSpec;
    @Mock RestClient.RequestBodySpec        requestBodySpec;
    @Mock RestClient.ResponseSpec           responseSpec;
    @Mock AiProperties                      aiProperties;

    // Helper: build provider with the mocked RestClient
    private GroqLlmProvider buildProvider() {
        when(aiProperties.getDefaultModel()).thenReturn("llama-3.3-70b-versatile");
        AiProperties.Llm llmConfig = new AiProperties.Llm();
        llmConfig.setReadTimeout(5000);
        llmConfig.setMaxRetries(2);
        when(aiProperties.getLlm()).thenReturn(llmConfig);
        return new GroqLlmProvider(restClient, aiProperties);
    }

    // Helper: stub the full RestClient chain through to responseSpec
    private void stubRestClientChain() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    }

    private static final List<LlmMessage> MESSAGES = List.of(
            LlmMessage.system("You are helpful."),
            LlmMessage.user("Hello!")
    );

    // ── providerName() ────────────────────────────────────────────────────────

    @Test @DisplayName("providerName() → 'groq'")
    void providerName_isGroq() {
        GroqLlmProvider provider = buildProvider();
        assertThat(provider.providerName()).isEqualTo("groq");
    }

    // ── LlmMessage helpers ────────────────────────────────────────────────────

    @Test @DisplayName("LlmMessage.roleAsString() → lowercase")
    void llmMessage_roleAsString() {
        assertThat(new LlmMessage(MessageRole.USER,      "").roleAsString()).isEqualTo("user");
        assertThat(new LlmMessage(MessageRole.ASSISTANT, "").roleAsString()).isEqualTo("assistant");
        assertThat(new LlmMessage(MessageRole.SYSTEM,    "").roleAsString()).isEqualTo("system");
    }

    // ── LlmResponse helpers ───────────────────────────────────────────────────

    @Test @DisplayName("LlmResponse.hasUsage() is false if any token field is null")
    void llmResponse_hasUsage() {
        assertThat(new LlmResponse("m", "c", null, 5, 10, 0L).hasUsage()).isFalse();
        assertThat(new LlmResponse("m", "c", 5, null, 10, 0L).hasUsage()).isFalse();
        assertThat(new LlmResponse("m", "c", 5, 5, null, 0L).hasUsage()).isFalse();
        assertThat(new LlmResponse("m", "c", 5, 5, 10, 0L).hasUsage()).isTrue();
    }

    // ── complete() — success ──────────────────────────────────────────────────

    @Nested @DisplayName("complete() — success")
    class SuccessTests {

        @Test @DisplayName("returns content and token usage when provider supplies them")
        void complete_withUsage() {
            stubRestClientChain();
            GroqLlmProvider provider = buildProvider();

            when(responseSpec.body(any(Class.class)))
                    .thenReturn(buildFakeResponse("llama-3.3-70b-versatile", "Hello!", 10, 5, 15));

            LlmResponse result = provider.complete(MESSAGES, "llama-3.3-70b-versatile");

            assertThat(result.model()).isEqualTo("llama-3.3-70b-versatile");
            assertThat(result.content()).isEqualTo("Hello!");
            assertThat(result.promptTokens()).isEqualTo(10);
            assertThat(result.completionTokens()).isEqualTo(5);
            assertThat(result.totalTokens()).isEqualTo(15);
            assertThat(result.hasUsage()).isTrue();
        }

        @Test @DisplayName("token fields are null when provider omits usage")
        void complete_withoutUsage() {
            stubRestClientChain();
            GroqLlmProvider provider = buildProvider();

            when(responseSpec.body(any(Class.class)))
                    .thenReturn(buildFakeResponse("llama-3.3-70b-versatile", "Hi!", null, null, null));

            LlmResponse result = provider.complete(MESSAGES, "llama-3.3-70b-versatile");

            assertThat(result.content()).isEqualTo("Hi!");
            assertThat(result.promptTokens()).isNull();
            assertThat(result.completionTokens()).isNull();
            assertThat(result.totalTokens()).isNull();
            assertThat(result.hasUsage()).isFalse();
        }

        @Test @DisplayName("falls back to defaultModel when model param is blank")
        void complete_usesDefaultModel() {
            stubRestClientChain();
            GroqLlmProvider provider = buildProvider();

            when(responseSpec.body(any(Class.class)))
                    .thenReturn(buildFakeResponse("llama-3.3-70b-versatile", "OK", 5, 3, 8));

            LlmResponse result = provider.complete(MESSAGES, "");

            assertThat(result.model()).isEqualTo("llama-3.3-70b-versatile");
        }
    }

    // ── complete() — errors ───────────────────────────────────────────────────

    @Nested @DisplayName("complete() — errors")
    class ErrorTests {

        @Test @DisplayName("HTTP 4xx/5xx → LlmException with status code")
        void complete_httpError() {
            stubRestClientChain();
            GroqLlmProvider provider = buildProvider();

            RestClientResponseException httpEx = mock(RestClientResponseException.class);
            when(httpEx.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
            when(httpEx.getStatusText()).thenReturn("Too Many Requests");
            when(responseSpec.body(any(Class.class))).thenThrow(httpEx);

            assertThatThrownBy(() -> provider.complete(MESSAGES, "llama-3.3-70b-versatile"))
                    .isInstanceOf(LlmException.class)
                    .hasMessageContaining("429");
        }

        @Test @DisplayName("Timeout → LlmException with 'timed out' message")
        void complete_timeout() {
            stubRestClientChain();
            GroqLlmProvider provider = buildProvider();

            when(responseSpec.body(any(Class.class)))
                    .thenThrow(new ResourceAccessException("Read timed out after timeout threshold"));

            assertThatThrownBy(() -> provider.complete(MESSAGES, "llama-3.3-70b-versatile"))
                    .isInstanceOf(LlmException.class)
                    .hasMessageContaining("timed out");
        }

        @Test @DisplayName("null response body → LlmException")
        void complete_nullBody() {
            stubRestClientChain();
            GroqLlmProvider provider = buildProvider();

            when(responseSpec.body(any(Class.class))).thenReturn(null);

            assertThatThrownBy(() -> provider.complete(MESSAGES, "llama-3.3-70b-versatile"))
                    .isInstanceOf(LlmException.class);
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    private GroqLlmProvider.GroqResponse buildFakeResponse(
            String model, String content,
            Integer promptTokens, Integer completionTokens, Integer totalTokens
    ) {
        GroqLlmProvider.Message  msg    = new GroqLlmProvider.Message("assistant", content);
        GroqLlmProvider.Choice   choice = new GroqLlmProvider.Choice("stop", msg);
        GroqLlmProvider.Usage    usage  = (promptTokens != null)
                ? new GroqLlmProvider.Usage(promptTokens, completionTokens, totalTokens)
                : null;
        return new GroqLlmProvider.GroqResponse(model, List.of(choice), usage);
    }
}
