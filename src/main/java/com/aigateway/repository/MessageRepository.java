package com.aigateway.repository;

import com.aigateway.entity.Message;
import com.aigateway.enums.MessageRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** All messages in a conversation, ordered chronologically. */
    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    /** Messages filtered by role (e.g. only USER messages). */
    List<Message> findByConversationIdAndRoleOrderByCreatedAtAsc(
            Long conversationId,
            MessageRole role
    );

    /** Count messages in a conversation. */
    long countByConversationId(Long conversationId);
}
