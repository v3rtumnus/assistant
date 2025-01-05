package at.altenburger.assistant.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentRepository {

    private final VectorStore vectorStore;

    public void addDocuments(List<Document> docsToAdd) {
        vectorStore.add(docsToAdd);
    }

    public List<Document> similaritySearch(String prompt) {
        return vectorStore.similaritySearch(SearchRequest.query(prompt).withSimilarityThreshold(0.5));
    }

    public void deleteDocuments(List<String> idList) {
        vectorStore.delete(idList);
    }
}