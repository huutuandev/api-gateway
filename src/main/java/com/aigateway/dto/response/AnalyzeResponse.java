package com.aigateway.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyzeResponse {

    private AnalyzeResult result;
    private ChatResponse.TokenUsage usage;
    private Long latencyMs;

}
