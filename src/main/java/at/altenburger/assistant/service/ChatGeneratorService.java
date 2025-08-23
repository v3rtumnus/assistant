package at.altenburger.assistant.service;

import at.altenburger.assistant.config.LoggingMcpToolCallbackProvider;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ChatGeneratorService {
    private final ChatClient chatClient;

    public ChatGeneratorService(ChatClient.Builder chatClientBuilder,
                                List<McpSyncClient> mcpSyncClients,
                                @Value("${application.default.prompt}") String defaultPrompt) {
        this.chatClient = chatClientBuilder
                .defaultToolCallbacks(new LoggingMcpToolCallbackProvider(mcpSyncClients))
                .defaultSystem(defaultPrompt)
                .build();
    }

    public String generate(String message) {
        log.info("Got request for message: {}", message);

        return chatClient.prompt(message)
                .call()
                .content();
    }
}