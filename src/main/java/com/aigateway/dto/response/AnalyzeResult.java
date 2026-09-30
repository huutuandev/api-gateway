package com.aigateway.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyzeResult {

    private String summary;
    private String sentiment; // POSITIVE, NEGATIVE, NEUTRAL
    private List<String> topics;

}
