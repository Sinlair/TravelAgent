package com.xx2201.travel.agent.infrastructure.repository;

import com.xx2201.travel.agent.domain.model.valobj.TravelKnowledgeRetrievalResult;
import com.xx2201.travel.agent.domain.model.valobj.TravelKnowledgeSnippet;
import com.xx2201.travel.agent.domain.repository.TravelKnowledgeRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Primary
public class RoutingTravelKnowledgeRepository implements TravelKnowledgeRepository {

    private final LocalTravelKnowledgeRepository localTravelKnowledgeRepository;
    private final TravelKnowledgeVectorStoreRepository vectorStoreRepository;

    public RoutingTravelKnowledgeRepository(
            LocalTravelKnowledgeRepository localTravelKnowledgeRepository,
            org.springframework.beans.factory.ObjectProvider<TravelKnowledgeVectorStoreRepository> vectorStoreRepositoryProvider
    ) {
        this.localTravelKnowledgeRepository = localTravelKnowledgeRepository;
        this.vectorStoreRepository = vectorStoreRepositoryProvider.getIfAvailable();
    }

    @Override
    public TravelKnowledgeRetrievalResult retrieve(String destination, List<String> preferences, String query, int limit) {
        String canonicalDestination = localTravelKnowledgeRepository.canonicalDestination(destination);
        if (vectorStoreRepository != null) {
            TravelKnowledgeRetrievalResult vectorResults = vectorStoreRepository.retrieve(canonicalDestination, preferences, query, limit);
            if (!vectorResults.selections().isEmpty()) {
                return vectorResults;
            }
        }
        return localTravelKnowledgeRepository.retrieve(canonicalDestination, preferences, query, limit);
    }
}
