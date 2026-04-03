package com.xx2201.travel.agent.domain.repository;

import com.xx2201.travel.agent.domain.model.valobj.TravelKnowledgeRetrievalResult;
import com.xx2201.travel.agent.domain.model.valobj.TravelKnowledgeSnippet;

import java.util.List;

public interface TravelKnowledgeRepository {

    TravelKnowledgeRetrievalResult retrieve(String destination, List<String> preferences, String query, int limit);

    default List<TravelKnowledgeSnippet> search(String destination, List<String> preferences, String query, int limit) {
        return retrieve(destination, preferences, query, limit).snippets();
    }
}
