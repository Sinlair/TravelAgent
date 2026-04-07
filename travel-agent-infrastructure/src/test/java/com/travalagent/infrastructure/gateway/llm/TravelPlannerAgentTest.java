package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.event.TimelinePublisher;
import com.travalagent.domain.gateway.AmapGateway;
import com.travalagent.domain.model.entity.ConstraintCheckStatus;
import com.travalagent.domain.model.entity.TaskMemory;
import com.travalagent.domain.model.entity.TimelineEvent;
import com.travalagent.domain.model.entity.TravelConstraintCheck;
import com.travalagent.domain.model.entity.TravelPlan;
import com.travalagent.domain.model.valobj.TravelKnowledgeRetrievalResult;
import com.travalagent.domain.model.valobj.TravelKnowledgeSelection;
import com.travalagent.domain.model.valobj.AgentExecutionContext;
import com.travalagent.domain.model.valobj.AgentExecutionResult;
import com.travalagent.domain.model.valobj.WeatherSnapshot;
import com.travalagent.domain.repository.TravelKnowledgeRepository;
import com.travalagent.domain.service.TravelPlanBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TravelPlannerAgentTest {

    private final TravelPlanBuilder travelPlanBuilder = mock(TravelPlanBuilder.class);
    private final AmapTravelPlanEnricher amapTravelPlanEnricher = mock(AmapTravelPlanEnricher.class);
    private final HeuristicTravelPlanValidator travelPlanValidator = mock(HeuristicTravelPlanValidator.class);
    private final HeuristicTravelPlanRepairer travelPlanRepairer = mock(HeuristicTravelPlanRepairer.class);
    private final TravelKnowledgeRepository travelKnowledgeRepository = mock(TravelKnowledgeRepository.class);
    private final AmapGateway amapGateway = mock(AmapGateway.class);
    private final TimelinePublisher timelinePublisher = mock(TimelinePublisher.class);

    @Test
    void executeReturnsClosestFeasibleAlternativeWhenStrictValidationFails() {
        TravelPlannerAgent agent = new TravelPlannerAgent(
                travelPlanBuilder,
                amapTravelPlanEnricher,
                travelPlanValidator,
                travelPlanRepairer,
                travelKnowledgeRepository,
                amapGateway,
                timelinePublisher
        );

        AgentExecutionContext context = new AgentExecutionContext(
                "conversation-1",
                "Plan a 2 day Hangzhou trip with a 700 CNY budget.",
                List.of(),
                new TaskMemory(
                        "conversation-1",
                        "Shanghai",
                        "Hangzhou",
                        2,
                        "700 CNY",
                        List.of("West Lake", "Lingyin Temple"),
                        null,
                        null,
                        Instant.now()
                ),
                null,
                List.of(),
                "planner"
        );

        TravelPlan draftPlan = plan("conversation-1", 700, 1200, List.of(
                new TravelConstraintCheck("budget", ConstraintCheckStatus.WARN, "Budget exceeds limit."),
                new TravelConstraintCheck("transit-load", ConstraintCheckStatus.WARN, "Transit is heavy.")
        ));
        TravelPlan repairedPlanOne = plan("conversation-1", 700, 1100, List.of(
                new TravelConstraintCheck("budget", ConstraintCheckStatus.WARN, "Budget still exceeds limit.")
        ));
        TravelPlan repairedPlanTwo = plan("conversation-1", 700, 980, List.of(
                new TravelConstraintCheck("budget", ConstraintCheckStatus.WARN, "Budget still exceeds limit.")
        ));
        TravelPlan feasiblePlan = plan("conversation-1", 700, 860, List.of(
                new TravelConstraintCheck("budget", ConstraintCheckStatus.PASS, "Budget is inside the adjusted limit.")
        ));

        TravelPlanValidationResult firstResult = new TravelPlanValidationResult(draftPlan, List.of("budget", "transit-load"), false, 0, 2);
        TravelPlanValidationResult secondResult = new TravelPlanValidationResult(repairedPlanOne, List.of("budget"), false, 0, 1);
        TravelPlanValidationResult thirdResult = new TravelPlanValidationResult(repairedPlanTwo, List.of("budget"), false, 0, 1);
        TravelPlanValidationResult acceptedResult = new TravelPlanValidationResult(feasiblePlan, List.of(), true, 0, 0);

        when(travelPlanBuilder.build(context)).thenReturn(draftPlan);
        when(travelPlanBuilder.render(any(TravelPlan.class), eq(context))).thenReturn("Feasible itinerary");
        when(amapTravelPlanEnricher.enrich(any(), any())).thenReturn(draftPlan, repairedPlanOne, repairedPlanTwo, feasiblePlan);
        when(travelPlanValidator.validate(any(), any())).thenReturn(firstResult, secondResult, thirdResult, acceptedResult);
        when(travelPlanRepairer.repair(any(), any(), any())).thenReturn(repairedPlanOne, repairedPlanTwo, feasiblePlan);
        when(travelKnowledgeRepository.retrieve(eq("Hangzhou"), eq(List.of("West Lake", "Lingyin Temple")), eq(context.userMessage()), eq(5))).thenReturn(
                new TravelKnowledgeRetrievalResult(
                        "Hangzhou",
                        List.of("food", "scenic"),
                        "local-fallback",
                        List.of(new TravelKnowledgeSelection(
                                "Hangzhou",
                                "food",
                                "West Lake core loop works best in half a day",
                                "Keep West Lake and Lingyin Temple on separate half days.",
                                List.of("hangzhou", "west lake"),
                                "local-curated",
                                "Hangzhou",
                                "food"
                        ))
                )
        );
        when(amapGateway.weather("Hangzhou")).thenReturn(new WeatherSnapshot(
                "Hangzhou",
                "Zhejiang",
                "2026-04-03 08:00:00",
                "Cloudy",
                "22",
                "NE",
                "3"
        ));

        AgentExecutionResult result = agent.execute(context);

        assertNotNull(result.travelPlan());
        assertEquals(feasiblePlan.conversationId(), result.travelPlan().conversationId());
        assertEquals(feasiblePlan.estimatedTotalMax(), result.travelPlan().estimatedTotalMax());
        assertTrue(result.answer().contains("closest feasible alternative"));
        assertTrue(result.answer().contains("Weather Snapshot"));
        assertTrue(result.answer().contains("Local Knowledge Hints"));
        assertTrue(result.answer().contains("West Lake core loop works best in half a day"));
        assertTrue(result.answer().contains("point-in-time snapshot"));
        assertNotNull(result.travelPlan().weatherSnapshot());
        assertNotNull(result.travelPlan().knowledgeRetrieval());
        assertEquals("Cloudy", result.travelPlan().weatherSnapshot().description());
        assertEquals("local-fallback", result.travelPlan().knowledgeRetrieval().retrievalSource());
        assertEquals(1, result.travelPlan().knowledgeRetrieval().selections().size());
        assertTrue(result.travelPlan().constraintRelaxed());
        assertFalse(result.travelPlan().adjustmentSuggestions().isEmpty());
        assertEquals(Boolean.FALSE, result.metadata().get("rejected"));
        assertEquals(Boolean.TRUE, result.metadata().get("constraintRelaxed"));
        assertEquals(3, result.metadata().get("repairAttempts"));
        assertEquals(1, result.metadata().get("knowledgeCount"));
        assertEquals(Boolean.TRUE, result.metadata().get("weatherIncluded"));
        verify(travelPlanRepairer, times(3)).repair(any(), any(), any());
        verify(timelinePublisher, times(13)).publish(any(TimelineEvent.class));
    }

    @Test
    void executeRejectsPlanWhenValidationStillFailsAfterRepairsAndRelaxation() {
        TravelPlannerAgent agent = new TravelPlannerAgent(
                travelPlanBuilder,
                amapTravelPlanEnricher,
                travelPlanValidator,
                travelPlanRepairer,
                travelKnowledgeRepository,
                amapGateway,
                timelinePublisher
        );

        AgentExecutionContext context = new AgentExecutionContext(
                "conversation-1",
                "Plan a 2 day Hangzhou trip with a 500 CNY budget and very relaxed pace.",
                List.of(),
                new TaskMemory(
                        "conversation-1",
                        "Shanghai",
                        "Hangzhou",
                        2,
                        "500 CNY",
                        List.of("relaxed pace"),
                        null,
                        null,
                        Instant.now()
                ),
                null,
                List.of(),
                "planner"
        );

        TravelPlan failingPlan = plan("conversation-1", 500, 980, List.of(
                new TravelConstraintCheck("budget", ConstraintCheckStatus.WARN, "Budget still exceeds limit."),
                new TravelConstraintCheck("transit-load", ConstraintCheckStatus.FAIL, "Transit load is still too high.")
        ));
        TravelPlanValidationResult firstResult = new TravelPlanValidationResult(failingPlan, List.of("budget", "transit-load"), false, 1, 1);
        TravelPlanValidationResult laterResult = new TravelPlanValidationResult(failingPlan, List.of("budget"), false, 0, 1);

        when(travelPlanBuilder.build(context)).thenReturn(failingPlan);
        when(amapTravelPlanEnricher.enrich(any(), any())).thenReturn(failingPlan, failingPlan, failingPlan, failingPlan, failingPlan, failingPlan, failingPlan);
        when(travelPlanValidator.validate(any(), any())).thenReturn(firstResult, laterResult, laterResult, laterResult, laterResult, laterResult, laterResult);
        when(travelPlanRepairer.repair(any(), any(), any())).thenReturn(failingPlan, failingPlan, failingPlan, failingPlan, failingPlan, failingPlan);

        AgentExecutionResult result = agent.execute(context);

        assertNull(result.travelPlan());
        assertTrue(result.answer().contains("Try these adjustments first"));
        assertEquals(Boolean.TRUE, result.metadata().get("rejected"));
        assertEquals(Boolean.FALSE, result.metadata().get("constraintRelaxed"));
        assertEquals(6, result.metadata().get("repairAttempts"));
        assertEquals(0, result.metadata().get("knowledgeCount"));
        assertEquals(Boolean.FALSE, result.metadata().get("weatherIncluded"));
        verify(travelPlanRepairer, times(6)).repair(any(), any(), any());
        verify(timelinePublisher, times(20)).publish(any(TimelineEvent.class));
    }

    private TravelPlan plan(String conversationId, int totalBudget, int estimatedMax, List<TravelConstraintCheck> checks) {
        return new TravelPlan(
                conversationId,
                "Draft",
                "Draft",
                "West Lake",
                "Center",
                List.of(),
                totalBudget,
                Math.max(0, estimatedMax - 120),
                estimatedMax,
                List.of("West Lake"),
                List.of(),
                checks,
                List.of(),
                Instant.now()
        );
    }
}
