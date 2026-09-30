package com.aigateway.controller;

import com.aigateway.dto.response.UsageResponse;
import com.aigateway.security.user.CustomUserDetails;
import com.aigateway.security.jwt.JwtService;
import com.aigateway.service.AiService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/usage")
@RequiredArgsConstructor
public class UsageController {

    private final AiService aiService;


    @GetMapping
    public ResponseEntity<UsageResponse> getUsage(@AuthenticationPrincipal CustomUserDetails principal) {
        Long userId = principal.getId();
        UsageResponse response = aiService.getUsageStats(userId);
        return ResponseEntity.ok(response);
    }

}
