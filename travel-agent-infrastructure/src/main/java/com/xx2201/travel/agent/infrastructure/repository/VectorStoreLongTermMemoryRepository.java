package com.xx2201.travel.agent.infrastructure.repository;

import com.xx2201.travel.agent.domain.model.valobj.LongTermMemoryItem;
import com.xx2201.travel.agent.domain.repository.LongTermMemoryRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@ConditionalOnBean(VectorStore.class)
public class VectorStoreLongTermMemoryRepository implements LongTermMemoryRepository {

    private final VectorStore vectorStore;

    public VectorStoreLongTermMemoryRepository(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void saveMemory(String conversationId, String category, String content, Map<String, Object> metadata) {
        String id = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        Map<String, Object> mergedMetadata = new java.util.LinkedHashMap<>();
        mergedMetadata.put("conversationId", conversationId);
        mergedMetadata.put("category", category);
        mergedMetadata.put("createdAt", createdAt.toString());
        if (metadata != null) {
            mergedMetadata.putAll(metadata);
        }
        vectorStore.add(List.of(new Document(id, content, mergedMetadata)));
    }

    @Override
    public List<LongTermMemoryItem> searchRelevant(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(limit)
                        .similarityThreshold(0.0)
                        .build()
        );
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        return documents.stream()
                .map(this::toMemoryItem)
                .toList();
    }

    private LongTermMemoryItem toMemoryItem(Document document) {
        Map<String, Object> metadata = document.getMetadata() == null ? Map.of() : document.getMetadata();
        return new LongTermMemoryItem(
                document.getId(),
                stringValue(metadata.get("conversationId")),
                stringValue(metadata.get("category")),
                document.getText(),
                metadata,
                parseInstant(metadata.get("createdAt"))
        );
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private Instant parseInstant(Object value) {
        if (value == null) {
            return Instant.now();
        }
        try {
            return Instant.parse(value.toString());
        }
        catch (Exception ignored) {
            return Instant.now();
        }
    }
}