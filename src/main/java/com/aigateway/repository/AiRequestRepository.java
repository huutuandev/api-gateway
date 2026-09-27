package com.aigateway.repository;

import com.aigateway.entity.AiRequest;
import com.aigateway.enums.AiRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiRequestRepository extends JpaRepository<AiRequest, Long> {

    Optional<AiRequest> findByRequestId(UUID requestId);

    List<AiRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<AiRequest> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    long countByUserIdAndStatus(Long userId, AiRequestStatus status);
}
