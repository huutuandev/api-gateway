package com.aigateway.service;

import com.aigateway.config.AiProperties;
import com.aigateway.dto.request.ChatRequest;
import com.aigateway.dto.response.ChatResponse;
import com.aigateway.dto.response.MessageResponse;
import com.aigateway.entity.AiRequest;
import com.aigateway.entity.Conversation;
import com.aigateway.entity.Message;
import com.aigateway.entity.User;
import com.aigateway.enums.AiRequestStatus;
import com.aigateway.enums.MessageRole;
import com.aigateway.exception.ResourceNotFoundException;
import com.aigateway.llm.LlmException;
import com.aigateway.llm.LlmMessage;
import com.aigateway.llm.LlmProvider;
import com.aigateway.llm.LlmResponse;
import com.aigateway.repository.AiRequestRepository;
import com.aigateway.repository.ConversationRepository;
import com.aigateway.repository.MessageRepository;
import com.aigateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates LLM calls and conversation persistence:
 *   1. Load or create Conversation
 *   2. Save USER message to DB
 *   3. Build LlmMessage context history
 *   4. Call LlmProvider.complete()
 *   5. Save ASSISTANT message to DB
 *   6. Persist AiRequest audit record
 *   7. Return ChatResponse
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final LlmProvider          llmProvider;
    private final AiRequestRepository  aiRequestRepository;
    private final UserRepository       userRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository      messageRepository;
    private final AiProperties           aiProperties;

    @Transactional
    public ChatResponse chat(Long userId, ChatRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: id=" + userId));

        // 1. Resolve or create conversation
        Conversation conversation;
        if (request.getConversationId() != null) {
            conversation = conversationRepository.findWithMessagesByIdAndUserId(request.getConversationId(), userId)
                    .orElseThrow(() -> ResourceNotFoundException.conversation(request.getConversationId()));
        } else {
            conversation = new Conversation();
            conversation.setUser(user);
            
            // Auto-generate title from the first message
            String content = request.getMessage();
            String title = content.length() > 50 ? content.substring(0, 47) + "..." : content;
            conversation.setTitle(title);
            
            conversation = conversationRepository.save(conversation);
            log.debug("Created new conversation: id={}", conversation.getId());
        }

        // 2. Save USER message
        Message userMsg = Message.builder()
                .conversation(conversation)
                .role(MessageRole.USER)
                .content(request.getMessage())
                .build();
        userMsg = messageRepository.save(userMsg);
        
        // Add to the local entity list so it's included in the LLM context
        conversation.getMessages().add(userMsg);

        // 3. Build context history
        List<LlmMessage> messages = conversation.getMessages().stream()
                .map(m -> new LlmMessage(m.getRole(), m.getContent()))
                .toList();

        String model = (request.getModel() != null && !request.getModel().isBlank())
                ? request.getModel()
                : aiProperties.getDefaultModel();

        log.info("AI chat: userId={}, conversationId={}, model={}, contextSize={}", 
                 userId, conversation.getId(), model, messages.size());

        // 4. Call LLM
        LlmResponse llmResponse;
        AiRequestStatus status;
        String errorMessage = null;

        long startAiTime = System.currentTimeMillis();

        try {
            llmResponse = llmProvider.complete(messages, model);
            status = AiRequestStatus.SUCCESS;

        } catch (LlmException ex) {
            long failDuration = System.currentTimeMillis() - startAiTime;
            status = ex.getMessage() != null && ex.getMessage().contains("timed out")
                    ? AiRequestStatus.TIMEOUT
                    : AiRequestStatus.FAILED;
            errorMessage = ex.getMessage();
            log.warn("LLM call failed: userId={}, conversationId={}, model={}, duration={}ms, status={}, error={}", 
                     userId, conversation.getId(), model, failDuration, status, errorMessage);

            persistAuditRecord(user, conversation, model, null, null, null, failDuration, status, errorMessage);
            throw ex;
        }

        // 5. Save ASSISTANT message
        Message assistantMsg = Message.builder()
                .conversation(conversation)
                .role(MessageRole.ASSISTANT)
                .content(llmResponse.content())
                .tokenCount(llmResponse.completionTokens())
                .build();
        assistantMsg = messageRepository.save(assistantMsg);

        // 6. Persist audit record
        persistAuditRecord(user, conversation, llmResponse.model(),
                llmResponse.promptTokens(), llmResponse.completionTokens(),
                llmResponse.totalTokens(), llmResponse.latencyMs(),
                status, null);

        log.info("AI chat completed: userId={}, conversationId={}, model={}, duration={}ms, status={}",
                 userId, conversation.getId(), llmResponse.model(), llmResponse.latencyMs(), status);

        // 7. Return response
        return buildChatResponse(conversation.getId(), assistantMsg, llmResponse);
    }

    private void persistAuditRecord(
            User user, Conversation conversation, String model,
            Integer promptTokens, Integer completionTokens, Integer totalTokens,
            Long latencyMs, AiRequestStatus status, String errorMessage) {

        AiRequest record = AiRequest.builder()
                .requestId(UUID.randomUUID())
                .user(user)
                .conversation(conversation)
                .model(model)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .latencyMs(latencyMs)
                .status(status)
                .errorMessage(errorMessage)
                .build();

        aiRequestRepository.save(record);
    }

    private ChatResponse buildChatResponse(Long conversationId, Message assistantMsg, LlmResponse r) {
        ChatResponse.TokenUsage usage = null;
        if (r.hasUsage()) {
            usage = ChatResponse.TokenUsage.builder()
                    .promptTokens(r.promptTokens())
                    .completionTokens(r.completionTokens())
                    .totalTokens(r.totalTokens())
                    .build();
        }

        MessageResponse msgResponse = MessageResponse.builder()
                .id(assistantMsg.getId())
                .role(assistantMsg.getRole())
                .content(assistantMsg.getContent())
                .tokenCount(assistantMsg.getTokenCount())
                .createdAt(assistantMsg.getCreatedAt())
                .build();

        return ChatResponse.builder()
                .conversationId(conversationId)
                .message(msgResponse)
                .usage(usage)
                .requestId(UUID.randomUUID().toString())
                .model(r.model())
                .latencyMs(r.latencyMs())
                .build();
    }
}
