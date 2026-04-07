package com.travalagent.infrastructure.repository;

import com.travalagent.domain.model.valobj.TravelKnowledgeSnippet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelKnowledgeRetrievalSupportTest {

    @Test
    void enrichSnippetInfersHotelAreaSubtypeAndQuality() {
        TravelKnowledgeSnippet snippet = TravelKnowledgeRetrievalSupport.enrichSnippet(new TravelKnowledgeSnippet(
                "Hangzhou",
                "hotel",
                "West Lake is the easiest area to stay in",
                "Stay around West Lake if you want easy access to the lake loop, Lingyin Temple, and evening food streets.",
                List.of("hangzhou", "hotel", "west lake"),
                "seed"
        ));

        assertEquals("hotel_area", snippet.schemaSubtype());
        assertNotNull(snippet.qualityScore());
        assertTrue(snippet.qualityScore() > 0);
    }

    @Test
    void enrichSnippetInfersTransitArrivalSubtype() {
        TravelKnowledgeSnippet snippet = TravelKnowledgeRetrievalSupport.enrichSnippet(new TravelKnowledgeSnippet(
                "Beijing",
                "transit",
                "From Beijing Capital Airport to the city",
                "From the airport, take the Airport Express and transfer into the metro network for downtown hotels.",
                List.of("beijing", "transit", "airport"),
                "seed"
        ));

        assertEquals("transit_arrival", snippet.schemaSubtype());
        assertTrue(snippet.qualityScore() >= 8);
    }

    @Test
    void enrichSnippetInfersTripStyleTags() {
        TravelKnowledgeSnippet snippet = TravelKnowledgeRetrievalSupport.enrichSnippet(new TravelKnowledgeSnippet(
                "Beijing",
                "scenic",
                "Capital Museum",
                "A family-friendly museum with broad history exhibits and easy pacing for children.",
                List.of("beijing", "museum"),
                "seed"
        ));

        assertTrue(snippet.tripStyleTags().contains("family"));
        assertTrue(snippet.tripStyleTags().contains("museum"));
    }

    @Test
    void buildResultPrefersPlannerFriendlyHotelAreaGuidance() {
        TravelKnowledgeRetrievalSupport.RetrievalPlan plan = TravelKnowledgeRetrievalSupport.plan(
                "Hangzhou",
                List.of("stay near West Lake"),
                "Plan a Hangzhou hotel stay near West Lake"
        );

        TravelKnowledgeSnippet listing = new TravelKnowledgeSnippet(
                "Hangzhou",
                "hotel",
                "Lake View Hotel",
                "A representative hotel near the lake with standard rooms and breakfast included.",
                List.of("hangzhou", "hotel", "lake"),
                "seed"
        );
        TravelKnowledgeSnippet area = new TravelKnowledgeSnippet(
                "Hangzhou",
                "hotel",
                "West Lake and Hubin are the easiest areas to stay in",
                "Stay around West Lake or Hubin to reduce transfers and keep most first-time highlights within reach.",
                List.of("hangzhou", "hotel", "west lake", "hubin"),
                "seed"
        );

        var result = TravelKnowledgeRetrievalSupport.buildResult(
                "Hangzhou",
                plan,
                "local-fallback",
                List.of(listing, area),
                1
        );

        assertEquals(1, result.selections().size());
        assertEquals("hotel_area", result.selections().get(0).schemaSubtype());
        assertEquals("West Lake and Hubin are the easiest areas to stay in", result.selections().get(0).title());
    }

    @Test
    void buildResultCarriesMatchedTripStylesWhenPreferenceOverlaps() {
        TravelKnowledgeRetrievalSupport.RetrievalPlan plan = TravelKnowledgeRetrievalSupport.plan(
                "Beijing",
                List.of("family museum"),
                "Plan a family museum day in Beijing"
        );

        TravelKnowledgeSnippet snippet = new TravelKnowledgeSnippet(
                "Beijing",
                "scenic",
                "Capital Museum",
                "A family-friendly museum with broad history exhibits and easy pacing for children.",
                List.of("beijing", "museum"),
                "seed",
                "scenic",
                32,
                List.of("北京"),
                List.of("family", "museum")
        );

        var result = TravelKnowledgeRetrievalSupport.buildResult("Beijing", plan, "local-fallback", List.of(snippet), 1);

        assertEquals(List.of("family", "museum"), result.inferredTripStyles());
        assertTrue(result.selections().get(0).matchedTripStyles().contains("family"));
        assertTrue(result.selections().get(0).matchedTripStyles().contains("museum"));
    }
}
