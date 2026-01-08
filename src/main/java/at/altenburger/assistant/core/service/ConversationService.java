package at.altenburger.assistant.core.service;

import at.altenburger.assistant.domain.entity.ConversationEntity;
import at.altenburger.assistant.domain.entity.MessageEntity;

/**
 * Service for managing conversations and messages
 */
public interface ConversationService {

    /**
     * Create a new conversation
     */
    ConversationEntity createConversation();

    /**
     * Get a conversation by ID, creating if it doesn't exist
     */
    ConversationEntity getOrCreateConversation(String id);

    /**
     * Add a message to a conversation
     */
    MessageEntity addMessage(String conversationId, String role, String content, String traceId);

    /**
     * Get a conversation with all its messages
     */
    ConversationEntity getConversationWithMessages(String id);
}