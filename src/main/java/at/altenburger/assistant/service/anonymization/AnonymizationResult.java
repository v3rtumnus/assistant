package at.altenburger.assistant.service.anonymization;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Result of anonymization containing the anonymized text and mappings.
 */
public class AnonymizationResult {

    private final String originalText;
    private final String anonymizedText;
    private final Map<String, AnonymizedEntity> placeholderToEntity;
    private final Map<String, String> entityToPlaceholder;

    public AnonymizationResult(String originalText, String anonymizedText,
                                Map<String, AnonymizedEntity> placeholderToEntity) {
        this.originalText = originalText;
        this.anonymizedText = anonymizedText;
        this.placeholderToEntity = Collections.unmodifiableMap(new HashMap<>(placeholderToEntity));
        this.entityToPlaceholder = placeholderToEntity.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getValue().getOriginalValue(),
                        Map.Entry::getKey,
                        (existing, replacement) -> existing // Keep first mapping if duplicates
                ));
    }

    public String getOriginalText() {
        return originalText;
    }

    public String getAnonymizedText() {
        return anonymizedText;
    }

    public Map<String, AnonymizedEntity> getPlaceholderToEntity() {
        return placeholderToEntity;
    }

    public String getOriginalValue(String placeholder) {
        AnonymizedEntity entity = placeholderToEntity.get(placeholder);
        return entity != null ? entity.getOriginalValue() : placeholder;
    }

    public String getPlaceholder(String originalValue) {
        return entityToPlaceholder.get(originalValue);
    }

    public boolean hasAnonymizedEntities() {
        return !placeholderToEntity.isEmpty();
    }

    public int getEntityCount() {
        return placeholderToEntity.size();
    }

    public Set<EntityType> getDetectedEntityTypes() {
        return placeholderToEntity.values().stream()
                .map(AnonymizedEntity::getType)
                .collect(Collectors.toSet());
    }

    /**
     * De-anonymize a text by replacing all placeholders with original values.
     */
    public String deanonymize(String text) {
        if (text == null || placeholderToEntity.isEmpty()) {
            return text;
        }

        String result = text;
        for (Map.Entry<String, AnonymizedEntity> entry : placeholderToEntity.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue().getOriginalValue());
        }
        return result;
    }

    /**
     * Anonymize new text using the existing entity mappings.
     * This is useful for anonymizing MCP tool responses that may contain
     * the same sensitive values as the original query.
     */
    public String anonymizeWithExistingMappings(String text) {
        if (text == null || entityToPlaceholder.isEmpty()) {
            return text;
        }

        String result = text;
        // Sort by length descending to replace longer matches first
        // (e.g., "living room" before "room")
        var sortedEntries = entityToPlaceholder.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
                .toList();

        for (Map.Entry<String, String> entry : sortedEntries) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Represents a single anonymized entity with its metadata.
     */
    public static class AnonymizedEntity {
        private final String originalValue;
        private final EntityType type;
        private final int startPosition;
        private final int endPosition;
        private final double confidence;

        public AnonymizedEntity(String originalValue, EntityType type,
                                 int startPosition, int endPosition, double confidence) {
            this.originalValue = originalValue;
            this.type = type;
            this.startPosition = startPosition;
            this.endPosition = endPosition;
            this.confidence = confidence;
        }

        public String getOriginalValue() {
            return originalValue;
        }

        public EntityType getType() {
            return type;
        }

        public int getStartPosition() {
            return startPosition;
        }

        public int getEndPosition() {
            return endPosition;
        }

        public double getConfidence() {
            return confidence;
        }

        @Override
        public String toString() {
            return String.format("AnonymizedEntity{type=%s, value='%s', confidence=%.2f}",
                    type, originalValue, confidence);
        }
    }

    @Override
    public String toString() {
        return String.format("AnonymizationResult{entities=%d, types=%s}",
                getEntityCount(), getDetectedEntityTypes());
    }
}