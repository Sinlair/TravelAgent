package com.xx2201.travel.agent.infrastructure.repository;

import com.xx2201.travel.agent.infrastructure.config.TravelAgentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutingLongTermMemoryRepositoryTest {

    @Test
    void autoModePrefersVectorStoreWhenAvailable() {
        SqliteConversationRepository sqliteRepository = mock(SqliteConversationRepository.class);
        VectorStoreLongTermMemoryRepository vectorStoreRepository = mock(VectorStoreLongTermMemoryRepository.class);
        TravelAgentProperties properties = new TravelAgentProperties();
        properties.setMemoryProvider(TravelAgentProperties.MemoryProvider.AUTO);

        RoutingLongTermMemoryRepository repository = new RoutingLongTermMemoryRepository(sqliteRepository, properties, providerOf(vectorStoreRepository));
        repository.saveMemory("c1", "travel", "content", Map.of("budget", "2000"));

        verify(vectorStoreRepository).saveMemory("c1", "travel", "content", Map.of("budget", "2000"));
    }

    @Test
    void sqliteModeUsesSqliteRepositoryEvenWhenVectorStoreExists() {
        SqliteConversationRepository sqliteRepository = mock(SqliteConversationRepository.class);
        VectorStoreLongTermMemoryRepository vectorStoreRepository = mock(VectorStoreLongTermMemoryRepository.class);
        TravelAgentProperties properties = new TravelAgentProperties();
        properties.setMemoryProvider(TravelAgentProperties.MemoryProvider.SQLITE);

        RoutingLongTermMemoryRepository repository = new RoutingLongTermMemoryRepository(sqliteRepository, properties, providerOf(vectorStoreRepository));
        repository.searchRelevant("hangzhou", 3);

        verify(sqliteRepository).searchRelevant("hangzhou", 3);
    }

    @Test
    void milvusModeFailsFastWhenVectorStoreIsMissing() {
        SqliteConversationRepository sqliteRepository = mock(SqliteConversationRepository.class);
        TravelAgentProperties properties = new TravelAgentProperties();
        properties.setMemoryProvider(TravelAgentProperties.MemoryProvider.MILVUS);

        RoutingLongTermMemoryRepository repository = new RoutingLongTermMemoryRepository(
                sqliteRepository,
                properties,
                new StaticListableBeanFactory().getBeanProvider(VectorStoreLongTermMemoryRepository.class)
        );

        assertThrows(IllegalStateException.class, () -> repository.searchRelevant("hangzhou", 3));
    }

    private org.springframework.beans.factory.ObjectProvider<VectorStoreLongTermMemoryRepository> providerOf(VectorStoreLongTermMemoryRepository repository) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("vectorStoreLongTermMemoryRepository", repository);
        return beanFactory.getBeanProvider(VectorStoreLongTermMemoryRepository.class);
    }
}