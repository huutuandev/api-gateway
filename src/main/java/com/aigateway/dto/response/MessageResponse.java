package com.aigateway.dto.response;

import com.aigateway.enums.MessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    private Long id;

    private MessageRole role;

    private String content;

    /** Token count, null if not yet computed. */
    private Integer tokenCount;

    private LocalDateTime createdAt;
}
