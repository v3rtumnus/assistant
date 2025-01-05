package at.altenburger.assistant.service;

import at.altenburger.assistant.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    @Value("classpath:prompts/context.st")
    private Resource systemContextPrompt;

    private final DocumentRepository documentRepository;

    public Prompt generatePromptFromClientPrompt(String clientPrompt) {
        List<Document> docs = documentRepository.similaritySearch(clientPrompt);
        Message systemMessage = getSystemMessage(docs, clientPrompt);
        return new Prompt(systemMessage);
    }

    private Message getSystemMessage(List<Document> similarDocuments, String clientPrompt) {
        String documents = similarDocuments.stream().map(Document::getContent).collect(Collectors.joining("\n"));
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemContextPrompt);
        return systemPromptTemplate.createMessage(Map.of("documents", documents, "question", clientPrompt));
    }
}