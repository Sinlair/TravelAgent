package com.xx2201.travel.agent.domain.model.valobj;

import java.util.List;

public record TravelKnowledgeSelection(
        String city,
        String topic,
        String title,
        String content,
        List<String> tags,
        String source,
        String schemaSubtype,
        Integer qualityScore,
        List<String> matchedTripStyles,
        String matchedCity,
        String matchedTopic
) {

    public TravelKnowledgeSelection {
        tags = tags == null ? List.of() : List.copyOf(tags);
        matchedTripStyles = matchedTripStyles == null ? List.of() : List.copyOf(matchedTripStyles);
    }

    public TravelKnowledgeSelection(
            String city,
            String topic,
            String title,
            String content,
            List<String> tags,
            String source,
            String matchedCity,
            String matchedTopic
    ) {
        this(city, topic, title, content, tags, source, null, null, List.of(), matchedCity, matchedTopic);
    }

    public TravelKnowledgeSelection(
            String city,
            String topic,
            String title,
            String content,
            List<String> tags,
            String source,
            String schemaSubtype,
            Integer qualityScore,
            String matchedCity,
            String matchedTopic
    ) {
        this(city, topic, title, content, tags, source, schemaSubtype, qualityScore, List.of(), matchedCity, matchedTopic);
    }

    public TravelKnowledgeSnippet toSnippet() {
        return new TravelKnowledgeSnippet(city, topic, title, content, tags, source, schemaSubtype, qualityScore, List.of(), List.of());
    }
}
