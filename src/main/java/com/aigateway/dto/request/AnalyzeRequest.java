package com.aigateway.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyzeRequest {

    @NotBlank(message = "Content must not be blank")
    private String content;

}
