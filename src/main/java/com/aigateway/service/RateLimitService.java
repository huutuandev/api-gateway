package com.aigateway.service;

import com.aigateway.config.AiProperties;
import com.aigateway.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final AiProperties aiProperties;

    /**
     * Checks if the user has exceeded their rate limit for AI chat.
     * Fixed-window implementation using Redis INCR and EXPIRE.
     *
     * @param userId the authenticated user's ID
     * @throws RateLimitExceededException if the limit is exceeded
     */
    public void enforceAiChatRateLimit(Long userId) {
        int limit = aiProperties.getRateLimit().getRequests();
        int windowSeconds = aiProperties.getRateLimit().getWindowSeconds();
        
        String key = "ratelimit:ai:chat:user:" + userId;
        
        // Atomically increment the count for this user
        Long count = redisTemplate.opsForValue().increment(key);
        
        if (count == null) {
            count = 1L; // Should not happen with increment, but just in case
        }
        
        // If this is the first request in the window, set the expiration
        if (count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
        
        if (count > limit) {
            log.warn("User {} exceeded AI chat rate limit ({} requests / {} seconds)", 
                    userId, limit, windowSeconds);
            throw new RateLimitExceededException(
                    String.format("Rate limit exceeded. Maximum %d requests per %d seconds allowed.", 
                    limit, windowSeconds)
            );
        }
    }
}
