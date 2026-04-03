package com.xx2201.travel.agent.app.service;

import com.xx2201.travel.agent.app.dto.ChatRequest;
import com.xx2201.travel.agent.app.dto.ChatResponse;
import com.xx2201.travel.agent.domain.event.TimelinePublisher;
import com.xx2201.travel.agent.domain.model.entity.ConversationMessage;
import com.xx2201.travel.agent.domain.model.entity.TaskMemory;
import com.xx2201.travel.agent.domain.model.entity.TimelineEvent;
import com.xx2201.travel.agent.domain.model.entity.TravelPlan;
import com.xx2201.travel.agent.domain.model.valobj.AgentExecutionResult;
import com.xx2201.travel.agent.domain.model.valobj.AgentRouteDecision;
import com.xx2201.travel.agent.domain.model.valobj.AgentType;
import com.xx2201.travel.agent.domain.model.valobj.ExecutionStage;
import com.xx2201.travel.agent.domain.model.valobj.MessageRole;
import com.xx2201.travel.agent.domain.repository.ConversationRepository;
import com.xx2201.travel.agent.domain.repository.LongTermMemoryRepository;
import com.xx2201.travel.agent.domain.service.AgentRouter;
import com.xx2201.travel.agent.domain.service.ConversationSummarizer;
import com.xx2201.travel.agent.domain.service.SpecialistAgent;
import com.xx2201.travel.agent.domain.service.TaskMemoryExtractor;
import com.xx2201.travel.agent.infrastructure.config.TravelAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationWorkflowTest {

    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final LongTermMemoryRepository longTermMemoryRepository = mock(LongTermMemoryRepository.class);
    private final AgentRouter agentRouter = mock(AgentRouter.class);
    private final TaskMemoryExtractor taskMemoryExtractor = mock(TaskMemoryExtractor.class);
    private final ConversationSummarizer conversationSummarizer = mock(ConversationSummarizer.class);
    private final SpecialistAgent plannerAgent = mock(SpecialistAgent.class);
    private final TimelinePublisher timelinePublisher = mock(TimelinePublisher.class);
    private final TravelAgentProperties properties = new TravelAgentProperties();

    private ConversationWorkflow conversationWorkflow;

    @BeforeEach
    void setUp() {
        properties.setMemoryWindow(12);
        properties.setSummaryThreshold(2);
        when(plannerAgent.supports()).thenReturn(AgentType.TRAVEL_PLANNER);
        this.conversationWorkflow = new ConversationWorkflow(
                conversationRepository,
                longTermMemoryRepository,
                agentRouter,
                taskMemoryExtractor,
                conversationSummarizer,
                List.of(plannerAgent),
                timelinePublisher,
                properties
        );
    }

    @Test
    void executeRunsPlannerWorkflowAndPersistsOutputs() {
        String conversationId = "conversation-1";
        ChatRequest request = new ChatRequest(conversationId, "Plan a 3 day Hangzhou trip with a 3000 CNY budget");
        TaskMemory storedTaskMemory = TaskMemory.empty(conversationId);
        TaskMemory workingMemory = new TaskMemory(
                conversationId,
                null,
                "Hangzhou",
                3,
                "3000 CNY",
                List.of("local food"),
                null,
                null,
                Instant.now()
        );
        TaskMemory updatedMemory = new TaskMemory(
                conversationId,
                null,
                "Hangzhou",
                3,
                "3000 CNY",
                List.of("local food"),
                null,
                null,
                Instant.now()
        );
        TravelPlan travelPlan = new TravelPlan(
                conversationId,
                "Hangzhou plan",
                "Three day city break",
                "West Lake",
                "Central access",
                List.of(),
                3000,
                2600,
                3200,
                List.of("West Lake"),
                List.of(),
                List.of(),
                List.of(),
                Instant.now()
        );

        List<ConversationMessage> recentMessages = List.of(new ConversationMessage(
                "msg-user",
                conversationId,
                MessageRole.USER,
                request.message(),
                null,
                Instant.now()
        ));
        List<ConversationMessage> fullMessages = List.of(
                recentMessages.get(0),
                new ConversationMessage(
                        "msg-assistant",
                        conversationId,
                        MessageRole.ASSISTANT,
                        "Here is your itinerary.",
                        AgentType.TRAVEL_PLANNER,
                        Instant.now()
                )
        );
        List<TimelineEvent> timeline = List.of(TimelineEvent.of(
                conversationId,
                ExecutionStage.COMPLETED,
                "Execution finished",
                Map.of("agent", AgentType.TRAVEL_PLANNER.name())
        ));

        when(conversationRepository.findConversation(conversationId)).thenReturn(Optional.empty());
        when(conversationRepository.findTaskMemory(conversationId)).thenReturn(Optional.of(storedTaskMemory));
        when(conversationRepository.findRecentMessages(conversationId, 12)).thenReturn(recentMessages);
        when(taskMemoryExtractor.extract(eq(storedTaskMemory), same(recentMessages))).thenReturn(workingMemory);
        when(longTermMemoryRepository.searchRelevant(request.message(), 3)).thenReturn(List.of());
        when(agentRouter.route(any())).thenReturn(new AgentRouteDecision(
                AgentType.TRAVEL_PLANNER,
                "keyword route: planner",
                false,
                null
        ));
        when(plannerAgent.execute(any())).thenReturn(new AgentExecutionResult(
                AgentType.TRAVEL_PLANNER,
                "Here is your itinerary.",
                Map.of("plannerMode", "constraint-driven"),
                travelPlan
        ));
        when(conversationRepository.findMessages(conversationId)).thenReturn(fullMessages);
        when(taskMemoryExtractor.extract(eq(storedTaskMemory), same(fullMessages))).thenReturn(updatedMemory);
        when(conversationSummarizer.summarize(null, fullMessages)).thenReturn("Summary");
        when(conversationRepository.findTimeline(conversationId)).thenReturn(timeline);

        ChatResponse response = conversationWorkflow.execute(request);

        assertEquals(conversationId, response.conversationId());
        assertEquals(AgentType.TRAVEL_PLANNER, response.agentType());
        assertEquals("Here is your itinerary.", response.answer());
        assertSame(updatedMemory, response.taskMemory());
        assertSame(travelPlan, response.travelPlan());
        assertSame(timeline, response.timeline());

        verify(conversationRepository).saveTravelPlan(travelPlan);
        verify(longTermMemoryRepository).saveMemory(eq(conversationId), eq("TRAVEL_PLANNER"), eq("Summary"), anyMap());
        verify(timelinePublisher, atLeastOnce()).publish(any(TimelineEvent.class));
    }
}
