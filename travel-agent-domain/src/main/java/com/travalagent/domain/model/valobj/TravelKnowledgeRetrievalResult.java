package com.travalagent.domain.model.valobj;

import java.util.List;

public record TravelKnowledgeRetrievalResult(
        String destination,
        List<String> inferredTopics,
        List<String> inferredTripStyles,
        String retrievalSource,
        List<TravelKnowledgeSelection> selections
) {

    public TravelKnowledgeRetrievalResult {
        inferredTopics = inferredTopics == null ? List.of() : List.copyOf(inferredTopics);
        inferredTripStyles = inferredTripStyles == null ? List.of() : List.copyOf(inferredTripStyles);
        selections = selections == null ? List.of() : List.copyOf(selections);
    }

    public TravelKnowledgeRetrievalResult(
            String destination,
            List<String> inferredTopics,
            String retrievalSource,
            List<TravelKnowledgeSelection> selections
    ) {
        this(destination, inferredTopics, List.of(), retrievalSource, selections);
    }

    public static TravelKnowledgeRetrievalResult empty(String destination, List<String> inferredTopics, String retrievalSource) {
        return new TravelKnowledgeRetrievalResult(destination, inferredTopics, List.of(), retrievalSource, List.of());
    }

    public static TravelKnowledgeRetrievalResult empty(String destination, List<String> inferredTopics, List<String> inferredTripStyles, String retrievalSource) {
        return new TravelKnowledgeRetrievalResult(destination, inferredTopics, inferredTripStyles, retrievalSource, List.of());
    }

    public List<TravelKnowledgeSnippet> snippets() {
        return selections.stream()
                .map(TravelKnowledgeSelection::toSnippet)
                .toList();
    }
}
