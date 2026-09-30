package com.aigateway.service;

import com.aigateway.config.AiProperties;
import com.aigateway.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock AiProperties aiProperties;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock AiProperties.RateLimit rateLimit;

    @InjectMocks RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        when(aiProperties.getRateLimit()).thenReturn(rateLimit);
        when(rateLimit.getRequests()).thenReturn(2);
        when(rateLimit.getWindowSeconds()).thenReturn(60);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void enforceAiChatRateLimit_allowsRequestWhenUnderLimit() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        rateLimitService.enforceAiChatRateLimit(1L);

        verify(redisTemplate).expire(eq("ratelimit:ai:chat:user:1"), eq(Duration.ofSeconds(60)));
    }

    @Test
    void enforceAiChatRateLimit_allowsRequestWhenAtLimit() {
        when(valueOperations.increment(anyString())).thenReturn(2L);

        rateLimitService.enforceAiChatRateLimit(1L);

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void enforceAiChatRateLimit_throwsExceptionWhenOverLimit() {
        when(valueOperations.increment(anyString())).thenReturn(3L);

        assertThatThrownBy(() -> rateLimitService.enforceAiChatRateLimit(1L))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Rate limit exceeded");
    }
}
