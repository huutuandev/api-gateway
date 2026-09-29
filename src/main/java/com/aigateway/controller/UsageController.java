package com.aigateway.controller;

import com.aigateway.dto.response.UsageResponse;
import com.aigateway.security.JwtService;
import com.aigateway.service.AiService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for User AI Usage statistics.
 *
 * All endpoints require a valid JWT (enforced by SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/usage")
@RequiredArgsConstructor
public class UsageController {

    private final AiService aiService;
    private final JwtService jwtService;

    /**
     * Get usage statistics for the authenticated user.
     * GET /api/v1/usage
     */
    @GetMapping
    public ResponseEntity<UsageResponse> getUsage(HttpServletRequest httpRequest) {
        Long userId = extractUserId(httpRequest);
        UsageResponse response = aiService.getUsageStats(userId);
        return ResponseEntity.ok(response);
    }

    private Long extractUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return jwtService.extractUserId(header.substring(7));
        }
        throw new IllegalStateException("No Bearer token found — should have been rejected by security filter");
    }
}
