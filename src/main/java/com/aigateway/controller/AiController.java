package com.aigateway.controller;

import com.aigateway.dto.request.ChatRequest;
import com.aigateway.dto.response.ChatResponse;
import com.aigateway.security.user.CustomUserDetails;
import com.aigateway.security.jwt.JwtService;
import com.aigateway.service.AiService;
import com.aigateway.service.RateLimitService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService        aiService;
    private final RateLimitService rateLimitService;


    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        Long userId = principal.getId();
        
        // Enforce rate limit before calling LLM
        rateLimitService.enforceAiChatRateLimit(userId);
        
        ChatResponse response = aiService.chat(userId, request);
        return ResponseEntity.ok(response);
    }


}
