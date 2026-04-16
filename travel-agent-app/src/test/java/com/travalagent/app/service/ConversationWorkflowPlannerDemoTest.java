package com.travalagent.app.service;

import com.travalagent.app.dto.ChatRequest;
import com.travalagent.app.dto.ChatResponse;
import com.travalagent.domain.event.TimelinePublisher;
import com.travalagent.domain.gateway.AmapGateway;
import com.travalagent.domain.model.entity.ConstraintCheckStatus;
import com.travalagent.domain.model.entity.ConversationMessage;
import com.travalagent.domain.model.entity.TaskMemory;
import com.travalagent.domain.model.entity.TimelineEvent;
import com.travalagent.domain.model.entity.TravelConstraintCheck;
import com.travalagent.domain.model.entity.TravelPlan;
import com.travalagent.domain.model.valobj.AgentRouteDecision;
import com.travalagent.domain.model.valobj.AgentType;
import com.travalagent.domain.model.valobj.ExecutionStage;
import com.travalagent.domain.model.valobj.MessageRole;
import com.travalagent.domain.model.valobj.TravelKnowledgeRetrievalResult;
import com.travalagent.domain.model.valobj.TravelKnowledgeSelection;
import com.travalagent.domain.model.valobj.WeatherSnapshot;
import com.travalagent.domain.repository.ConversationRepository;
import com.travalagent.domain.repository.LongTermMemoryRepository;
import com.travalagent.domain.repository.TravelKnowledgeRepository;
import com.travalagent.domain.service.AgentRouter;
import com.travalagent.domain.service.ConversationSummarizer;
import com.travalagent.domain.service.ImageAttachmentInterpreter;
import com.travalagent.domain.service.TaskMemoryExtractor;
import com.travalagent.domain.service.TravelPlanBuilder;
import com.travalagent.infrastructure.config.TravelAgentProperties;
import com.travalagent.infrastructure.gateway.llm.AmapTravelPlanEnricher;
import com.travalagent.infrastructure.gateway.llm.HeuristicTravelPlanRepairer;
import com.travalagent.infrastructure.gateway.llm.HeuristicTravelPlanValidator;
import com.travalagent.infrastructure.gateway.llm.TravelPlanValidationResult;
import com.travalagent.infrastructure.gateway.llm.TravelPlannerAgent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationWorkflowPlannerDemoTest {

    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final LongTermMemoryRepository longTermMemoryRepository = mock(LongTermMemoryRepository.class);
    private final AgentRouter agentRouter = mock(AgentRouter.class);
    private final TaskMemoryExtractor taskMemoryExtractor = mock(TaskMemoryExtractor.class);
    private final ConversationSummarizer conversationSummarizer = mock(ConversationSummarizer.class);
    private final ImageAttachmentInterpreter imageAttachmentInterpreter = mock(ImageAttachmentInterpreter.class);
    private final TimelinePublisher timelinePublisher = mock(TimelinePublisher.class);

    private final TravelPlanBuilder travelPlanBuilder = mock(TravelPlanBuilder.class);
    private final AmapTravelPlanEnricher amapTravelPlanEnricher = mock(AmapTravelPlanEnricher.class);
    private final HeuristicTravelPlanValidator travelPlanValidator = mock(HeuristicTravelPlanValidator.class);
    private final HeuristicTravelPlanRepairer travelPlanRepairer = mock(HeuristicTravelPlanRepairer.class);
    private final TravelKnowledgeRepository travelKnowledgeRepository = mock(TravelKnowledgeRepository.class);
    private final AmapGateway amapGateway = mock(AmapGateway.class);

    @Test
    void executeRunsPlannerDemoFlowEndToEnd() {
        TravelAgentProperties properties = new TravelAgentProperties();
        properties.setMemoryWindow(12);
        properties.setSummaryThreshold(2);

        TravelPlannerAgent plannerAgent = new TravelPlannerAgent(
                travelPlanBuilder,
                amapTravelPlanEnricher,
                travelPlanValidator,
                travelPlanRepairer,
                travelKnowledgeRepository,
                amapGateway,
                timelinePublisher
        );

        ConversationWorkflow workflow = new ConversationWorkflow(
                conversationRepository,
                longTermMemoryRepository,
                agentRouter,
                taskMemoryExtractor,
                conversationSummarizer,
                imageAttachmentInterpreter,
                List.of(plannerAgent),
                timelinePublisher,
                properties
        );

        String conversationId = "planner-demo-1";
        ChatRequest request = new ChatRequest(conversationId, "Plan a 2 day Hangzhou trip with a 700 CNY budget.", null, List.of(), null);
        TaskMemory storedTaskMemory = TaskMemory.empty(conversationId);
        TaskMemory workingMemory = new TaskMemory(
                conversationId,
                "Shanghai",
                "Hangzhou",
                2,
                "700 CNY",
                List.of("family museum", "West Lake"),
                null,
                null,
                Instant.now()
        );
        TaskMemory updatedMemory = workingMemory;

        List<ConversationMessage> recentMessages = List.of(new ConversationMessage(
                "msg-user",
                conversationId,
                MessageRole.USER,
                request.message(),
                null,
                Instant.now()
        ));
        List<ConversationMessage> fullMessages = List.of(
                recentMessages.getFirst(),
                new ConversationMessage(
                        "msg-assistant",
                        conversationId,
                        MessageRole.ASSISTANT,
                        "Planner output persisted.",
                        AgentType.TRAVEL_PLANNER,
                        Instant.now()
                )
        );

        TravelPlan draftPlan = plan(conversationId, 700, 1200, List.of(
                new TravelConstraintCheck("budget", ConstraintCheckStatus.WARN, "Budget exceeds limit."),
                new TravelConstraintCheck("transit-load", ConstraintCheckStatus.WARN, "Transit is heavy.")
        ));
        TravelPlan repairedPlanOne = plan(conversationId, 700, 980, List.of(
                new TravelConstraintCheck("budget", ConstraintCheckStatus.WARN, "Budget still exceeds limit.")
        ));
        TravelPlan repairedPlanTwo = plan(conversationId, 700, 910, List.of(
                new TravelConstraintCheck("budget", ConstraintCheckStatus.WARN, "Budget still exceeds limit.")
        ));
        TravelPlan feasiblePlan = plan(conversationId, 700, 860, List.of(
                new TravelConstraintCheck("budget", ConstraintCheckStatus.PASS, "Budget is inside the adjusted limit.")
        ));

        TravelPlanValidationResult firstResult = new TravelPlanValidationResult(draftPlan, List.of("budget", "transit-load"), false, 0, 2);
        TravelPlanValidationResult secondResult = new TravelPlanValidationResult(repairedPlanOne, List.of("budget"), false, 0, 1);
        TravelPlanValidationResult thirdResult = new TravelPlanValidationResult(repairedPlanTwo, List.of("budget"), false, 0, 1);
        TravelPlanValidationResult acceptedResult = new TravelPlanValidationResult(feasiblePlan, List.of(), true, 0, 0);

        List<TimelineEvent> timeline = List.of(
                TimelineEvent.of(conversationId, ExecutionStage.COMPLETED, "Execution finished", Map.of("agent", AgentType.TRAVEL_PLANNER.name()))
        );

        when(conversationRepository.findConversation(conversationId)).thenReturn(Optional.empty());
        when(conversationRepository.findTaskMemory(conversationId)).thenReturn(Optional.of(storedTaskMemory));
        when(conversationRepository.findRecentMessages(conversationId, 12)).thenReturn(recentMessages);
        when(taskMemoryExtractor.extract(eq(storedTaskMemory), eq(recentMessages))).thenReturn(workingMemory);
        when(longTermMemoryRepository.searchRelevant(request.message(), 3)).thenReturn(List.of());
        when(agentRouter.route(any())).thenReturn(new AgentRouteDecision(
                AgentType.TRAVEL_PLANNER,
                "keyword route: planner",
                false,
                null
        ));
        when(travelPlanBuilder.build(any())).thenReturn(draftPlan);
        when(travelPlanBuilder.render(any(TravelPlan.class), any())).thenReturn("Feasible itinerary");
        when(amapTravelPlanEnricher.enrich(any(), any())).thenReturn(draftPlan, repairedPlanOne, repairedPlanTwo, feasiblePlan);
        when(travelPlanValidator.validate(any(), any())).thenReturn(firstResult, secondResult, thirdResult, acceptedResult);
        when(travelPlanRepairer.repair(any(), any(), any())).thenReturn(repairedPlanOne, repairedPlanTwo, feasiblePlan);
        when(travelKnowledgeRepository.retrieve(eq("Hangzhou"), eq(List.of("family museum", "West Lake")), eq(request.message()), eq(5))).thenReturn(
                new TravelKnowledgeRetrievalResult(
                        "Hangzhou",
                        List.of("scenic", "food", "hotel"),
                        List.of("family", "museum"),
                        "local-fallback",
                        List.of(new TravelKnowledgeSelection(
                                "Hangzhou",
                                "scenic",
                                "West Lake is easiest with a half-day loop",
                                "Keep West Lake in one half day and avoid adding a second cross-town attraction.",
                                List.of("hangzhou", "west lake"),
                                "local-curated",
                                "scenic",
                                38,
                                List.of("family", "museum"),
                                "Hangzhou",
                                "scenic"
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
        when(conversationRepository.findMessages(conversationId)).thenReturn(fullMessages);
        when(taskMemoryExtractor.extract(eq(storedTaskMemory), eq(fullMessages))).thenReturn(updatedMemory);
        when(conversationSummarizer.summarize(null, fullMessages)).thenReturn("Planner demo summary");
        when(conversationRepository.findTimeline(conversationId)).thenReturn(timeline);

        ChatResponse response = workflow.execute(request);

        assertEquals(AgentType.TRAVEL_PLANNER, response.agentType());
        assertNotNull(response.travelPlan());
        assertTrue(response.answer().contains("closest feasible alternative"));
        assertTrue(response.answer().contains("Weather Snapshot"));
        assertTrue(response.answer().contains("Local Knowledge Hints"));
        assertTrue(response.answer().contains("West Lake is easiest with a half-day loop"));
        assertEquals("Cloudy", response.travelPlan().weatherSnapshot().description());
        assertEquals("local-fallback", response.travelPlan().knowledgeRetrieval().retrievalSource());
        assertEquals(List.of("family", "museum"), response.travelPlan().knowledgeRetrieval().inferredTripStyles());
        assertFalse(response.travelPlan().adjustmentSuggestions().isEmpty());
        verify(conversationRepository).saveTravelPlan(response.travelPlan());
        verify(longTermMemoryRepository).saveMemory(eq(conversationId), eq("TRAVEL_PLANNER"), eq("Planner demo summary"), any());
        verify(timelinePublisher, atLeastOnce()).publish(any(TimelineEvent.class));
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
