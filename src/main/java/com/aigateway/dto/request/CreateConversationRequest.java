package com.aigateway.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationRequest {

    /** Optional conversation title. If blank, service may auto-generate. */
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;
}
