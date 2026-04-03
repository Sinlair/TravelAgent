package com.xx2201.travel.agent.infrastructure.repository;

import com.xx2201.travel.agent.domain.model.valobj.TravelKnowledgeRetrievalResult;
import com.xx2201.travel.agent.domain.model.valobj.TravelKnowledgeSelection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutingTravelKnowledgeRepositoryTest {

    @Test
    void prefersVectorStoreWhenItReturnsResults() {
        LocalTravelKnowledgeRepository localRepository = mock(LocalTravelKnowledgeRepository.class);
        TravelKnowledgeVectorStoreRepository vectorRepository = mock(TravelKnowledgeVectorStoreRepository.class);
        when(localRepository.canonicalDestination("Hangzhou")).thenReturn("Hangzhou");
        when(vectorRepository.retrieve("Hangzhou", List.of("food"), "hangzhou food", 3)).thenReturn(
                new TravelKnowledgeRetrievalResult(
                        "Hangzhou",
                        List.of("food"),
                        "vector-store",
                        List.of(new TravelKnowledgeSelection(
                                "Hangzhou",
                                "food",
                                "Hefang Street cluster",
                                "Cluster evening food stops around Hefang Street.",
                                List.of("hangzhou", "food"),
                                "seed",
                                "Hangzhou",
                                "food"
                        ))
                )
        );

        RoutingTravelKnowledgeRepository repository = new RoutingTravelKnowledgeRepository(localRepository, providerOf(vectorRepository));
        TravelKnowledgeRetrievalResult results = repository.retrieve("Hangzhou", List.of("food"), "hangzhou food", 3);

        assertEquals(1, results.selections().size());
        assertEquals("vector-store", results.retrievalSource());
        verify(vectorRepository).retrieve("Hangzhou", List.of("food"), "hangzhou food", 3);
    }

    @Test
    void fallsBackToLocalRepositoryWhenVectorStoreIsUnavailableOrEmpty() {
        LocalTravelKnowledgeRepository localRepository = mock(LocalTravelKnowledgeRepository.class);
        TravelKnowledgeVectorStoreRepository vectorRepository = mock(TravelKnowledgeVectorStoreRepository.class);
        when(localRepository.canonicalDestination("Hangzhou")).thenReturn("Hangzhou");
        when(vectorRepository.retrieve("Hangzhou", List.of("food"), "hangzhou food", 3)).thenReturn(
                TravelKnowledgeRetrievalResult.empty("Hangzhou", List.of("food"), "vector-store")
        );
        when(localRepository.retrieve("Hangzhou", List.of("food"), "hangzhou food", 3)).thenReturn(
                new TravelKnowledgeRetrievalResult(
                        "Hangzhou",
                        List.of("food"),
                        "local-fallback",
                        List.of(new TravelKnowledgeSelection(
                                "Hangzhou",
                                "food",
                                "Hubin fallback",
                                "Local fallback hint.",
                                List.of("hangzhou"),
                                "local",
                                "Hangzhou",
                                "food"
                        ))
                )
        );

        RoutingTravelKnowledgeRepository repository = new RoutingTravelKnowledgeRepository(localRepository, providerOf(vectorRepository));
        TravelKnowledgeRetrievalResult results = repository.retrieve("Hangzhou", List.of("food"), "hangzhou food", 3);

        assertEquals(1, results.selections().size());
        assertEquals("local-fallback", results.retrievalSource());
        verify(localRepository).retrieve("Hangzhou", List.of("food"), "hangzhou food", 3);
    }

    @Test
    void resolvesAliasBeforeDelegatingToRepositories() {
        LocalTravelKnowledgeRepository localRepository = mock(LocalTravelKnowledgeRepository.class);
        TravelKnowledgeVectorStoreRepository vectorRepository = mock(TravelKnowledgeVectorStoreRepository.class);
        when(localRepository.canonicalDestination("杭州")).thenReturn("Hangzhou");
        when(vectorRepository.retrieve("Hangzhou", List.of("food"), "杭州 food", 3)).thenReturn(
                TravelKnowledgeRetrievalResult.empty("Hangzhou", List.of("food"), "vector-store")
        );
        when(localRepository.retrieve("Hangzhou", List.of("food"), "杭州 food", 3)).thenReturn(
                TravelKnowledgeRetrievalResult.empty("Hangzhou", List.of("food"), "local-fallback")
        );

        RoutingTravelKnowledgeRepository repository = new RoutingTravelKnowledgeRepository(localRepository, providerOf(vectorRepository));
        repository.retrieve("杭州", List.of("food"), "杭州 food", 3);

        verify(localRepository).canonicalDestination("杭州");
        verify(vectorRepository).retrieve("Hangzhou", List.of("food"), "杭州 food", 3);
    }

    private org.springframework.beans.factory.ObjectProvider<TravelKnowledgeVectorStoreRepository> providerOf(TravelKnowledgeVectorStoreRepository repository) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("travelKnowledgeVectorStoreRepository", repository);
        return beanFactory.getBeanProvider(TravelKnowledgeVectorStoreRepository.class);
    }
}
