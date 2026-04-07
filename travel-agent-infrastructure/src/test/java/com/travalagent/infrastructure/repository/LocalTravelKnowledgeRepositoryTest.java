package com.travalagent.infrastructure.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travalagent.domain.model.valobj.TravelKnowledgeSnippet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalTravelKnowledgeRepositoryTest {

    private final LocalTravelKnowledgeRepository repository = new LocalTravelKnowledgeRepository(new ObjectMapper());

    @Test
    void searchReturnsDestinationSpecificKnowledge() {
        var results = repository.search("Hangzhou", List.of("food", "relaxed pace"), "plan a relaxed hangzhou food trip", 3);

        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(item -> item.city().equals("Hangzhou")));
        assertTrue(results.stream().allMatch(item -> item.topic().equals("food") || item.topic().equals("scenic")));
        assertTrue(results.stream().allMatch(item -> item.content() != null && !item.content().isBlank()));
        assertTrue(results.stream().allMatch(item -> item.qualityScore() == null || item.qualityScore() > 0));
        assertTrue(results.stream().allMatch(this::hasRetrievalMetadata));
    }

    @Test
    void canonicalDestinationResolvesChineseAlias() {
        assertEquals("Hangzhou", repository.canonicalDestination("杭州"));
    }

    private boolean hasRetrievalMetadata(TravelKnowledgeSnippet snippet) {
        assertNotNull(snippet.title());
        return snippet.schemaSubtype() == null || !snippet.schemaSubtype().isBlank();
    }
}
