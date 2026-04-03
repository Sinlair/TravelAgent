package com.xx2201.travel.agent.domain.repository;

import com.xx2201.travel.agent.domain.model.valobj.LongTermMemoryItem;

import java.util.List;
import java.util.Map;

public interface LongTermMemoryRepository {

    void saveMemory(String conversationId, String category, String content, Map<String, Object> metadata);

    List<LongTermMemoryItem> searchRelevant(String query, int limit);
}
