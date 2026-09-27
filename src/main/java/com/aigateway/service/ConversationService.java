package com.aigateway.service;

import com.aigateway.dto.request.AddMessageRequest;
import com.aigateway.dto.request.CreateConversationRequest;
import com.aigateway.dto.response.ConversationDetailResponse;
import com.aigateway.dto.response.ConversationResponse;
import com.aigateway.dto.response.MessageResponse;
import com.aigateway.entity.Conversation;
import com.aigateway.entity.Message;
import com.aigateway.entity.User;
import com.aigateway.exception.ResourceNotFoundException;
import com.aigateway.repository.ConversationRepository;
import com.aigateway.repository.MessageRepository;
import com.aigateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository      messageRepository;
    private final UserRepository         userRepository;

    // ── Create conversation ───────────────────────────────────────────────────

    /**
     * Create a new, empty conversation belonging to the given user.
     *
     * @param userId  authenticated user's id (from JWT)
     * @param request optional title
     * @return lightweight ConversationResponse (no messages)
     */
    @Transactional
    public ConversationResponse createConversation(Long userId,
                                                   CreateConversationRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: id=" + userId));

        Conversation conversation = Conversation.builder()
                .user(user)
                .title(request.getTitle())
                .build();

        Conversation saved = conversationRepository.save(conversation);
        log.info("Conversation created: conversationId={}, userId={}", saved.getId(), userId);

        return toConversationResponse(saved);
    }

    // ── Add message ───────────────────────────────────────────────────────────

    /**
     * Append a message to a conversation.
     * Ownership is enforced: only the conversation's owner can add messages.
     *
     * @param userId         authenticated user's id
     * @param conversationId target conversation
     * @param request        role + content
     * @return MessageResponse
     */
    @Transactional
    public MessageResponse addMessage(Long userId,
                                      Long conversationId,
                                      AddMessageRequest request) {

        // Ownership check: returns empty if not found OR not owned by this user
        Conversation conversation = conversationRepository
                .findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> ResourceNotFoundException.conversation(conversationId));

        Message message = Message.builder()
                .conversation(conversation)
                .role(request.getRole())
                .content(request.getContent())
                .build();

        Message saved = messageRepository.save(message);
        log.debug("Message added: messageId={}, conversationId={}, role={}",
                saved.getId(), conversationId, saved.getRole());

        return toMessageResponse(saved);
    }

    // ── Get history ───────────────────────────────────────────────────────────

    /**
     * Load a conversation with its full ordered message history.
     * Uses a JOIN FETCH to avoid N+1 queries.
     *
     * @param userId         authenticated user's id (ownership check)
     * @param conversationId target conversation
     * @return ConversationDetailResponse with messages ordered by createdAt ASC
     */
    @Transactional(readOnly = true)
    public ConversationDetailResponse getHistory(Long userId, Long conversationId) {

        Conversation conversation = conversationRepository
                .findWithMessagesByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> ResourceNotFoundException.conversation(conversationId));

        List<MessageResponse> messageResponses = conversation.getMessages()
                .stream()
                .map(this::toMessageResponse)
                .toList();

        log.debug("History loaded: conversationId={}, messageCount={}", conversationId,
                messageResponses.size());

        return ConversationDetailResponse.builder()
                .id(conversation.getId())
                .userId(conversation.getUser().getId())
                .title(conversation.getTitle())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .messages(messageResponses)
                .build();
    }

    // ── List conversations ────────────────────────────────────────────────────

    /**
     * List all conversations for a user, newest-updated first.
     * Does NOT load messages (lightweight).
     *
     * @param userId authenticated user's id
     * @return list of ConversationResponse summaries
     */
    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(Long userId) {
        return conversationRepository
                .findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(this::toConversationResponse)
                .toList();
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private ConversationResponse toConversationResponse(Conversation c) {
        return ConversationResponse.builder()
                .id(c.getId())
                .userId(c.getUser().getId())
                .title(c.getTitle())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private MessageResponse toMessageResponse(Message m) {
        return MessageResponse.builder()
                .id(m.getId())
                .role(m.getRole())
                .content(m.getContent())
                .tokenCount(m.getTokenCount())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
