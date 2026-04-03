package com.xx2201.travel.agent.infrastructure.repository;

import com.xx2201.travel.agent.domain.model.valobj.TravelKnowledgeRetrievalResult;
import com.xx2201.travel.agent.domain.model.valobj.TravelKnowledgeSnippet;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TravelKnowledgeVectorStoreRepositoryTest {

    @Test
    void upsertStoresNormalizedFilterMetadataAlongsideDisplayValues() {
        VectorStore vectorStore = mock(VectorStore.class);
        TravelKnowledgeVectorStoreRepository repository = new TravelKnowledgeVectorStoreRepository(vectorStore);

        repository.upsert(List.of(
                new TravelKnowledgeSnippet(
                        "Hangzhou",
                        "hotel",
                        "West Lake stay",
                        "Stay near West Lake for easy access.",
                        List.of("hangzhou", "hotel"),
                        "seed",
                        "hotel_area",
                        42
                )
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        Document document = captor.getValue().getFirst();

        assertEquals("hangzhou", document.getMetadata().get("city"));
        assertEquals("hotel", document.getMetadata().get("topic"));
        assertEquals("Hangzhou", document.getMetadata().get("displayCity"));
        assertEquals("hotel_area", document.getMetadata().get("schemaSubtype"));
        assertEquals("Hangzhou", document.getMetadata().get("cityAliases"));
        assertEquals("outdoors", document.getMetadata().get("tripStyleTags"));
    }

    @Test
    void retrieveMapsDocumentsBackToKnowledgeSnippetsAndBuildsStructuredFilter() {
        VectorStore vectorStore = mock(VectorStore.class);
        TravelKnowledgeVectorStoreRepository repository = new TravelKnowledgeVectorStoreRepository(vectorStore);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document(
                        "doc-1",
                        "Hefang Street cluster\nCluster evening food stops around Hefang Street.",
                        Map.of(
                                "city", "Hangzhou",
                                "topic", "food",
                                "displayCity", "Hangzhou",
                                "displayTopic", "food",
                                "title", "Hefang Street cluster",
                                "source", "seed",
                                "tags", "hangzhou,food,hefang"
                        )
                )
        ));

        TravelKnowledgeRetrievalResult results = repository.retrieve("Hangzhou", List.of("food"), "hangzhou food trip", 3);

        assertEquals(1, results.selections().size());
        assertEquals("vector-store", results.retrievalSource());
        assertEquals("Hangzhou", results.selections().get(0).city());
        assertEquals("food", results.selections().get(0).topic());
        assertEquals("Hefang Street cluster", results.selections().get(0).title());
        assertTrue(results.selections().get(0).content().contains("Cluster evening food stops"));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();
        assertTrue(request.hasFilterExpression());
        assertNotNull(request.getFilterExpression());
        assertEquals(Math.max(3 * 6, 18), request.getTopK());
    }
}
