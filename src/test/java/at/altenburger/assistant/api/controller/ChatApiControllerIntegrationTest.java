package at.altenburger.assistant.api.controller;

import at.altenburger.assistant.config.TestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import at.altenburger.assistant.api.dto.ChatRequest;
import at.altenburger.assistant.api.dto.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestConfig.class)
@ActiveProfiles("test")
@DisplayName("ChatApiController Integration Tests")
class ChatApiControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    @DisplayName("Should reject empty message")
    void shouldRejectEmptyMessage() throws Exception {
        // Arrange
        ChatRequest request = new ChatRequest();
        request.setMessage("");
        
        // Act & Assert
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }
    
    @Test
    @DisplayName("Should reject message exceeding max length")
    void shouldRejectTooLongMessage() throws Exception {
        // Arrange
        ChatRequest request = new ChatRequest();
        request.setMessage("a".repeat(5001));
        
        // Act & Assert
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    @DisplayName("Should return conversation ID in response")
    void shouldReturnConversationId() throws Exception {
        // Arrange
        ChatRequest request = new ChatRequest();
        request.setMessage("Hello");
        
        // Act
        MvcResult result = mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        
        // Assert
        String responseBody = result.getResponse().getContentAsString();
        ChatResponse response = objectMapper.readValue(responseBody, ChatResponse.class);
        
        assertThat(response.getConversationId()).isNotNull();
        assertThat(response.getTraceId()).isNotNull();
    }
    
    @Test
    @DisplayName("Should retrieve conversation by ID")
    void shouldRetrieveConversation() throws Exception {
        // Arrange - Create a conversation
        ChatRequest request = new ChatRequest();
        request.setMessage("Test message");
        
        MvcResult chatResult = mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = chatResult.getResponse().getContentAsString();
        ChatResponse chatResponse = objectMapper.readValue(responseBody, ChatResponse.class);
        String conversationId = chatResponse.getConversationId();
        
        // Act & Assert
        mockMvc.perform(get("/api/conversation/" + conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conversationId))
                .andExpect(jsonPath("$.messages").isArray());
    }
}
