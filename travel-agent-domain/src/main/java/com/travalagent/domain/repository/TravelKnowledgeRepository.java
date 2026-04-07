package com.travalagent.domain.repository;

import com.travalagent.domain.model.valobj.TravelKnowledgeRetrievalResult;
import com.travalagent.domain.model.valobj.TravelKnowledgeSnippet;

import java.util.List;

public interface TravelKnowledgeRepository {

    TravelKnowledgeRetrievalResult retrieve(String destination, List<String> preferences, String query, int limit);

    default List<TravelKnowledgeSnippet> search(String destination, List<String> preferences, String query, int limit) {
        return retrieve(destination, preferences, query, limit).snippets();
    }
}
