package com.aigateway.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageResponse {
    private long requests;
    private long tokens;
    private double averageLatencyMs;
    private double errorRate;
}
