package com.aigateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    /** HMAC-SHA secret key. Read from JWT_SECRET env var. */
    private String secret;

    /** Access token TTL in milliseconds. Default: 15 min. */
    private long accessTokenExpiration = 900_000L;

    /** Refresh token TTL in milliseconds. Default: 7 days. */
    private long refreshTokenExpiration = 604_800_000L;

    /** Refresh token TTL in seconds (derived). */
    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpiration / 1000;
    }
}
