package com.aigateway.dto.request;

import com.aigateway.enums.MessageRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddMessageRequest {

    @NotNull(message = "Role is required")
    private MessageRole role;

    @NotBlank(message = "Content is required")
    private String content;
}
