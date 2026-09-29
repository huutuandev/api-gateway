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

    @org.springframework.data.jpa.repository.Query("""
        SELECT
            COUNT(r) as requests,
            COALESCE(SUM(r.totalTokens), 0) as tokens,
            COALESCE(AVG(r.latencyMs), 0.0) as averageLatencyMs,
            COALESCE(SUM(CASE WHEN r.status = 'FAILED' THEN 1 ELSE 0 END) * 1.0 / NULLIF(COUNT(r), 0), 0.0) as errorRate
        FROM AiRequest r
        WHERE r.user.id = :userId
    """)
    UsageStatsProjection getUsageStatsByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);

    interface UsageStatsProjection {
        long getRequests();
        long getTokens();
        double getAverageLatencyMs();
        double getErrorRate();
    }
}
