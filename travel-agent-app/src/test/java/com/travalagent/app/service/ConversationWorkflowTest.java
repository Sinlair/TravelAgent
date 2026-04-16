package com.travalagent.app.service;

import com.travalagent.app.dto.ChatRequest;
import com.travalagent.app.dto.ChatImageAttachmentRequest;
import com.travalagent.app.dto.ChatResponse;
import com.travalagent.domain.event.TimelinePublisher;
import com.travalagent.domain.model.entity.ConversationImageAttachment;
import com.travalagent.domain.model.entity.ConversationImageFacts;
import com.travalagent.domain.model.entity.ConversationImageContext;
import com.travalagent.domain.model.entity.ConversationMessage;
import com.travalagent.domain.model.entity.ConversationSession;
import com.travalagent.domain.model.entity.TaskMemory;
import com.travalagent.domain.model.entity.TimelineEvent;
import com.travalagent.domain.model.entity.TravelPlan;
import com.travalagent.domain.model.entity.TravelPlanDay;
import com.travalagent.domain.model.valobj.AgentExecutionContext;
import com.travalagent.domain.model.valobj.AgentExecutionResult;
import com.travalagent.domain.model.valobj.AgentRouteDecision;
import com.travalagent.domain.model.valobj.AgentType;
import com.travalagent.domain.model.valobj.ExecutionStage;
import com.travalagent.domain.model.valobj.ImageAttachmentInterpretation;
import com.travalagent.domain.model.valobj.MessageRole;
import com.travalagent.domain.repository.ConversationRepository;
import com.travalagent.domain.repository.LongTermMemoryRepository;
import com.travalagent.domain.service.AgentRouter;
import com.travalagent.domain.service.ConversationSummarizer;
import com.travalagent.domain.service.ImageAttachmentInterpreter;
import com.travalagent.domain.service.SpecialistAgent;
import com.travalagent.domain.service.TaskMemoryExtractor;
import com.travalagent.infrastructure.config.TravelAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationWorkflowTest {

    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final LongTermMemoryRepository longTermMemoryRepository = mock(LongTermMemoryRepository.class);
    private final AgentRouter agentRouter = mock(AgentRouter.class);
    private final TaskMemoryExtractor taskMemoryExtractor = mock(TaskMemoryExtractor.class);
    private final ConversationSummarizer conversationSummarizer = mock(ConversationSummarizer.class);
    private final ImageAttachmentInterpreter imageAttachmentInterpreter = mock(ImageAttachmentInterpreter.class);
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
                imageAttachmentInterpreter,
                List.of(plannerAgent),
                timelinePublisher,
                properties
        );
    }

    @Test
    void executeRunsPlannerWorkflowAndPersistsOutputs() {
        String conversationId = "conversation-1";
        ChatRequest request = new ChatRequest(conversationId, "Plan a 3 day Hangzhou trip with a 3000 CNY budget", null, List.of(), null);
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
        when(taskMemoryExtractor.extract(any(TaskMemory.class), anyList())).thenReturn(workingMemory, updatedMemory);
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
        when(conversationSummarizer.summarize(null, fullMessages)).thenReturn("Summary");
        when(conversationRepository.findTimeline(conversationId)).thenReturn(timeline);

        ChatResponse response = conversationWorkflow.execute(request);

        assertEquals(conversationId, response.conversationId());
        assertEquals(AgentType.TRAVEL_PLANNER, response.agentType());
        assertEquals("Here is your itinerary.", response.answer());
        assertSame(updatedMemory, response.taskMemory());
        assertEquals(travelPlan.title(), response.travelPlan().title());
        assertEquals(travelPlan.hotelArea(), response.travelPlan().hotelArea());
        assertFalse(response.travelPlan().checklist().isEmpty());
        assertSame(timeline, response.timeline());
        assertNotNull(response.feedbackTarget());
        assertEquals("OVERALL", response.feedbackTarget().scope());
        assertEquals(List.of("startDate", "travelers"), response.missingInformation().stream()
                .map(item -> item.code())
                .toList());
        assertEquals(List.of("CLARIFICATION_REQUIRED"), response.issues().stream()
                .map(item -> item.code())
                .toList());

        verify(conversationRepository).saveTravelPlan(any(TravelPlan.class));
        verify(conversationRepository).saveTravelPlanVersion(any());
        verify(longTermMemoryRepository).saveMemory(eq(conversationId), eq("TRAVEL_PLANNER"), eq("Summary"), anyMap());
        verify(timelinePublisher, atLeastOnce()).publish(any(TimelineEvent.class));
    }

    @Test
    void executeKeepsStructuredBriefFieldsWhenFinalizingConversation() {
        String conversationId = "conversation-brief";
        ChatRequest request = new ChatRequest(
                conversationId,
                "Plan a 2 day Hangzhou trip for a couple from 2026-05-02 to 2026-05-03 with a 2500 CNY budget",
                new com.travalagent.app.dto.TripBriefRequest(
                        "Shanghai",
                        "Hangzhou",
                        "2026-05-02",
                        "2026-05-03",
                        2,
                        "couple",
                        "2500 CNY",
                        List.of("West Lake", "local food")
                ),
                List.of(),
                null
        );

        TaskMemory storedTaskMemory = TaskMemory.empty(conversationId);
        TravelPlan travelPlan = new TravelPlan(
                conversationId,
                "Hangzhou plan",
                "Two day city break",
                "West Lake",
                "Central access",
                List.of(),
                2500,
                1800,
                2200,
                List.of("West Lake"),
                List.of(),
                List.of(),
                List.of(
                        new TravelPlanDay(
                                1,
                                "Arrival and lakefront",
                                "09:00",
                                "18:00",
                                60,
                                240,
                                200,
                                List.of(),
                                null
                        ),
                        new TravelPlanDay(
                                2,
                                "Tea village and departure",
                                "09:00",
                                "16:00",
                                50,
                                220,
                                180,
                                List.of(),
                                null
                        )
                ),
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

        when(conversationRepository.findConversation(conversationId)).thenReturn(Optional.empty());
        when(conversationRepository.findTaskMemory(conversationId)).thenReturn(Optional.of(storedTaskMemory));
        when(conversationRepository.findRecentMessages(conversationId, 12)).thenReturn(recentMessages);
        when(taskMemoryExtractor.extract(any(TaskMemory.class), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
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
        when(conversationRepository.findTimeline(conversationId)).thenReturn(List.of());

        ChatResponse response = conversationWorkflow.execute(request);

        assertEquals("Shanghai", response.taskMemory().origin());
        assertEquals("Hangzhou", response.taskMemory().destination());
        assertEquals("2026-05-02", response.taskMemory().startDate());
        assertEquals("2026-05-03", response.taskMemory().endDate());
        assertEquals("couple", response.taskMemory().travelers());
        assertEquals("2500 CNY", response.taskMemory().budget());
        assertEquals(List.of("West Lake", "local food"), response.taskMemory().preferences());
        assertEquals("2026-05-02", response.travelPlan().days().get(0).date());
        assertEquals("2026-05-03", response.travelPlan().days().get(1).date());
        assertTrue(response.missingInformation().isEmpty());

        ArgumentCaptor<TaskMemory> taskMemoryCaptor = ArgumentCaptor.forClass(TaskMemory.class);
        verify(taskMemoryExtractor, times(2)).extract(taskMemoryCaptor.capture(), anyList());
        assertEquals("2026-05-02", taskMemoryCaptor.getAllValues().get(0).startDate());
        assertEquals("couple", taskMemoryCaptor.getAllValues().get(0).travelers());
        assertEquals("2026-05-02", taskMemoryCaptor.getAllValues().get(1).startDate());
        assertEquals("couple", taskMemoryCaptor.getAllValues().get(1).travelers());
    }

    @Test
    void executeStagesImageAttachmentContextBeforePlanning() {
        String conversationId = "conversation-images";
        ChatRequest request = new ChatRequest(
                conversationId,
                null,
                null,
                List.of(new ChatImageAttachmentRequest(
                        "hotel.png",
                        "image/png",
                        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+yWZ0AAAAASUVORK5CYII="
                )),
                null
        );
        when(conversationRepository.findConversation(conversationId)).thenReturn(Optional.empty());
        when(imageAttachmentInterpreter.interpretTravelContext(eq(null), anyList())).thenReturn(new ImageAttachmentInterpretation(
                "- Hotel: West Lake area\n- Check-in: Friday",
                new ConversationImageFacts(
                        null,
                        "Hangzhou",
                        "Friday",
                        null,
                        null,
                        null,
                        null,
                        "West Lake area",
                        List.of(),
                        List.of("origin", "budget", "days", "hotelName", "activities")
                )
        ));
        when(conversationRepository.findTimeline(conversationId)).thenReturn(List.of());
        when(conversationRepository.findTaskMemory(conversationId)).thenReturn(Optional.empty());

        ChatResponse response = conversationWorkflow.execute(request);

        assertEquals(AgentType.GENERAL, response.agentType());
        assertEquals("ANSWER", response.feedbackTarget().scope());
        assertEquals("IMAGE_CONTEXT_CONFIRMATION_REQUIRED", response.issues().getFirst().code());
        verify(conversationRepository).savePendingImageContext(any(ConversationImageContext.class));
        verify(plannerAgent, never()).execute(any());

        ArgumentCaptor<ConversationMessage> savedMessageCaptor = ArgumentCaptor.forClass(ConversationMessage.class);
        verify(conversationRepository, atLeastOnce()).saveMessage(savedMessageCaptor.capture());
        ConversationMessage stagedUserMessage = savedMessageCaptor.getAllValues().stream()
                .filter(message -> message.role() == MessageRole.USER)
                .findFirst()
                .orElseThrow();
        assertEquals("PENDING", stagedUserMessage.metadata().get("imageContextStatus"));
        assertFalse(stagedUserMessage.metadata().containsKey("imageContextSummary"));
        assertFalse(stagedUserMessage.metadata().containsKey("imageFacts"));
    }

    @Test
    void executeConfirmedImageContextPassesSummaryToPlanner() {
        String conversationId = "conversation-images-confirm";
        ChatRequest request = new ChatRequest(conversationId, null, null, List.of(), "CONFIRM");
        TaskMemory storedTaskMemory = TaskMemory.empty(conversationId);
        TaskMemory updatedMemory = TaskMemory.empty(conversationId);
        ConversationImageContext pendingImageContext = new ConversationImageContext(
                conversationId,
                "- Hotel: West Lake area\n- Check-in: Friday",
                new ConversationImageFacts(
                        null,
                        "Hangzhou",
                        "Friday",
                        null,
                        null,
                        null,
                        null,
                        "West Lake area",
                        List.of(),
                        List.of("origin", "budget", "days", "hotelName", "activities")
                ),
                List.of(new ConversationImageAttachment("attachment-1", "hotel.png", "image/png", 68)),
                Instant.now(),
                Instant.now()
        );
        List<ConversationMessage> recentMessages = List.of(new ConversationMessage(
                "msg-user",
                conversationId,
                MessageRole.USER,
                "Uploaded travel image context",
                null,
                Instant.now(),
                Map.of(
                        "imageAttachments", List.of(Map.of("name", "hotel.png", "mediaType", "image/png", "sizeBytes", 68)),
                        "imageContextSummary", "- Hotel: West Lake area\n- Check-in: Friday"
                )
        ));
        List<ConversationMessage> fullMessages = List.of(
                recentMessages.get(0),
                new ConversationMessage(
                        "msg-assistant",
                        conversationId,
                        MessageRole.ASSISTANT,
                        "Here is a plan from the image context.",
                        AgentType.TRAVEL_PLANNER,
                        Instant.now()
                )
        );

        when(conversationRepository.findConversation(conversationId)).thenReturn(Optional.of(new ConversationSession(
                conversationId,
                "Image trip",
                null,
                null,
                Instant.now(),
                Instant.now()
        )));
        when(conversationRepository.findPendingImageContext(conversationId)).thenReturn(Optional.of(pendingImageContext));
        when(conversationRepository.findTaskMemory(conversationId)).thenReturn(Optional.of(storedTaskMemory));
        when(conversationRepository.findRecentMessages(conversationId, 12)).thenReturn(recentMessages);
        when(taskMemoryExtractor.extract(any(TaskMemory.class), anyList())).thenReturn(storedTaskMemory, updatedMemory);
        when(longTermMemoryRepository.searchRelevant(any(), eq(3))).thenReturn(List.of());
        when(agentRouter.route(any())).thenReturn(new AgentRouteDecision(
                AgentType.TRAVEL_PLANNER,
                "image-assisted route",
                false,
                null
        ));
        when(plannerAgent.execute(any())).thenReturn(new AgentExecutionResult(
                AgentType.TRAVEL_PLANNER,
                "Here is a plan from the image context.",
                Map.of(),
                null
        ));
        when(conversationRepository.findMessages(conversationId)).thenReturn(fullMessages);
        when(conversationRepository.findTimeline(conversationId)).thenReturn(List.of());

        conversationWorkflow.execute(request);

        ArgumentCaptor<ConversationMessage> savedMessageCaptor = ArgumentCaptor.forClass(ConversationMessage.class);
        verify(conversationRepository, atLeastOnce()).saveMessage(savedMessageCaptor.capture());
        assertTrue(savedMessageCaptor.getAllValues().stream()
                .filter(message -> message.role() == MessageRole.USER)
                .anyMatch(message -> message.content().contains("Hotel: West Lake area")));

        ArgumentCaptor<AgentExecutionContext> agentContextCaptor = ArgumentCaptor.forClass(AgentExecutionContext.class);
        verify(plannerAgent).execute(agentContextCaptor.capture());
        assertEquals(0, agentContextCaptor.getValue().imageAttachments().size());
        assertEquals("- Hotel: West Lake area\n- Check-in: Friday", agentContextCaptor.getValue().imageContextSummary());
        assertTrue(agentContextCaptor.getValue().userMessage().contains("Hotel: West Lake area"));
        verify(conversationRepository).deletePendingImageContext(conversationId);
    }

    @Test
    void executeIgnoresPendingImageContextMetadataFromEarlierMessages() {
        String conversationId = "conversation-ignore-pending";
        ChatRequest request = new ChatRequest(conversationId, "Please plan the trip without using the old screenshot", null, List.of(), null);
        TaskMemory storedTaskMemory = TaskMemory.empty(conversationId);
        TaskMemory workingMemory = new TaskMemory(
                conversationId,
                null,
                "Hangzhou",
                2,
                "2000 CNY",
                List.of(),
                null,
                null,
                Instant.now()
        );
        TaskMemory updatedMemory = workingMemory;
        List<ConversationMessage> recentMessages = List.of(
                new ConversationMessage(
                        "msg-image",
                        conversationId,
                        MessageRole.USER,
                        "Uploaded travel image context",
                        null,
                        Instant.now(),
                        Map.of(
                                "imageAttachments", List.of(Map.of("name", "hotel.png", "mediaType", "image/png", "sizeBytes", 68)),
                                "imageAttachmentCount", 1,
                                "imageContextSummary", "- Hotel Area: West Lake\n- Start Date: Friday"
                        )
                ),
                new ConversationMessage(
                        "msg-user",
                        conversationId,
                        MessageRole.USER,
                        "Ignore the extracted image facts",
                        null,
                        Instant.now()
                )
        );
        List<ConversationMessage> fullMessages = List.of(
                recentMessages.get(0),
                recentMessages.get(1),
                new ConversationMessage(
                        "msg-assistant",
                        conversationId,
                        MessageRole.ASSISTANT,
                        "Here is the text-only itinerary.",
                        AgentType.TRAVEL_PLANNER,
                        Instant.now()
                )
        );

        when(conversationRepository.findConversation(conversationId)).thenReturn(Optional.of(new ConversationSession(
                conversationId,
                "Image trip",
                null,
                null,
                Instant.now(),
                Instant.now()
        )));
        when(conversationRepository.findPendingImageContext(conversationId)).thenReturn(Optional.empty());
        when(conversationRepository.findTaskMemory(conversationId)).thenReturn(Optional.of(storedTaskMemory));
        when(conversationRepository.findRecentMessages(conversationId, 12)).thenReturn(recentMessages);
        when(taskMemoryExtractor.extract(any(TaskMemory.class), anyList())).thenReturn(workingMemory, updatedMemory);
        when(longTermMemoryRepository.searchRelevant(any(), eq(3))).thenReturn(List.of());
        when(agentRouter.route(any())).thenReturn(new AgentRouteDecision(
                AgentType.TRAVEL_PLANNER,
                "text route",
                false,
                null
        ));
        when(plannerAgent.execute(any())).thenReturn(new AgentExecutionResult(
                AgentType.TRAVEL_PLANNER,
                "Here is the text-only itinerary.",
                Map.of(),
                null
        ));
        when(conversationRepository.findMessages(conversationId)).thenReturn(fullMessages);
        when(conversationRepository.findTimeline(conversationId)).thenReturn(List.of());

        conversationWorkflow.execute(request);

        ArgumentCaptor<AgentExecutionContext> agentContextCaptor = ArgumentCaptor.forClass(AgentExecutionContext.class);
        verify(plannerAgent).execute(agentContextCaptor.capture());
        assertFalse(agentContextCaptor.getValue().recentMessages().getFirst().content().contains("West Lake"));
        assertFalse(agentContextCaptor.getValue().recentMessages().getFirst().content().contains("Attached images"));
    }
}
