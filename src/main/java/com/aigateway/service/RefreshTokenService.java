package com.aigateway.service;

import com.aigateway.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Manages opaque refresh tokens stored in Redis.
 *
 * Key pattern: auth:refresh:{userId}:{tokenId}
 * Value:       "active"
 * TTL:         jwt.refresh-token-expiration (default 7 days)
 *
 * Token rotation: each refresh call deletes the old key and creates a new one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Generate a new refresh token, store it in Redis, and return the
     * opaque token string "{userId}:{tokenId}".
     */
    public String createRefreshToken(Long userId) {
        String tokenId = UUID.randomUUID().toString();
        String redisKey = buildKey(userId, tokenId);

        redisTemplate.opsForValue().set(
                redisKey,
                "active",
                Duration.ofMillis(jwtProperties.getRefreshTokenExpiration())
        );

        log.debug("Refresh token created: userId={}", userId);
        return encode(userId, tokenId);
    }

    // ── Validate ──────────────────────────────────────────────────────────────

    /**
     * Return true only if the Redis key for this token still exists.
     */
    public boolean exists(String refreshToken) {
        try {
            Parts parts = decode(refreshToken);
            String key = buildKey(parts.userId(), parts.tokenId());
            Boolean present = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(present);
        } catch (Exception e) {
            return false;
        }
    }

    // ── Rotate ────────────────────────────────────────────────────────────────

    /**
     * Validate, delete old token, and return the userId so a new token can
     * be generated. Throws {@link com.aigateway.exception.InvalidTokenException}
     * if the token is not in Redis.
     */
    public Long validateAndExtractUserId(String refreshToken) {
        Parts parts = decode(refreshToken);
        String key = buildKey(parts.userId(), parts.tokenId());

        Boolean present = redisTemplate.hasKey(key);
        if (!Boolean.TRUE.equals(present)) {
            throw new com.aigateway.exception.InvalidTokenException(
                    "Refresh token is invalid or has expired"
            );
        }
        return parts.userId();
    }

    /**
     * Delete the old refresh token from Redis.
     */
    public void deleteRefreshToken(String refreshToken) {
        try {
            Parts parts = decode(refreshToken);
            redisTemplate.delete(buildKey(parts.userId(), parts.tokenId()));
            log.debug("Refresh token deleted: userId={}", parts.userId());
        } catch (Exception e) {
            log.warn("Could not delete refresh token: {}", e.getMessage());
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private String buildKey(Long userId, String tokenId) {
        return KEY_PREFIX + userId + ":" + tokenId;
    }

    /** Encode as "{userId}:{tokenId}" for the opaque token value sent to client. */
    private String encode(Long userId, String tokenId) {
        return userId + ":" + tokenId;
    }

    /** Decode "{userId}:{tokenId}" back to its parts. */
    private Parts decode(String token) {
        if (token == null || !token.contains(":")) {
            throw new com.aigateway.exception.InvalidTokenException(
                    "Refresh token format is invalid"
            );
        }
        int firstColon = token.indexOf(':');
        try {
            Long userId = Long.parseLong(token.substring(0, firstColon));
            String tokenId = token.substring(firstColon + 1);
            return new Parts(userId, tokenId);
        } catch (NumberFormatException e) {
            throw new com.aigateway.exception.InvalidTokenException(
                    "Refresh token format is invalid"
            );
        }
    }

    private record Parts(Long userId, String tokenId) {}
}
