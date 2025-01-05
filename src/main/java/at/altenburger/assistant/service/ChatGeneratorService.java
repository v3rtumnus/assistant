package at.altenburger.assistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ChatGeneratorService {
    @Value("classpath:prompts/default.st")
    private Resource systemDefaultPrompt;

    @Value("#{${embeddings.pii.filter}}")
    private Map<String,String> piiFilter;

    private final ChatClient chatClient;
    private final RagService ragService;

    public ChatGeneratorService(ChatClient.Builder chatClientBuilder, RagService ragService) {
        this.chatClient = chatClientBuilder.build();
        this.ragService = ragService;
    }

    public String generate(String message) {
        log.info("Got request for message: {}", message);
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemDefaultPrompt);
        Prompt queryMessage = new Prompt(systemPromptTemplate.createMessage(Map.of("query", message)));

        String response = chatClient.prompt(queryMessage)
                .call()
                .content();

        switch (response) {
            case "smart home" -> {
                log.info("Classified as smart home command");
                //TODO smart home
                return "Will be implemented";
            }
            case "context" -> {
                log.info("Classified as command requiring more context");
                Prompt prompt = new Prompt(filterPiiData(ragService.generatePromptFromClientPrompt(message).getContents(), piiFilter));
                String answer = chatClient.prompt(prompt)
                        .call()
                        .content();
                return defilterPiiData(answer);
            }
            default -> {
                log.info("Classified as generic question");
                return response;
            }
        }
    }

    private String defilterPiiData(String content) {
        //use the filter method but with reversed replacements
        return filterPiiData(content, reverseMap(piiFilter));
    }

    private String filterPiiData(String message, Map<String, String> replacements) {
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            // Replace all occurrences of the key with the value
            message = message.replace(entry.getKey(), entry.getValue());
        }
        return message;
    }

    private <K, V> Map<V, K> reverseMap(Map<K, V> originalMap) {
        Map<V, K> reversedMap = new HashMap<>();

        for (Map.Entry<K, V> entry : originalMap.entrySet()) {
            reversedMap.put(entry.getValue(), entry.getKey());
        }

        return reversedMap;
    }
}