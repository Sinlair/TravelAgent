package com.xx2201.travel.agent.app.service;

import com.xx2201.travel.agent.app.dto.ChatRequest;
import com.xx2201.travel.agent.app.dto.ChatResponse;
import com.xx2201.travel.agent.domain.event.TimelinePublisher;
import com.xx2201.travel.agent.domain.model.entity.ConversationMessage;
import com.xx2201.travel.agent.domain.model.entity.ConversationSession;
import com.xx2201.travel.agent.domain.model.entity.TaskMemory;
import com.xx2201.travel.agent.domain.model.entity.TimelineEvent;
import com.xx2201.travel.agent.domain.model.entity.TravelPlan;
import com.xx2201.travel.agent.domain.model.valobj.AgentExecutionContext;
import com.xx2201.travel.agent.domain.model.valobj.AgentExecutionResult;
import com.xx2201.travel.agent.domain.model.valobj.AgentRouteDecision;
import com.xx2201.travel.agent.domain.model.valobj.AgentType;
import com.xx2201.travel.agent.domain.model.valobj.ExecutionStage;
import com.xx2201.travel.agent.domain.model.valobj.LongTermMemoryItem;
import com.xx2201.travel.agent.domain.model.valobj.MessageRole;
import com.xx2201.travel.agent.domain.model.valobj.RoutingContext;
import com.xx2201.travel.agent.domain.repository.ConversationRepository;
import com.xx2201.travel.agent.domain.repository.LongTermMemoryRepository;
import com.xx2201.travel.agent.domain.service.AgentRouter;
import com.xx2201.travel.agent.domain.service.ConversationSummarizer;
import com.xx2201.travel.agent.domain.service.SpecialistAgent;
import com.xx2201.travel.agent.domain.service.TaskMemoryExtractor;
import com.xx2201.travel.agent.infrastructure.config.TravelAgentProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ConversationWorkflow {

    private static final String DEFAULT_CLARIFICATION_ZH = "\u8bf7\u5148\u8865\u5145\u76ee\u7684\u5730\u3001\u5929\u6570\u548c\u9884\u7b97\uff0c\u6211\u518d\u7ee7\u7eed\u7ec6\u5316\u884c\u7a0b\u3002";
    private static final String DEFAULT_CLARIFICATION_EN = "To continue planning, please share the destination, trip length, and budget.";
    private static final String DEFAULT_TITLE_ZH = "\u65b0\u7684\u65c5\u884c\u89c4\u5212";
    private static final String DEFAULT_TITLE_EN = "New conversation";

    private final ConversationRepository conversationRepository;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final AgentRouter agentRouter;
    private final TaskMemoryExtractor taskMemoryExtractor;
    private final ConversationSummarizer conversationSummarizer;
    private final Map<AgentType, SpecialistAgent> specialistAgents;
    private final TimelinePublisher timelinePublisher;
    private final TravelAgentProperties properties;

    public ConversationWorkflow(
            ConversationRepository conversationRepository,
            LongTermMemoryRepository longTermMemoryRepository,
            AgentRouter agentRouter,
            TaskMemoryExtractor taskMemoryExtractor,
            ConversationSummarizer conversationSummarizer,
            List<SpecialistAgent> specialistAgents,
            TimelinePublisher timelinePublisher,
            TravelAgentProperties properties
    ) {
        this.conversationRepository = conversationRepository;
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.agentRouter = agentRouter;
        this.taskMemoryExtractor = taskMemoryExtractor;
        this.conversationSummarizer = conversationSummarizer;
        this.specialistAgents = specialistAgents.stream().collect(Collectors.toMap(SpecialistAgent::supports, Function.identity()));
        this.timelinePublisher = timelinePublisher;
        this.properties = properties;
    }

    @Transactional
    public ChatResponse execute(ChatRequest request) {
        PreparedConversation preparedConversation = prepareConversation(request);
        MemoryContext memoryContext = buildMemoryContext(preparedConversation.conversationId(), request.message());
        AgentRouteDecision routeDecision = route(preparedConversation, request.message(), memoryContext);
        AgentOutcome agentOutcome = resolveAgentOutcome(preparedConversation, request.message(), memoryContext, routeDecision);
        return finalizeConversation(preparedConversation, memoryContext, routeDecision, agentOutcome);
    }

    private PreparedConversation prepareConversation(ChatRequest request) {
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? UUID.randomUUID().toString()
                : request.conversationId();

        ConversationSession session = conversationRepository.findConversation(conversationId)
                .orElseGet(() -> new ConversationSession(
                        conversationId,
                        titleOf(request.message()),
                        null,
                        null,
                        Instant.now(),
                        Instant.now()
                ));

        conversationRepository.saveConversation(new ConversationSession(
                conversationId,
                session.title(),
                session.lastAgent(),
                session.summary(),
                session.createdAt(),
                Instant.now()
        ));

        conversationRepository.saveMessage(new ConversationMessage(
                UUID.randomUUID().toString(),
                conversationId,
                MessageRole.USER,
                request.message(),
                null,
                Instant.now()
        ));

        publish(conversationId, ExecutionStage.ANALYZE_QUERY, "Analyze user intent and missing slots", Map.of("message", request.message()));
        return new PreparedConversation(conversationId, session);
    }

    private MemoryContext buildMemoryContext(String conversationId, String userMessage) {
        TaskMemory storedTaskMemory = conversationRepository.findTaskMemory(conversationId)
                .orElse(TaskMemory.empty(conversationId));
        List<ConversationMessage> recentMessages = conversationRepository.findRecentMessages(conversationId, properties.getMemoryWindow());
        TaskMemory workingMemory = taskMemoryExtractor.extract(storedTaskMemory, recentMessages);
        List<LongTermMemoryItem> longTermMemories = longTermMemoryRepository.searchRelevant(userMessage, 3);

        publish(conversationId, ExecutionStage.RECALL_MEMORY, "Recall short-term window, summary, and long-term memory", Map.of(
                "longTermCount", longTermMemories.size()
        ));

        return new MemoryContext(storedTaskMemory, recentMessages, workingMemory, longTermMemories);
    }

    private AgentRouteDecision route(PreparedConversation preparedConversation, String userMessage, MemoryContext memoryContext) {
        AgentRouteDecision routeDecision = agentRouter.route(new RoutingContext(
                preparedConversation.conversationId(),
                userMessage,
                memoryContext.recentMessages(),
                memoryContext.workingMemory(),
                preparedConversation.session().summary(),
                memoryContext.longTermMemories()
        ));

        AgentRouteDecision normalizedDecision = routeDecision == null
                ? new AgentRouteDecision(AgentType.GENERAL, "fallback route", false, null)
                : new AgentRouteDecision(
                        routeDecision.agentType() == null ? AgentType.GENERAL : routeDecision.agentType(),
                        routeDecision.reason() == null || routeDecision.reason().isBlank() ? "fallback route" : routeDecision.reason(),
                        routeDecision.clarificationRequired(),
                        routeDecision.clarificationQuestion()
                );

        publish(preparedConversation.conversationId(), ExecutionStage.SELECT_AGENT, "Route request to the best agent", Map.of(
                "agent", normalizedDecision.agentType().name(),
                "reason", normalizedDecision.reason()
        ));

        return normalizedDecision;
    }

    private AgentOutcome resolveAgentOutcome(
            PreparedConversation preparedConversation,
            String userMessage,
            MemoryContext memoryContext,
            AgentRouteDecision routeDecision
    ) {
        AgentType agentType = routeDecision.agentType() == null ? AgentType.GENERAL : routeDecision.agentType();
        if (routeDecision.clarificationRequired()) {
            return new AgentOutcome(
                    agentType,
                    clarificationQuestion(userMessage, routeDecision.clarificationQuestion()),
                    null
            );
        }

        publish(preparedConversation.conversationId(), ExecutionStage.SPECIALIST, "Execute specialist agent", Map.of("agent", agentType.name()));
        AgentExecutionResult result = selectSpecialist(agentType).execute(new AgentExecutionContext(
                preparedConversation.conversationId(),
                userMessage,
                memoryContext.recentMessages(),
                memoryContext.workingMemory(),
                preparedConversation.session().summary(),
                memoryContext.longTermMemories(),
                routeDecision.reason()
        ));

        return new AgentOutcome(agentType, result.answer(), result.travelPlan());
    }

    private ChatResponse finalizeConversation(
            PreparedConversation preparedConversation,
            MemoryContext memoryContext,
            AgentRouteDecision routeDecision,
            AgentOutcome agentOutcome
    ) {
        String conversationId = preparedConversation.conversationId();

        conversationRepository.saveMessage(new ConversationMessage(
                UUID.randomUUID().toString(),
                conversationId,
                MessageRole.ASSISTANT,
                agentOutcome.answer(),
                agentOutcome.agentType(),
                Instant.now()
        ));

        List<ConversationMessage> fullMessages = conversationRepository.findMessages(conversationId);
        TaskMemory updatedMemory = taskMemoryExtractor.extract(memoryContext.storedTaskMemory(), fullMessages);
        if (routeDecision.clarificationRequired()) {
            updatedMemory = updatedMemory.merge(new TaskMemory(
                    conversationId,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    agentOutcome.answer(),
                    null,
                    Instant.now()
            ));
        }
        conversationRepository.saveTaskMemory(updatedMemory);

        if (agentOutcome.travelPlan() != null) {
            conversationRepository.saveTravelPlan(agentOutcome.travelPlan());
        }

        String summary = preparedConversation.session().summary();
        if (fullMessages.size() >= properties.getSummaryThreshold()) {
            summary = conversationSummarizer.summarize(summary, fullMessages);
        }
        conversationRepository.saveConversation(new ConversationSession(
                conversationId,
                preparedConversation.session().title(),
                agentOutcome.agentType(),
                summary,
                preparedConversation.session().createdAt(),
                Instant.now()
        ));

        if (summary != null && !summary.isBlank()) {
            longTermMemoryRepository.saveMemory(
                    conversationId,
                    agentOutcome.agentType().name(),
                    summary,
                    Map.of(
                            "destination", updatedMemory.destination() == null ? "" : updatedMemory.destination(),
                            "days", updatedMemory.days() == null ? "" : updatedMemory.days(),
                            "budget", updatedMemory.budget() == null ? "" : updatedMemory.budget()
                    )
            );
        }

        publish(conversationId, ExecutionStage.FINALIZE_MEMORY, "Persist summary, task memory, and structured plan", Map.of(
                "hasSummary", summary != null && !summary.isBlank(),
                "hasPlan", agentOutcome.travelPlan() != null
        ));
        publish(conversationId, ExecutionStage.COMPLETED, "Execution finished", Map.of("agent", agentOutcome.agentType().name()));

        return new ChatResponse(
                conversationId,
                agentOutcome.agentType(),
                agentOutcome.answer(),
                updatedMemory,
                agentOutcome.travelPlan(),
                conversationRepository.findTimeline(conversationId)
        );
    }

    private SpecialistAgent selectSpecialist(AgentType agentType) {
        SpecialistAgent specialistAgent = specialistAgents.getOrDefault(agentType, specialistAgents.get(AgentType.GENERAL));
        if (specialistAgent == null) {
            throw new IllegalStateException("No specialist agent registered for " + agentType);
        }
        return specialistAgent;
    }

    private void publish(String conversationId, ExecutionStage stage, String message, Map<String, Object> details) {
        timelinePublisher.publish(TimelineEvent.of(conversationId, stage, message, details));
    }

    private String clarificationQuestion(String message, String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            return candidate;
        }
        return containsChinese(message) ? DEFAULT_CLARIFICATION_ZH : DEFAULT_CLARIFICATION_EN;
    }

    private String titleOf(String message) {
        String content = message == null ? DEFAULT_TITLE_EN : message.strip();
        if (content.isBlank()) {
            return containsChinese(message) ? DEFAULT_TITLE_ZH : DEFAULT_TITLE_EN;
        }
        return content.length() > 18 ? content.substring(0, 18) + "..." : content;
    }

    private boolean containsChinese(String value) {
        return value != null && value.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    private record PreparedConversation(
            String conversationId,
            ConversationSession session
    ) {
    }

    private record MemoryContext(
            TaskMemory storedTaskMemory,
            List<ConversationMessage> recentMessages,
            TaskMemory workingMemory,
            List<LongTermMemoryItem> longTermMemories
    ) {
    }

    private record AgentOutcome(
            AgentType agentType,
            String answer,
            TravelPlan travelPlan
    ) {
    }
}

