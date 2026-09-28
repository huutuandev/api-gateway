package com.aigateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai") // Đổi từ openai sang ai
@Getter
@Setter
public class AiProperties {

    /**
     * Groq API key. MUST be set via environment variable GROQ_API_KEY.
     * Never log or expose this value.
     */
    private String provider;

    private String apiKey;

    /** Base URL of the Groq API. */
    private String baseUrl;

    /** Default model to use when caller doesn't specify. */
    private String defaultModel;

    /** Configurations for outbound LLM calls. */
    private Llm llm = new Llm();

    /** Rate limiting configuration for AI endpoints. */
    private RateLimit rateLimit = new RateLimit();

    @Getter
    @Setter
    public static class Llm {
        private int connectTimeout = 5000;
        private int readTimeout = 30000;
        private int maxRetries = 2;
    }

    @Getter
    @Setter
    public static class RateLimit {
        /** Number of allowed requests per window. */
        private int requests = 5;
        
        /** Window size in seconds. */
        private int windowSeconds = 60;
    }
}
