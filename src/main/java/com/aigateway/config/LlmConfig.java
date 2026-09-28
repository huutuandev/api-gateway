package com.aigateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class LlmConfig {

    private final AiProperties aiProperties;

    /**
     * RestClient configured for the AI API (Groq).
     * API key is injected from AiProperties — never hard-coded.
     * Timeout is configurable via ai.timeout-ms.
     */
    @Bean("aiRestClient")
    public RestClient aiRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(aiProperties.getLlm().getConnectTimeout());
        factory.setReadTimeout(aiProperties.getLlm().getReadTimeout());

        return RestClient.builder()
                .baseUrl(aiProperties.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Authorization", "Bearer " + aiProperties.getApiKey())
                .build();
    }
}
