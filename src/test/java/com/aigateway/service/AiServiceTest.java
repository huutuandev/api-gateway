package com.aigateway.service;

import com.aigateway.config.AiProperties;
import com.aigateway.dto.request.ChatRequest;
import com.aigateway.dto.response.ChatResponse;
import com.aigateway.entity.Conversation;
import com.aigateway.entity.Message;
import com.aigateway.entity.User;
import com.aigateway.enums.AiRequestStatus;
import com.aigateway.enums.MessageRole;
import com.aigateway.enums.UserRole;
import com.aigateway.exception.ResourceNotFoundException;
import com.aigateway.llm.LlmException;
import com.aigateway.llm.LlmProvider;
import com.aigateway.llm.LlmResponse;
import com.aigateway.repository.AiRequestRepository;
import com.aigateway.repository.ConversationRepository;
import com.aigateway.repository.MessageRepository;
import com.aigateway.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiServiceTest {

    @Mock LlmProvider           llmProvider;
    @Mock AiRequestRepository   aiRequestRepository;
    @Mock UserRepository        userRepository;
    @Mock ConversationRepository conversationRepository;
    @Mock MessageRepository      messageRepository;
    @Mock AiProperties           aiProperties;

    @InjectMocks AiService aiService;

    private User testUser;
    private Conversation existingConversation;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L).email("user@example.com")
                .role(UserRole.USER).enabled(true).build();

        existingConversation = Conversation.builder()
                .id(10L).user(testUser).title("Existing Chat")
                .messages(new ArrayList<>()).build();

        when(aiProperties.getDefaultModel()).thenReturn("llama-3.3-70b-versatile");
        
        // Mock messageRepository.save to return the passed argument (simulate DB generated ID if needed)
        when(messageRepository.save(any(Message.class))).thenAnswer(i -> {
            Message m = i.getArgument(0);
            if (m.getId() == null) m.setId(999L);
            return m;
        });
        
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(i -> {
            Conversation c = i.getArgument(0);
            if (c.getId() == null) c.setId(20L);
            return c;
        });
    }

    private ChatRequest buildRequest(Long conversationId, String content) {
        return new ChatRequest(conversationId, "gpt-4o-mini", content);
    }

    // ── chat() — success ──────────────────────────────────────────────────────

    @Nested @DisplayName("chat() — success")
    class SuccessTests {

        @Test @DisplayName("new conversation created when conversationId is null")
        void chat_newConversation() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(llmProvider.complete(anyList(), anyString()))
                    .thenReturn(new LlmResponse("gpt-4o-mini", "Hello!", 10, 5, 15, 250L));

            ChatResponse response = aiService.chat(1L, buildRequest(null, "Hi"));

            // Verification: Conversation created
            ArgumentCaptor<Conversation> convCaptor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationRepository).save(convCaptor.capture());
            assertThat(convCaptor.getValue().getTitle()).isEqualTo("Hi");
            assertThat(response.getConversationId()).isEqualTo(20L);

            // Verification: Messages saved
            verify(messageRepository, times(2)).save(any(Message.class));

            // Verification: Response structure
            assertThat(response.getMessage().getContent()).isEqualTo("Hello!");
            assertThat(response.getMessage().getRole()).isEqualTo(MessageRole.ASSISTANT);
            assertThat(response.getUsage()).isNotNull();
            assertThat(response.getUsage().getTotalTokens()).isEqualTo(15);
        }

        @Test @DisplayName("existing conversation fetched when conversationId is provided")
        void chat_existingConversation() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(conversationRepository.findWithMessagesByIdAndUserId(10L, 1L))
                    .thenReturn(Optional.of(existingConversation));
            when(llmProvider.complete(anyList(), anyString()))
                    .thenReturn(new LlmResponse("gpt-4o-mini", "Hello!", 10, 5, 15, 250L));

            ChatResponse response = aiService.chat(1L, buildRequest(10L, "Hi"));

            // Verification: Conversation NOT saved again, just used
            verify(conversationRepository, never()).save(any(Conversation.class));
            assertThat(response.getConversationId()).isEqualTo(10L);

            // Verification: Messages saved
            verify(messageRepository, times(2)).save(any(Message.class));
        }

        @Test @DisplayName("persists AiRequest audit record on success")
        void chat_persistsAuditRecord() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(conversationRepository.findWithMessagesByIdAndUserId(10L, 1L))
                    .thenReturn(Optional.of(existingConversation));

            com.aigateway.entity.AiRequest[] savedRecord = {null};
            when(aiRequestRepository.save(any())).thenAnswer(inv -> {
                savedRecord[0] = inv.getArgument(0);
                return savedRecord[0];
            });

            when(llmProvider.complete(anyList(), anyString()))
                    .thenReturn(new LlmResponse("gpt-4o-mini", "OK", 8, 4, 12, 150L));

            aiService.chat(1L, buildRequest(10L, "Test"));

            assertThat(savedRecord[0]).isNotNull();
            assertThat(savedRecord[0].getStatus()).isEqualTo(AiRequestStatus.SUCCESS);
            assertThat(savedRecord[0].getPromptTokens()).isEqualTo(8);
            assertThat(savedRecord[0].getCompletionTokens()).isEqualTo(4);
            assertThat(savedRecord[0].getTotalTokens()).isEqualTo(12);
        }
    }

    // ── chat() — failures ─────────────────────────────────────────────────────

    @Nested @DisplayName("chat() — failures")
    class FailureTests {

        @Test @DisplayName("LLM provider failure → persists FAILED record then re-throws")
        void chat_llmFailed() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(conversationRepository.findWithMessagesByIdAndUserId(10L, 1L))
                    .thenReturn(Optional.of(existingConversation));

            when(llmProvider.complete(anyList(), anyString()))
                    .thenThrow(LlmException.httpError("openai", 500, "Internal Server Error"));

            assertThatThrownBy(() -> aiService.chat(1L, buildRequest(10L, "Hi")))
                    .isInstanceOf(LlmException.class);

            // User message is saved before LLM call
            verify(messageRepository, times(1)).save(any(Message.class));

            // Audit record must still be saved
            ArgumentCaptor<com.aigateway.entity.AiRequest> captor =
                    ArgumentCaptor.forClass(com.aigateway.entity.AiRequest.class);
            verify(aiRequestRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(AiRequestStatus.FAILED);
        }

        @Test @DisplayName("User not found → ResourceNotFoundException, no LLM call")
        void chat_userNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> aiService.chat(99L, buildRequest(null, "Hi")))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(llmProvider);
            verifyNoInteractions(messageRepository);
        }
        
        @Test @DisplayName("Conversation not found → ResourceNotFoundException")
        void chat_conversationNotFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(conversationRepository.findWithMessagesByIdAndUserId(99L, 1L))
                    .thenReturn(Optional.empty());
                    
            assertThatThrownBy(() -> aiService.chat(1L, buildRequest(99L, "Hi")))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
                    
            verifyNoInteractions(llmProvider);
        }
    }

    // ── getUsageStats() ───────────────────────────────────────────────────────

    @Nested @DisplayName("getUsageStats()")
    class UsageTests {
        @Test @DisplayName("Returns aggregated usage correctly")
        void getUsageStats_success() {
            AiRequestRepository.UsageStatsProjection projection = mock(AiRequestRepository.UsageStatsProjection.class);
            when(projection.getRequests()).thenReturn(150L);
            when(projection.getTokens()).thenReturn(1000L);
            when(projection.getAverageLatencyMs()).thenReturn(450.5);
            when(projection.getErrorRate()).thenReturn(0.05);

            when(aiRequestRepository.getUsageStatsByUserId(1L)).thenReturn(projection);

            com.aigateway.dto.response.UsageResponse response = aiService.getUsageStats(1L);

            assertThat(response.getRequests()).isEqualTo(150L);
            assertThat(response.getTokens()).isEqualTo(1000L);
            assertThat(response.getAverageLatencyMs()).isEqualTo(450.5);
            assertThat(response.getErrorRate()).isEqualTo(0.05);
        }
    }
}
