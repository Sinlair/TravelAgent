package com.travalagent.domain.model.valobj;

import java.util.List;

public record TravelKnowledgeSnippet(
        String city,
        String topic,
        String title,
        String content,
        List<String> tags,
        String source,
        String schemaSubtype,
        Integer qualityScore,
        List<String> cityAliases,
        List<String> tripStyleTags
) {

    public TravelKnowledgeSnippet {
        tags = tags == null ? List.of() : List.copyOf(tags);
        cityAliases = cityAliases == null ? List.of() : List.copyOf(cityAliases);
        tripStyleTags = tripStyleTags == null ? List.of() : List.copyOf(tripStyleTags);
    }

    public TravelKnowledgeSnippet(
            String city,
            String topic,
            String title,
            String content,
            List<String> tags,
            String source
    ) {
        this(city, topic, title, content, tags, source, null, null, List.of(), List.of());
    }

    public TravelKnowledgeSnippet(
            String city,
            String topic,
            String title,
            String content,
            List<String> tags,
            String source,
            String schemaSubtype,
            Integer qualityScore
    ) {
        this(city, topic, title, content, tags, source, schemaSubtype, qualityScore, List.of(), List.of());
    }
}
