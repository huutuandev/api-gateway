package com.aigateway.controller;

import com.aigateway.dto.request.AddMessageRequest;
import com.aigateway.dto.request.CreateConversationRequest;
import com.aigateway.dto.response.ConversationDetailResponse;
import com.aigateway.dto.response.ConversationResponse;
import com.aigateway.dto.response.MessageResponse;
import com.aigateway.security.user.CustomUserDetails;
import com.aigateway.security.jwt.JwtService;
import com.aigateway.service.ConversationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final JwtService          jwtService;


    @PostMapping
    public ResponseEntity<ConversationResponse> create(
            @Valid @RequestBody CreateConversationRequest request,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        Long userId = principal.getId();
        ConversationResponse response = conversationService.createConversation(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @GetMapping
    public ResponseEntity<List<ConversationResponse>> list(@AuthenticationPrincipal CustomUserDetails principal) {
        Long userId = principal.getId();
        return ResponseEntity.ok(conversationService.listConversations(userId));
    }


    @GetMapping("/{id}/history")
    public ResponseEntity<ConversationDetailResponse> history(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        Long userId = principal.getId();
        return ResponseEntity.ok(conversationService.getHistory(userId, id));
    }


    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageResponse> addMessage(
            @PathVariable Long id,
            @Valid @RequestBody AddMessageRequest request,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        Long userId = principal.getId();
        MessageResponse response = conversationService.addMessage(userId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


}
