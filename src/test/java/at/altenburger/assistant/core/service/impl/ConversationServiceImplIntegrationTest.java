package at.altenburger.assistant.core.service.impl;

import at.altenburger.assistant.domain.entity.ConversationEntity;
import at.altenburger.assistant.domain.entity.MessageEntity;
import at.altenburger.assistant.domain.repository.ConversationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ConversationServiceImpl.class)
@ActiveProfiles("test")
@DisplayName("ConversationService Integration Tests")
class ConversationServiceImplIntegrationTest {

    @Autowired
    private ConversationServiceImpl conversationService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Test
    @DisplayName("Should create new conversation")
    void shouldCreateNewConversation() {
        // Act
        ConversationEntity conversation = conversationService.createConversation();

        // Assert
        assertThat(conversation).isNotNull();
        assertThat(conversation.getId()).isNotNull();
        assertThat(conversation.getCreatedAt()).isNotNull();
        assertThat(conversation.getMessages()).isEmpty();
    }

    @Test
    @DisplayName("Should add message to conversation")
    void shouldAddMessageToConversation() {
        // Arrange
        ConversationEntity conversation = conversationService.createConversation();
        String conversationId = conversation.getId();

        // Act
        MessageEntity message = conversationService.addMessage(
                conversationId,
                "user",
                "Hello!",
                "trace-123"
        );

        // Assert
        assertThat(message).isNotNull();
        assertThat(message.getRole()).isEqualTo("user");
        assertThat(message.getContent()).isEqualTo("Hello!");
        assertThat(message.getTraceId()).isEqualTo("trace-123");
    }

    @Test
    @DisplayName("Should retrieve conversation with messages")
    void shouldRetrieveConversationWithMessages() {
        // Arrange
        ConversationEntity conversation = conversationService.createConversation();
        String conversationId = conversation.getId();

        conversationService.addMessage(conversationId, "user", "Message 1", "trace-1");
        conversationService.addMessage(conversationId, "assistant", "Response 1", "trace-2");

        // Act
        ConversationEntity retrieved = conversationService.getConversationWithMessages(conversationId);

        // Assert
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getMessages()).hasSize(2);
        assertThat(retrieved.getMessages().get(0).getRole()).isEqualTo("user");
        assertThat(retrieved.getMessages().get(1).getRole()).isEqualTo("assistant");
    }

    @Test
    @DisplayName("Should persist conversation to database")
    void shouldPersistConversation() {
        // Arrange
        ConversationEntity conversation = conversationService.createConversation();
        conversationService.addMessage(conversation.getId(), "user", "Test", null);

        // Act
        ConversationEntity persisted = conversationRepository.findById(conversation.getId()).orElse(null);

        // Assert
        assertThat(persisted).isNotNull();
        assertThat(persisted.getMessages()).hasSize(1);
    }
}
