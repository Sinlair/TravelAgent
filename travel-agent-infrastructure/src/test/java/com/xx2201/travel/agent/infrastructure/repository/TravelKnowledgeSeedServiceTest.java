package com.xx2201.travel.agent.infrastructure.repository;

import com.xx2201.travel.agent.domain.model.valobj.TravelKnowledgeRetrievalResult;
import com.xx2201.travel.agent.domain.model.valobj.TravelKnowledgeSelection;
import com.xx2201.travel.agent.domain.model.valobj.TravelKnowledgeSnippet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TravelKnowledgeSeedServiceTest {

    @Test
    void seedDefaultKnowledgePushesAllLocalSnippetsIntoVectorStore() {
        LocalTravelKnowledgeRepository localRepository = mock(LocalTravelKnowledgeRepository.class);
        TravelKnowledgeVectorStoreRepository vectorRepository = mock(TravelKnowledgeVectorStoreRepository.class);
        List<TravelKnowledgeSnippet> snippets = List.of(
                new TravelKnowledgeSnippet("Hangzhou", "food", "Hefang Street cluster", "Cluster evening food stops around Hefang Street.", List.of("hangzhou", "food"), "local-curated")
        );
        when(localRepository.all()).thenReturn(snippets);
        when(vectorRepository.upsert(snippets)).thenReturn(1);

        TravelKnowledgeSeedService service = new TravelKnowledgeSeedService(localRepository, providerOf(vectorRepository));
        int seeded = service.seedDefaultKnowledge();

        assertEquals(1, seeded);
        verify(vectorRepository).upsert(snippets);
    }

    @Test
    void seedDefaultKnowledgeFailsFastWhenVectorStoreIsUnavailable() {
        LocalTravelKnowledgeRepository localRepository = mock(LocalTravelKnowledgeRepository.class);
        TravelKnowledgeSeedService service = new TravelKnowledgeSeedService(
                localRepository,
                new StaticListableBeanFactory().getBeanProvider(TravelKnowledgeVectorStoreRepository.class)
        );

        assertThrows(IllegalStateException.class, service::seedDefaultKnowledge);
    }

    @Test
    void verifySeededKnowledgeQueriesRequestedCitiesAgainstVectorStore() {
        LocalTravelKnowledgeRepository localRepository = mock(LocalTravelKnowledgeRepository.class);
        TravelKnowledgeVectorStoreRepository vectorRepository = mock(TravelKnowledgeVectorStoreRepository.class);
        when(vectorRepository.retrieve("Hangzhou", List.of("where to stay", "airport arrival", "food street", "city highlights"), "Hangzhou scenic food hotel transit itinerary planning", 5))
                .thenReturn(new TravelKnowledgeRetrievalResult(
                        "Hangzhou",
                        List.of("hotel", "food"),
                        "vector-store",
                        List.of(new TravelKnowledgeSelection(
                                "Hangzhou",
                                "hotel",
                                "West Lake stay",
                                "Stay by West Lake for easy first-time access.",
                                List.of("hangzhou"),
                                "seed",
                                "hotel_area",
                                42,
                                "Hangzhou",
                                "hotel"
                        ))
                ));

        TravelKnowledgeSeedService service = new TravelKnowledgeSeedService(localRepository, providerOf(vectorRepository));
        List<TravelKnowledgeRetrievalResult> results = service.verifySeededKnowledge(List.of("Hangzhou"));

        assertEquals(1, results.size());
        assertEquals("Hangzhou", results.get(0).destination());
        assertTrue(results.get(0).selections().size() == 1);
        verify(vectorRepository).retrieve("Hangzhou", List.of("where to stay", "airport arrival", "food street", "city highlights"), "Hangzhou scenic food hotel transit itinerary planning", 5);
    }

    @Test
    void verifySeededKnowledgeFallsBackToDefaultSampleCitiesWhenNoneRequested() {
        LocalTravelKnowledgeRepository localRepository = mock(LocalTravelKnowledgeRepository.class);
        TravelKnowledgeVectorStoreRepository vectorRepository = mock(TravelKnowledgeVectorStoreRepository.class);
        when(localRepository.all()).thenReturn(List.of(
                new TravelKnowledgeSnippet("Beijing", "scenic", "Forbidden City", "Historic core.", List.of("beijing"), "seed"),
                new TravelKnowledgeSnippet("Hangzhou", "hotel", "West Lake stay", "Stay near West Lake.", List.of("hangzhou"), "seed"),
                new TravelKnowledgeSnippet("Shanghai", "food", "Bund dinner", "Evening food area.", List.of("shanghai"), "seed")
        ));
        when(vectorRepository.retrieve(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(5)))
                .thenReturn(TravelKnowledgeRetrievalResult.empty("sample", List.of("food"), "vector-store"));

        TravelKnowledgeSeedService service = new TravelKnowledgeSeedService(localRepository, providerOf(vectorRepository));
        List<TravelKnowledgeRetrievalResult> results = service.verifySeededKnowledge(List.of());

        assertEquals(3, results.size());
        verify(vectorRepository).retrieve("Hangzhou", List.of("where to stay", "airport arrival", "food street", "city highlights"), "Hangzhou scenic food hotel transit itinerary planning", 5);
        verify(vectorRepository).retrieve("Beijing", List.of("where to stay", "airport arrival", "food street", "city highlights"), "Beijing scenic food hotel transit itinerary planning", 5);
        verify(vectorRepository).retrieve("Shanghai", List.of("where to stay", "airport arrival", "food street", "city highlights"), "Shanghai scenic food hotel transit itinerary planning", 5);
    }

    private org.springframework.beans.factory.ObjectProvider<TravelKnowledgeVectorStoreRepository> providerOf(TravelKnowledgeVectorStoreRepository repository) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("travelKnowledgeVectorStoreRepository", repository);
        return beanFactory.getBeanProvider(TravelKnowledgeVectorStoreRepository.class);
    }
}
