package com.xx2201.travel.agent.domain.model.entity;

import com.xx2201.travel.agent.domain.model.valobj.TravelKnowledgeRetrievalResult;
import com.xx2201.travel.agent.domain.model.valobj.WeatherSnapshot;

import java.time.Instant;
import java.util.List;

public record TravelPlan(
        String conversationId,
        String title,
        String summary,
        String hotelArea,
        String hotelAreaReason,
        List<TravelHotelRecommendation> hotels,
        Integer totalBudget,
        Integer estimatedTotalMin,
        Integer estimatedTotalMax,
        List<String> highlights,
        List<TravelBudgetItem> budget,
        List<TravelConstraintCheck> checks,
        List<TravelPlanDay> days,
        WeatherSnapshot weatherSnapshot,
        TravelKnowledgeRetrievalResult knowledgeRetrieval,
        boolean constraintRelaxed,
        List<String> adjustmentSuggestions,
        Instant updatedAt
) {

    public TravelPlan {
        hotels = hotels == null ? List.of() : List.copyOf(hotels);
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
        budget = budget == null ? List.of() : List.copyOf(budget);
        checks = checks == null ? List.of() : List.copyOf(checks);
        days = days == null ? List.of() : List.copyOf(days);
        adjustmentSuggestions = adjustmentSuggestions == null ? List.of() : List.copyOf(adjustmentSuggestions);
    }

    public TravelPlan(
            String conversationId,
            String title,
            String summary,
            String hotelArea,
            String hotelAreaReason,
            List<TravelHotelRecommendation> hotels,
            Integer totalBudget,
            Integer estimatedTotalMin,
            Integer estimatedTotalMax,
            List<String> highlights,
            List<TravelBudgetItem> budget,
            List<TravelConstraintCheck> checks,
            List<TravelPlanDay> days,
            Instant updatedAt
    ) {
        this(
                conversationId,
                title,
                summary,
                hotelArea,
                hotelAreaReason,
                hotels,
                totalBudget,
                estimatedTotalMin,
                estimatedTotalMax,
                highlights,
                budget,
                checks,
                days,
                null,
                null,
                false,
                List.of(),
                updatedAt
        );
    }

    public TravelPlan withPlannerInsights(
            WeatherSnapshot weatherSnapshot,
            TravelKnowledgeRetrievalResult knowledgeRetrieval,
            boolean constraintRelaxed,
            List<String> adjustmentSuggestions
    ) {
        return new TravelPlan(
                conversationId,
                title,
                summary,
                hotelArea,
                hotelAreaReason,
                hotels,
                totalBudget,
                estimatedTotalMin,
                estimatedTotalMax,
                highlights,
                budget,
                checks,
                days,
                weatherSnapshot,
                knowledgeRetrieval,
                constraintRelaxed,
                adjustmentSuggestions,
                updatedAt
        );
    }
}

