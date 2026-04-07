package com.travalagent.infrastructure.repository;

import com.travalagent.domain.model.valobj.LongTermMemoryItem;
import com.travalagent.domain.repository.LongTermMemoryRepository;
import com.travalagent.infrastructure.config.TravelAgentProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@Primary
public class RoutingLongTermMemoryRepository implements LongTermMemoryRepository {

    private final SqliteConversationRepository sqliteRepository;
    private final VectorStoreLongTermMemoryRepository vectorStoreRepository;
    private final TravelAgentProperties properties;

    public RoutingLongTermMemoryRepository(
            SqliteConversationRepository sqliteRepository,
            TravelAgentProperties properties,
            org.springframework.beans.factory.ObjectProvider<VectorStoreLongTermMemoryRepository> vectorStoreRepositoryProvider
    ) {
        this.sqliteRepository = sqliteRepository;
        this.properties = properties;
        this.vectorStoreRepository = vectorStoreRepositoryProvider.getIfAvailable();
    }

    @Override
    public void saveMemory(String conversationId, String category, String content, Map<String, Object> metadata) {
        activeRepository().saveMemory(conversationId, category, content, metadata);
    }

    @Override
    public List<LongTermMemoryItem> searchRelevant(String query, int limit) {
        return activeRepository().searchRelevant(query, limit);
    }

    private LongTermMemoryRepository activeRepository() {
        return switch (properties.getMemoryProvider()) {
            case SQLITE -> sqliteRepository;
            case MILVUS -> requireVectorStoreRepository();
            case AUTO -> vectorStoreRepository != null ? vectorStoreRepository : sqliteRepository;
        };
    }

    private LongTermMemoryRepository requireVectorStoreRepository() {
        if (vectorStoreRepository == null) {
            throw new IllegalStateException("travel.agent.memory-provider=MILVUS but no VectorStore bean is available");
        }
        return vectorStoreRepository;
    }
}