package com.travalagent.domain.repository;

import com.travalagent.domain.model.valobj.LongTermMemoryItem;

import java.util.List;
import java.util.Map;

public interface LongTermMemoryRepository {

    void saveMemory(String conversationId, String category, String content, Map<String, Object> metadata);

    List<LongTermMemoryItem> searchRelevant(String query, int limit);
}
