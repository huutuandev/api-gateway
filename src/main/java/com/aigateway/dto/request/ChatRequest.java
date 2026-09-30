package com.aigateway.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /**
     * Optional conversation ID to link this request to an existing conversation.
     * If null, a new conversation will be created automatically.
     */
    private Long conversationId;

    /**
     * The model to use. If blank, falls back to openai.default-model.
     */
    private String model;

    /**
     * The latest message from the user to send to the LLM.
     */
    @NotBlank(message = "Message content must not be blank")
    private String message;
}
