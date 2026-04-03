package com.xx2201.travel.agent.infrastructure.repository;

import com.xx2201.travel.agent.domain.model.valobj.TravelKnowledgeRetrievalResult;
import com.xx2201.travel.agent.domain.model.valobj.TravelKnowledgeSnippet;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class TravelKnowledgeSeedService {

    private static final List<String> DEFAULT_SAMPLE_CITY_PREFERENCE = List.of(
            "Hangzhou",
            "Beijing",
            "Shanghai",
            "Chengdu",
            "Guangzhou"
    );

    private final LocalTravelKnowledgeRepository localTravelKnowledgeRepository;
    private final ObjectProvider<TravelKnowledgeVectorStoreRepository> travelKnowledgeVectorStoreRepositoryProvider;

    public TravelKnowledgeSeedService(
            LocalTravelKnowledgeRepository localTravelKnowledgeRepository,
            ObjectProvider<TravelKnowledgeVectorStoreRepository> travelKnowledgeVectorStoreRepositoryProvider
    ) {
        this.localTravelKnowledgeRepository = localTravelKnowledgeRepository;
        this.travelKnowledgeVectorStoreRepositoryProvider = travelKnowledgeVectorStoreRepositoryProvider;
    }

    public int seedDefaultKnowledge() {
        TravelKnowledgeVectorStoreRepository vectorRepository = travelKnowledgeVectorStoreRepositoryProvider.getIfAvailable();
        if (vectorRepository == null) {
            throw new IllegalStateException("Travel knowledge vector store is not available. Ensure Milvus and embedding configuration are enabled.");
        }
        List<TravelKnowledgeSnippet> snippets = localTravelKnowledgeRepository.all();
        return vectorRepository.upsert(snippets);
    }

    public List<TravelKnowledgeRetrievalResult> verifySeededKnowledge(List<String> requestedCities) {
        TravelKnowledgeVectorStoreRepository vectorRepository = travelKnowledgeVectorStoreRepositoryProvider.getIfAvailable();
        if (vectorRepository == null) {
            throw new IllegalStateException("Travel knowledge vector store is not available. Ensure Milvus and embedding configuration are enabled.");
        }

        List<String> sampleCities = resolveSampleCities(requestedCities);
        List<TravelKnowledgeRetrievalResult> results = new ArrayList<>();
        for (String city : sampleCities) {
            results.add(vectorRepository.retrieve(
                    city,
                    List.of("where to stay", "airport arrival", "food street", "city highlights"),
                    city + " scenic food hotel transit itinerary planning",
                    5
            ));
        }
        return List.copyOf(results);
    }

    private List<String> resolveSampleCities(List<String> requestedCities) {
        Set<String> normalizedRequested = new LinkedHashSet<>();
        if (requestedCities != null) {
            for (String city : requestedCities) {
                if (city != null && !city.isBlank()) {
                    normalizedRequested.add(city.trim());
                }
            }
        }
        if (!normalizedRequested.isEmpty()) {
            return List.copyOf(normalizedRequested);
        }

        List<TravelKnowledgeSnippet> snippets = localTravelKnowledgeRepository.all();
        Set<String> availableCities = new LinkedHashSet<>();
        for (TravelKnowledgeSnippet snippet : snippets) {
            if (snippet.city() != null && !snippet.city().isBlank()) {
                availableCities.add(snippet.city().trim());
            }
        }

        List<String> resolved = new ArrayList<>();
        for (String preferredCity : DEFAULT_SAMPLE_CITY_PREFERENCE) {
            String matched = availableCities.stream()
                    .filter(city -> city.equalsIgnoreCase(preferredCity))
                    .findFirst()
                    .orElse(null);
            if (matched != null) {
                resolved.add(matched);
            }
            if (resolved.size() == 3) {
                return List.copyOf(resolved);
            }
        }

        for (String city : availableCities) {
            resolved.add(city);
            if (resolved.size() == 3) {
                break;
            }
        }
        return List.copyOf(resolved);
    }
}
