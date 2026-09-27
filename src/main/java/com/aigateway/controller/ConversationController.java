package com.aigateway.controller;

import com.aigateway.dto.request.AddMessageRequest;
import com.aigateway.dto.request.CreateConversationRequest;
import com.aigateway.dto.response.ConversationDetailResponse;
import com.aigateway.dto.response.ConversationResponse;
import com.aigateway.dto.response.MessageResponse;
import com.aigateway.security.JwtService;
import com.aigateway.service.ConversationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Conversation and Message management.
 *
 * All endpoints require a valid JWT (enforced by SecurityConfig → anyRequest().authenticated()).
 * userId is extracted from the JWT claims, NOT from the request body.
 */
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final JwtService          jwtService;

    // ── Conversations ─────────────────────────────────────────────────────────

    /**
     * Create a new conversation.
     * POST /api/v1/conversations
     */
    @PostMapping
    public ResponseEntity<ConversationResponse> create(
            @Valid @RequestBody CreateConversationRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = extractUserId(httpRequest);
        ConversationResponse response = conversationService.createConversation(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all conversations for the authenticated user (newest first).
     * GET /api/v1/conversations
     */
    @GetMapping
    public ResponseEntity<List<ConversationResponse>> list(HttpServletRequest httpRequest) {
        Long userId = extractUserId(httpRequest);
        return ResponseEntity.ok(conversationService.listConversations(userId));
    }

    /**
     * Get conversation detail with full message history.
     * GET /api/v1/conversations/{id}/history
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<ConversationDetailResponse> history(
            @PathVariable Long id,
            HttpServletRequest httpRequest
    ) {
        Long userId = extractUserId(httpRequest);
        return ResponseEntity.ok(conversationService.getHistory(userId, id));
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    /**
     * Add a message to a conversation.
     * POST /api/v1/conversations/{id}/messages
     */
    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageResponse> addMessage(
            @PathVariable Long id,
            @Valid @RequestBody AddMessageRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = extractUserId(httpRequest);
        MessageResponse response = conversationService.addMessage(userId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Extract userId from the Bearer JWT in the Authorization header.
     * By the time this runs, JwtAuthenticationFilter has already validated the token.
     */
    private Long extractUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            return jwtService.extractUserId(token);
        }
        throw new IllegalStateException("No Bearer token found — should have been rejected by security filter");
    }
}
