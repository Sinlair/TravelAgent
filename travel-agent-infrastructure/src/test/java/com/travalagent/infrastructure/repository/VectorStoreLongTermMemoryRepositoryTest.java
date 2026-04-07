package com.travalagent.infrastructure.repository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorStoreLongTermMemoryRepositoryTest {

    @Test
    void savesMemoryAsDocumentWithMetadata() {
        VectorStore vectorStore = mock(VectorStore.class);
        VectorStoreLongTermMemoryRepository repository = new VectorStoreLongTermMemoryRepository(vectorStore);

        repository.saveMemory("conversation-1", "travel", "Visit West Lake", Map.of("days", 3));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        Document document = captor.getValue().get(0);
        assertEquals("Visit West Lake", document.getText());
        assertEquals("conversation-1", document.getMetadata().get("conversationId"));
        assertEquals("travel", document.getMetadata().get("category"));
        assertEquals(3, document.getMetadata().get("days"));
    }

    @Test
    void mapsSimilaritySearchResultsBackToDomainItems() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("doc-1", "Remember Hangzhou budget", Map.of(
                        "conversationId", "conversation-1",
                        "category", "travel",
                        "createdAt", "2026-04-01T00:00:00Z",
                        "budget", "2000"
                ))
        ));

        VectorStoreLongTermMemoryRepository repository = new VectorStoreLongTermMemoryRepository(vectorStore);
        var result = repository.searchRelevant("hangzhou", 3);

        assertEquals(1, result.size());
        assertEquals("doc-1", result.get(0).id());
        assertEquals("conversation-1", result.get(0).conversationId());
        assertEquals("travel", result.get(0).category());
        assertEquals("Remember Hangzhou budget", result.get(0).content());
        assertFalse(result.get(0).metadata().isEmpty());
    }
}