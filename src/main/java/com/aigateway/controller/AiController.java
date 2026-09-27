package com.aigateway.controller;

import com.aigateway.dto.request.ChatRequest;
import com.aigateway.dto.response.ChatResponse;
import com.aigateway.security.JwtService;
import com.aigateway.service.AiService;
import com.aigateway.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI chat endpoint.
 * Requires a valid JWT (enforced by SecurityConfig — anyRequest().authenticated()).
 * userId is extracted from the JWT, never from the request body.
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService        aiService;
    private final JwtService       jwtService;
    private final RateLimitService rateLimitService;

    /**
     * POST /api/v1/ai/chat
     * Send messages to the LLM and receive the completion.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = extractUserId(httpRequest);
        
        // Enforce rate limit before calling LLM
        rateLimitService.enforceAiChatRateLimit(userId);
        
        ChatResponse response = aiService.chat(userId, request);
        return ResponseEntity.ok(response);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Long extractUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return jwtService.extractUserId(header.substring(7));
        }
        throw new IllegalStateException("No Bearer token — should have been rejected by security filter");
    }
}
