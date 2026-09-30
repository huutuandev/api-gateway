package com.aigateway.repository;

import com.aigateway.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /** All conversations for a user, newest first. */
    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);

    /** Find a conversation belonging to a specific user (ownership check). */
    Optional<Conversation> findByIdAndUserId(Long id, Long userId);

    /** Count conversations for a user. */
    long countByUserId(Long userId);

    /** Check if a conversation belongs to a user. */
    boolean existsByIdAndUserId(Long id, Long userId);

    /**
     * Fetch a conversation together with its messages in one query
     * to avoid N+1 when loading history.
     */
    @Query("""
            SELECT DISTINCT c FROM Conversation c
            LEFT JOIN FETCH c.messages m
            WHERE c.id = :id AND c.user.id = :userId
            ORDER BY m.createdAt ASC
            """)
    Optional<Conversation> findWithMessagesByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId
    );
}
