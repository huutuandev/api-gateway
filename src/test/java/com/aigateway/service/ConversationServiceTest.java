package com.aigateway.service;

import com.aigateway.dto.request.AddMessageRequest;
import com.aigateway.dto.request.CreateConversationRequest;
import com.aigateway.dto.response.ConversationDetailResponse;
import com.aigateway.dto.response.ConversationResponse;
import com.aigateway.dto.response.MessageResponse;
import com.aigateway.entity.Conversation;
import com.aigateway.entity.Message;
import com.aigateway.entity.User;
import com.aigateway.enums.MessageRole;
import com.aigateway.enums.UserRole;
import com.aigateway.exception.ResourceNotFoundException;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock ConversationRepository conversationRepository;
    @Mock MessageRepository      messageRepository;
    @Mock UserRepository         userRepository;

    @InjectMocks ConversationService conversationService;

    private User testUser;
    private Conversation testConversation;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("$2a$hash")
                .role(UserRole.USER)
                .enabled(true)
                .build();

        testConversation = Conversation.builder()
                .id(10L)
                .user(testUser)
                .title("My Chat")
                .messages(new ArrayList<>())
                .build();

        // Simulate @PrePersist
        testConversation.setCreatedAt(LocalDateTime.now());
        testConversation.setUpdatedAt(LocalDateTime.now());
    }

    // ── createConversation() ─────────────────────────────────────────────────

    @Nested @DisplayName("createConversation()")
    class CreateConversationTests {

        @Test @DisplayName("success → ConversationResponse with correct userId and title")
        void create_success() {
            CreateConversationRequest req = new CreateConversationRequest("My Chat");

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(conversationRepository.save(any())).thenReturn(testConversation);

            ConversationResponse resp = conversationService.createConversation(1L, req);

            assertThat(resp.getId()).isEqualTo(10L);
            assertThat(resp.getUserId()).isEqualTo(1L);
            assertThat(resp.getTitle()).isEqualTo("My Chat");
            assertThat(resp.getCreatedAt()).isNotNull();
        }

        @Test @DisplayName("success with null title → saves null title")
        void create_nullTitle() {
            CreateConversationRequest req = new CreateConversationRequest(null);

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            Conversation noTitle = Conversation.builder()
                    .id(11L).user(testUser).title(null).messages(new ArrayList<>()).build();
            noTitle.setCreatedAt(LocalDateTime.now());
            noTitle.setUpdatedAt(LocalDateTime.now());

            when(conversationRepository.save(any())).thenReturn(noTitle);

            ConversationResponse resp = conversationService.createConversation(1L, req);

            assertThat(resp.getTitle()).isNull();

            ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationRepository).save(captor.capture());
            assertThat(captor.getValue().getTitle()).isNull();
        }

        @Test @DisplayName("user not found → ResourceNotFoundException")
        void create_userNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    conversationService.createConversation(99L, new CreateConversationRequest("X")))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(conversationRepository, never()).save(any());
        }
    }

    // ── addMessage() ─────────────────────────────────────────────────────────

    @Nested @DisplayName("addMessage()")
    class AddMessageTests {

        @Test @DisplayName("success → MessageResponse with correct role and content")
        void addMessage_success() {
            AddMessageRequest req = new AddMessageRequest(MessageRole.USER, "Hello AI");

            when(conversationRepository.findByIdAndUserId(10L, 1L))
                    .thenReturn(Optional.of(testConversation));

            Message saved = Message.builder()
                    .id(100L)
                    .conversation(testConversation)
                    .role(MessageRole.USER)
                    .content("Hello AI")
                    .build();
            saved.setCreatedAt(LocalDateTime.now());

            when(messageRepository.save(any())).thenReturn(saved);

            MessageResponse resp = conversationService.addMessage(1L, 10L, req);

            assertThat(resp.getId()).isEqualTo(100L);
            assertThat(resp.getRole()).isEqualTo(MessageRole.USER);
            assertThat(resp.getContent()).isEqualTo("Hello AI");
            assertThat(resp.getCreatedAt()).isNotNull();
        }

        @Test @DisplayName("conversation not found or not owned → ResourceNotFoundException")
        void addMessage_conversationNotFound() {
            when(conversationRepository.findByIdAndUserId(10L, 2L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    conversationService.addMessage(2L, 10L,
                            new AddMessageRequest(MessageRole.USER, "Hi")))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(messageRepository, never()).save(any());
        }

        @Test @DisplayName("ASSISTANT role → saved correctly")
        void addMessage_assistantRole() {
            AddMessageRequest req = new AddMessageRequest(MessageRole.ASSISTANT, "I can help you");

            when(conversationRepository.findByIdAndUserId(10L, 1L))
                    .thenReturn(Optional.of(testConversation));

            Message saved = Message.builder()
                    .id(101L).conversation(testConversation)
                    .role(MessageRole.ASSISTANT).content("I can help you").build();
            saved.setCreatedAt(LocalDateTime.now());
            when(messageRepository.save(any())).thenReturn(saved);

            MessageResponse resp = conversationService.addMessage(1L, 10L, req);
            assertThat(resp.getRole()).isEqualTo(MessageRole.ASSISTANT);
        }
    }

    // ── getHistory() ─────────────────────────────────────────────────────────

    @Nested @DisplayName("getHistory()")
    class GetHistoryTests {

        @Test @DisplayName("success → ConversationDetailResponse with messages ordered by createdAt")
        void getHistory_success() {
            LocalDateTime t1 = LocalDateTime.now().minusMinutes(5);
            LocalDateTime t2 = LocalDateTime.now();

            Message m1 = Message.builder().id(1L).conversation(testConversation)
                    .role(MessageRole.USER).content("Hello").build();
            m1.setCreatedAt(t1);

            Message m2 = Message.builder().id(2L).conversation(testConversation)
                    .role(MessageRole.ASSISTANT).content("Hi there!").build();
            m2.setCreatedAt(t2);

            testConversation.setMessages(List.of(m1, m2));

            when(conversationRepository.findWithMessagesByIdAndUserId(10L, 1L))
                    .thenReturn(Optional.of(testConversation));

            ConversationDetailResponse resp = conversationService.getHistory(1L, 10L);

            assertThat(resp.getId()).isEqualTo(10L);
            assertThat(resp.getMessages()).hasSize(2);
            assertThat(resp.getMessages().get(0).getRole()).isEqualTo(MessageRole.USER);
            assertThat(resp.getMessages().get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);
        }

        @Test @DisplayName("conversation not found → ResourceNotFoundException")
        void getHistory_notFound() {
            when(conversationRepository.findWithMessagesByIdAndUserId(10L, 2L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> conversationService.getHistory(2L, 10L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test @DisplayName("empty conversation → returns empty messages list")
        void getHistory_noMessages() {
            when(conversationRepository.findWithMessagesByIdAndUserId(10L, 1L))
                    .thenReturn(Optional.of(testConversation)); // messages is ArrayList() (empty)

            ConversationDetailResponse resp = conversationService.getHistory(1L, 10L);

            assertThat(resp.getMessages()).isEmpty();
        }
    }

    // ── listConversations() ───────────────────────────────────────────────────

    @Nested @DisplayName("listConversations()")
    class ListConversationsTests {

        @Test @DisplayName("returns list ordered newest first")
        void list_success() {
            Conversation c2 = Conversation.builder()
                    .id(20L).user(testUser).title("Second").messages(new ArrayList<>()).build();
            c2.setCreatedAt(LocalDateTime.now());
            c2.setUpdatedAt(LocalDateTime.now());

            when(conversationRepository.findByUserIdOrderByUpdatedAtDesc(1L))
                    .thenReturn(List.of(c2, testConversation));

            List<ConversationResponse> list = conversationService.listConversations(1L);

            assertThat(list).hasSize(2);
            assertThat(list.get(0).getId()).isEqualTo(20L); // newest first
        }

        @Test @DisplayName("no conversations → returns empty list")
        void list_empty() {
            when(conversationRepository.findByUserIdOrderByUpdatedAtDesc(1L))
                    .thenReturn(List.of());

            assertThat(conversationService.listConversations(1L)).isEmpty();
        }
    }
}
