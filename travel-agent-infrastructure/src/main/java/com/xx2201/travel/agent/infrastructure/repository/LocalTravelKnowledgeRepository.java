package com.xx2201.travel.agent.infrastructure.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xx2201.travel.agent.domain.model.valobj.TravelKnowledgeRetrievalResult;
import com.xx2201.travel.agent.domain.model.valobj.TravelKnowledgeSnippet;
import com.xx2201.travel.agent.domain.repository.TravelKnowledgeRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class LocalTravelKnowledgeRepository implements TravelKnowledgeRepository {

    private static final TypeReference<List<KnowledgeRecord>> RECORDS = new TypeReference<>() {
    };
    private static final String BASE_RESOURCE = "travel-knowledge.json";
    private static final String COLLECTED_RESOURCE = "travel-knowledge.collected.json";
    private static final String CLEANED_RESOURCE = "travel-knowledge.cleaned.json";

    private final List<TravelKnowledgeSnippet> snippets;
    private final Map<String, String> cityAliasLookup;

    public LocalTravelKnowledgeRepository(ObjectMapper objectMapper) {
        this.snippets = loadSnippets(objectMapper);
        this.cityAliasLookup = buildCityAliasLookup(this.snippets);
    }

    public List<TravelKnowledgeSnippet> all() {
        return snippets;
    }

    public String canonicalDestination(String destination) {
        if (destination == null || destination.isBlank()) {
            return destination;
        }
        return cityAliasLookup.getOrDefault(normalize(destination), destination.trim());
    }

    @Override
    public TravelKnowledgeRetrievalResult retrieve(String destination, List<String> preferences, String query, int limit) {
        String canonicalDestination = canonicalDestination(destination);
        TravelKnowledgeRetrievalSupport.RetrievalPlan plan = TravelKnowledgeRetrievalSupport.plan(canonicalDestination, preferences, query);
        if (plan.combinedQuery().isBlank() || limit <= 0) {
            return TravelKnowledgeRetrievalSupport.emptyResult(canonicalDestination, plan, "local-fallback");
        }

        List<TravelKnowledgeSnippet> rankedSnippets = snippets.stream()
                .filter(snippet -> TravelKnowledgeRetrievalSupport.matchesDestination(snippet, plan.normalizedDestination()))
                .filter(snippet -> TravelKnowledgeRetrievalSupport.matchesTopics(snippet.topic(), plan.inferredTopics()))
                .map(snippet -> new ScoredSnippet(snippet, score(snippet, plan)))
                .filter(item -> item.score() > 0)
                .sorted((left, right) -> Integer.compare(right.score(), left.score()))
                .map(ScoredSnippet::snippet)
                .toList();

        return TravelKnowledgeRetrievalSupport.buildResult(canonicalDestination, plan, "local-fallback", rankedSnippets, limit);
    }

    private int score(TravelKnowledgeSnippet snippet, TravelKnowledgeRetrievalSupport.RetrievalPlan plan) {
        TravelKnowledgeSnippet enriched = TravelKnowledgeRetrievalSupport.enrichSnippet(snippet);
        int score = 0;
        String city = normalize(enriched.city());
        String topic = normalize(enriched.topic());
        String title = normalize(enriched.title());
        String content = normalize(enriched.content());
        String tags = enriched.tags().stream().map(this::normalize).collect(Collectors.joining(" "));
        String aliases = enriched.cityAliases().stream().map(this::normalize).collect(Collectors.joining(" "));
        String tripStyles = enriched.tripStyleTags().stream().map(this::normalize).collect(Collectors.joining(" "));
        Set<String> terms = new HashSet<>();
        addTerms(terms, plan.combinedQuery());

        if (!plan.normalizedDestination().isBlank()) {
            if (city.equals(plan.normalizedDestination())) {
                score += 120;
            }
            else if (city.contains(plan.normalizedDestination()) || plan.normalizedDestination().contains(city) || aliases.contains(plan.normalizedDestination())) {
                score += 40;
            }
        }

        if (TravelKnowledgeRetrievalSupport.matchesTopics(enriched.topic(), plan.inferredTopics())) {
            score += 40;
        }

        for (String term : terms) {
            if (term.isBlank()) {
                continue;
            }
            if (topic.contains(term)) {
                score += 20;
            }
            if (tags.contains(term)) {
                score += 14;
            }
            if (title.contains(term)) {
                score += 10;
            }
            if (content.contains(term)) {
                score += 6;
            }
            if (tripStyles.contains(term)) {
                score += 12;
            }
        }
        return score + TravelKnowledgeRetrievalSupport.plannerPreferenceScore(enriched, plan);
    }

    private List<TravelKnowledgeSnippet> loadSnippets(ObjectMapper objectMapper) {
        Map<String, TravelKnowledgeSnippet> merged = new LinkedHashMap<>();
        List<String> resources = new ArrayList<>();
        resources.add(BASE_RESOURCE);
        resources.add(hasResource(CLEANED_RESOURCE) ? CLEANED_RESOURCE : COLLECTED_RESOURCE);
        for (String resourceName : resources) {
            for (TravelKnowledgeSnippet snippet : loadFromResource(resourceName, objectMapper)) {
                merged.put(dedupeKey(snippet), snippet);
            }
        }
        return List.copyOf(merged.values());
    }

    private boolean hasResource(String resourceName) {
        return new ClassPathResource(resourceName).exists();
    }

    private List<TravelKnowledgeSnippet> loadFromResource(String resourceName, ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(resourceName);
        if (!resource.exists()) {
            return List.of();
        }
        try (InputStream inputStream = resource.getInputStream()) {
            List<KnowledgeRecord> records = objectMapper.readValue(inputStream, RECORDS);
            return records.stream()
                    .map(record -> TravelKnowledgeRetrievalSupport.enrichSnippet(new TravelKnowledgeSnippet(
                            record.city(),
                            record.topic(),
                            record.title(),
                            record.content(),
                            record.tags() == null ? List.of() : record.tags(),
                            record.source(),
                            record.schemaSubtype(),
                            record.qualityScore(),
                            record.cityAliases(),
                            record.tripStyleTags()
                    )))
                    .toList();
        }
        catch (IOException exception) {
            throw new IllegalStateException("Failed to load travel knowledge snippets from " + resourceName, exception);
        }
    }

    private String dedupeKey(TravelKnowledgeSnippet snippet) {
        return normalize(snippet.city()) + "::" + normalize(snippet.topic()) + "::" + normalize(snippet.title());
    }

    private void addTerms(Set<String> terms, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String normalized = normalize(raw);
        if (!normalized.isBlank()) {
            terms.add(normalized);
        }
        for (String token : normalized.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]+")) {
            if (!token.isBlank()) {
                terms.add(token);
            }
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, String> buildCityAliasLookup(List<TravelKnowledgeSnippet> knowledgeSnippets) {
        Map<String, String> lookup = new LinkedHashMap<>();
        for (TravelKnowledgeSnippet snippet : knowledgeSnippets) {
            if (snippet.city() != null && !snippet.city().isBlank()) {
                lookup.putIfAbsent(normalize(snippet.city()), snippet.city());
            }
            for (String alias : snippet.cityAliases()) {
                if (alias != null && !alias.isBlank()) {
                    lookup.putIfAbsent(normalize(alias), snippet.city());
                }
            }
        }
        return Map.copyOf(lookup);
    }

    private record KnowledgeRecord(
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
    }

    private record ScoredSnippet(
            TravelKnowledgeSnippet snippet,
            int score
    ) {
    }
}
