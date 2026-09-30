package com.aigateway.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDetailResponse {

    private Long id;

    private Long userId;

    private String title;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** Full ordered message history (chronological, ASC). */
    private List<MessageResponse> messages;
}
