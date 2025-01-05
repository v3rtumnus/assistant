package at.altenburger.assistant.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.autoconfigure.chat.client.ChatClientBuilderConfigurer;
import org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AssistantConfig {

    @Bean
    @ConditionalOnMissingBean
    public PgVectorStore vectorStore(JdbcTemplate jdbcTemplate, OllamaEmbeddingModel embeddingModel, PgVectorStoreProperties properties, ObjectProvider<ObservationRegistry> observationRegistry, ObjectProvider<VectorStoreObservationConvention> customObservationConvention, BatchingStrategy batchingStrategy) {
        boolean initializeSchema = properties.isInitializeSchema();
        return (new PgVectorStore.Builder(jdbcTemplate, embeddingModel)).withSchemaName(properties.getSchemaName()).withVectorTableName(properties.getTableName()).withVectorTableValidationsEnabled(properties.isSchemaValidation()).withDimensions(properties.getDimensions()).withDistanceType(properties.getDistanceType()).withRemoveExistingVectorStoreTable(properties.isRemoveExistingVectorStoreTable()).withIndexType(properties.getIndexType()).withInitializeSchema(initializeSchema).withObservationRegistry((ObservationRegistry) observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP)).withSearchObservationConvention(customObservationConvention.getIfAvailable(() -> null)).withBatchingStrategy(batchingStrategy).withMaxDocumentBatchSize(properties.getMaxDocumentBatchSize()).build();
    }

    @Bean
    @Scope("prototype")
    ChatClient.Builder openAiChatClientBuilder(ChatClientBuilderConfigurer chatClientBuilderConfigurer,
                                               @Qualifier("openAiChatModel") ChatModel chatModel) {
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        return chatClientBuilderConfigurer.configure(builder);
    }

}
