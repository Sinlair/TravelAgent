package com.travalagent.app.service;

import com.travalagent.app.dto.ChatRequest;
import com.travalagent.app.dto.ChatImageAttachmentRequest;
import com.travalagent.app.dto.ChatResponse;
import com.travalagent.app.dto.ChatResponseFeedbackTarget;
import com.travalagent.app.dto.ChatResponseIssue;
import com.travalagent.app.dto.ConversationConstraintSummary;
import com.travalagent.app.dto.ConversationMissingInformationItem;
import com.travalagent.app.dto.ReplanScopeRequest;
import com.travalagent.app.dto.TripBriefRequest;
import com.travalagent.domain.event.TimelinePublisher;
import com.travalagent.domain.model.entity.ConversationMessage;
import com.travalagent.domain.model.entity.ConversationSession;
import com.travalagent.domain.model.entity.ConversationImageAttachment;
import com.travalagent.domain.model.entity.ConversationImageFacts;
import com.travalagent.domain.model.entity.ConversationImageContext;
import com.travalagent.domain.model.entity.TaskMemory;
import com.travalagent.domain.model.entity.TimelineEvent;
import com.travalagent.domain.model.entity.TravelChecklistItem;
import com.travalagent.domain.model.entity.TravelPlan;
import com.travalagent.domain.model.entity.TravelPlanDay;
import com.travalagent.domain.model.entity.TravelPlanVersionSnapshot;
import com.travalagent.domain.model.valobj.AgentExecutionContext;
import com.travalagent.domain.model.valobj.AgentExecutionResult;
import com.travalagent.domain.model.valobj.AgentRouteDecision;
import com.travalagent.domain.model.valobj.AgentType;
import com.travalagent.domain.model.valobj.ExecutionStage;
import com.travalagent.domain.model.valobj.ImageAttachment;
import com.travalagent.domain.model.valobj.ImageAttachmentInterpretation;
import com.travalagent.domain.model.valobj.LongTermMemoryItem;
import com.travalagent.domain.model.valobj.MessageRole;
import com.travalagent.domain.model.valobj.RoutingContext;
import com.travalagent.domain.repository.ConversationRepository;
import com.travalagent.domain.repository.LongTermMemoryRepository;
import com.travalagent.domain.service.AgentRouter;
import com.travalagent.domain.service.ConversationSummarizer;
import com.travalagent.domain.service.ImageAttachmentInterpreter;
import com.travalagent.domain.service.SpecialistAgent;
import com.travalagent.domain.service.TaskMemoryExtractor;
import com.travalagent.infrastructure.config.TravelAgentProperties;
import com.travalagent.types.enums.ResponseCode;
import com.travalagent.types.exception.AppException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ConversationWorkflow {

    private static final int MAX_IMAGE_ATTACHMENTS = 4;
    private static final int MAX_IMAGE_ATTACHMENT_BYTES = 5 * 1024 * 1024;
    private static final Pattern DATA_URL_PATTERN = Pattern.compile("^data:([^;]+);base64,(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    private static final String DEFAULT_CLARIFICATION_ZH = "\u8bf7\u5148\u8865\u5145\u76ee\u7684\u5730\u3001\u5929\u6570\u548c\u9884\u7b97\uff0c\u6211\u518d\u7ee7\u7eed\u7ec6\u5316\u884c\u7a0b\u3002";
    private static final String DEFAULT_CLARIFICATION_EN = "To continue planning, please share the destination, trip length, and budget.";
    private static final String DEFAULT_TITLE_ZH = "\u65b0\u7684\u65c5\u884c\u89c4\u5212";
    private static final String DEFAULT_TITLE_EN = "New conversation";
    private static final String IMAGE_ONLY_MESSAGE_ZH = "\u8bf7\u6839\u636e\u6211\u4e0a\u4f20\u7684\u56fe\u7247\u6574\u7406\u65c5\u884c\u4fe1\u606f\uff0c\u5e76\u5e2e\u6211\u89c4\u5212\u884c\u7a0b\u3002";
    private static final String IMAGE_ONLY_MESSAGE_EN = "Please use the uploaded travel images as context and help plan the trip.";
    private static final String IMAGE_UPLOAD_NOTE_ZH = "\u5df2\u4e0a\u4f20\u65c5\u884c\u56fe\u7247";
    private static final String IMAGE_UPLOAD_NOTE_EN = "Uploaded travel image context";
    private static final String IMAGE_CONFIRM_MESSAGE_ZH = "\u8bf7\u4f7f\u7528\u6211\u4e0a\u4f20\u56fe\u7247\u91cc\u63d0\u53d6\u7684\u4fe1\u606f\u7ee7\u7eed\u89c4\u5212\u3002";
    private static final String IMAGE_CONFIRM_MESSAGE_EN = "Use the extracted travel facts from my uploaded images and continue planning.";
    private static final String IMAGE_CONFIRM_PROMPT_ZH = "\u6211\u5df2\u4ece\u4f60\u4e0a\u4f20\u7684\u56fe\u7247\u91cc\u63d0\u53d6\u4e86\u53ef\u80fd\u7528\u4e8e\u884c\u7a0b\u89c4\u5212\u7684\u4fe1\u606f\u3002\u8bf7\u786e\u8ba4\u662f\u5426\u4f7f\u7528\u8fd9\u4e9b\u4fe1\u606f\uff0c\u6216\u8005\u518d\u8865\u5145\u4f60\u60f3\u4fee\u6b63\u7684\u5185\u5bb9\u3002";
    private static final String IMAGE_CONFIRM_PROMPT_EN = "I extracted travel-relevant facts from your uploaded images. Please confirm whether to use them, or add any corrections before planning.";
    private static final String IMAGE_DISMISS_ACK_ZH = "\u597d\u7684\uff0c\u6211\u4e0d\u4f1a\u4f7f\u7528\u8fd9\u6279\u56fe\u7247\u63d0\u53d6\u7ed3\u679c\u3002\u4f60\u53ef\u4ee5\u76f4\u63a5\u8f93\u5165\u6587\u672c\u8981\u6c42\uff0c\u6216\u91cd\u65b0\u4e0a\u4f20\u56fe\u7247\u3002";
    private static final String IMAGE_DISMISS_ACK_EN = "Okay. I will ignore the extracted image facts for now. You can continue with text input or upload different images.";
    private static final String IMAGE_CONTEXT_STATUS_PENDING = "PENDING";
    private static final String IMAGE_CONTEXT_STATUS_CONFIRMED = "CONFIRMED";
    private static final String IMAGE_CONTEXT_STATUS_DISMISSED = "DISMISSED";

    private final ConversationRepository conversationRepository;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final AgentRouter agentRouter;
    private final TaskMemoryExtractor taskMemoryExtractor;
    private final ConversationSummarizer conversationSummarizer;
    private final ImageAttachmentInterpreter imageAttachmentInterpreter;
    private final Map<AgentType, SpecialistAgent> specialistAgents;
    private final TimelinePublisher timelinePublisher;
    private final TravelAgentProperties properties;

    public ConversationWorkflow(
            ConversationRepository conversationRepository,
            LongTermMemoryRepository longTermMemoryRepository,
            AgentRouter agentRouter,
            TaskMemoryExtractor taskMemoryExtractor,
            ConversationSummarizer conversationSummarizer,
            ImageAttachmentInterpreter imageAttachmentInterpreter,
            List<SpecialistAgent> specialistAgents,
            TimelinePublisher timelinePublisher,
            TravelAgentProperties properties
    ) {
        this.conversationRepository = conversationRepository;
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.agentRouter = agentRouter;
        this.taskMemoryExtractor = taskMemoryExtractor;
        this.conversationSummarizer = conversationSummarizer;
        this.imageAttachmentInterpreter = imageAttachmentInterpreter;
        this.specialistAgents = specialistAgents.stream().collect(Collectors.toMap(SpecialistAgent::supports, Function.identity()));
        this.timelinePublisher = timelinePublisher;
        this.properties = properties;
    }

    @Transactional
    public ChatResponse execute(ChatRequest request) {
        List<ImageAttachment> imageAttachments = normalizeImageAttachments(request.attachments());
        String rawUserMessage = normalizeText(request.message());
        TaskMemory briefPatch = taskMemoryPatchFromBrief(request.conversationId(), request.brief());
        ImageContextAction imageContextAction = normalizeImageContextAction(request.imageContextAction());
        ReplanScopeSelection replanScope = normalizeReplanScope(request.conversationId(), request.replanScope());
        TravelPlan existingPlan = currentPlanForReplan(request.conversationId(), replanScope);
        if (imageContextAction == ImageContextAction.DISMISS) {
            return dismissPendingImageContext(request.conversationId(), rawUserMessage);
        }

        ConversationImageContext pendingImageContext = request.conversationId() == null || request.conversationId().isBlank()
                ? null
                : conversationRepository.findPendingImageContext(request.conversationId()).orElse(null);
        if (!imageAttachments.isEmpty() && imageContextAction != ImageContextAction.CONFIRM) {
            return stagePendingImageContext(request.conversationId(), rawUserMessage, imageAttachments);
        }

        ConversationImageFacts confirmedFacts = null;
        String imageContextSummary;
        if (imageContextAction == ImageContextAction.CONFIRM && pendingImageContext != null) {
            rawUserMessage = buildConfirmedUserMessage(rawUserMessage, pendingImageContext.summary());
            imageContextSummary = pendingImageContext.summary();
            confirmedFacts = pendingImageContext.facts();
            imageAttachments = List.of();
        } else if (!imageAttachments.isEmpty()) {
            ImageAttachmentInterpretation interpretation = imageAttachmentInterpreter.interpretTravelContext(rawUserMessage, imageAttachments);
            imageContextSummary = interpretation == null ? null : interpretation.summary();
        } else {
            imageContextSummary = null;
        }
        String effectiveUserMessage = buildEffectiveUserMessage(
                rawUserMessage,
                imageAttachments,
                imageContextSummary,
                briefPatch,
                replanScope,
                existingPlan
        );

        PreparedConversation preparedConversation = prepareConversation(
                request.conversationId(),
                rawUserMessage,
                effectiveUserMessage,
                imageAttachments,
                imageContextSummary,
                confirmedFacts,
                imageContextAction == ImageContextAction.CONFIRM && pendingImageContext != null ? IMAGE_CONTEXT_STATUS_CONFIRMED : null,
                briefPatch
        );
        MemoryContext memoryContext = buildMemoryContext(preparedConversation.conversationId(), effectiveUserMessage, confirmedFacts, briefPatch);
        AgentRouteDecision routeDecision = route(preparedConversation, effectiveUserMessage, memoryContext);
        AgentOutcome agentOutcome = resolveAgentOutcome(
                preparedConversation,
                effectiveUserMessage,
                memoryContext,
                routeDecision,
                imageAttachments,
                imageContextSummary
        );
        if (imageContextAction == ImageContextAction.CONFIRM && pendingImageContext != null) {
            conversationRepository.deletePendingImageContext(preparedConversation.conversationId());
        }
        return finalizeConversation(
                preparedConversation,
                memoryContext,
                routeDecision,
                agentOutcome,
                existingPlan,
                replanScope,
                effectiveUserMessage
        );
    }

    private ChatResponse stagePendingImageContext(
            String requestedConversationId,
            String rawUserMessage,
            List<ImageAttachment> imageAttachments
    ) {
        String conversationId = requestedConversationId == null || requestedConversationId.isBlank()
                ? UUID.randomUUID().toString()
                : requestedConversationId;
        ImageAttachmentInterpretation interpretation = imageAttachmentInterpreter.interpretTravelContext(rawUserMessage, imageAttachments);
        String imageContextSummary = interpretation == null || interpretation.summary() == null || interpretation.summary().isBlank()
                ? (containsChinese(rawUserMessage) ? "\u672a\u80fd\u4ece\u56fe\u7247\u91cc\u63d0\u53d6\u51fa\u786e\u5b9a\u7684\u65c5\u884c\u4fe1\u606f\u3002" : "I could not extract clear travel facts from the uploaded images.")
                : interpretation.summary();
        ConversationImageFacts imageFacts = interpretation == null ? emptyImageFacts() : interpretation.facts();
        PreparedConversation preparedConversation = prepareConversation(
                conversationId,
                rawUserMessage,
                storedUserMessage(rawUserMessage, null, imageAttachments),
                imageAttachments,
                null,
                null,
                IMAGE_CONTEXT_STATUS_PENDING,
                null
        );
        ConversationImageContext pendingImageContext = new ConversationImageContext(
                conversationId,
                imageContextSummary,
                imageFacts,
                imageAttachments.stream()
                        .map(item -> new ConversationImageAttachment(item.id(), item.name(), item.mediaType(), item.sizeBytes()))
                        .toList(),
                Instant.now(),
                Instant.now()
        );
        conversationRepository.savePendingImageContext(pendingImageContext);
        String assistantAnswer = containsChinese(rawUserMessage == null ? imageContextSummary : rawUserMessage)
                ? IMAGE_CONFIRM_PROMPT_ZH
                : IMAGE_CONFIRM_PROMPT_EN;
        String assistantMessageId = UUID.randomUUID().toString();
        conversationRepository.saveMessage(new ConversationMessage(
                assistantMessageId,
                conversationId,
                MessageRole.ASSISTANT,
                assistantAnswer,
                AgentType.GENERAL,
                Instant.now(),
                Map.of()
        ));
        conversationRepository.saveConversation(new ConversationSession(
                conversationId,
                preparedConversation.session().title(),
                AgentType.GENERAL,
                preparedConversation.session().summary(),
                preparedConversation.session().createdAt(),
                Instant.now()
        ));
        publish(conversationId, ExecutionStage.COMPLETED, "Image context extracted and awaiting confirmation", Map.of(
                "imageAttachmentCount", imageAttachments.size()
        ));
        return buildChatResponse(
                conversationId,
                AgentType.GENERAL,
                assistantAnswer,
                conversationRepository.findTaskMemory(conversationId).orElse(TaskMemory.empty(conversationId)),
                null,
                conversationRepository.findTimeline(conversationId),
                assistantMessageId,
                null,
                Map.of(),
                pendingImageContext
        );
    }

    private ChatResponse dismissPendingImageContext(String requestedConversationId, String rawUserMessage) {
        if (requestedConversationId == null || requestedConversationId.isBlank()) {
            throw new AppException(ResponseCode.INVALID_REQUEST, "Conversation ID is required to dismiss pending image context");
        }
        String conversationId = requestedConversationId.trim();
        ConversationSession session = conversationRepository.findConversation(conversationId)
                .orElseThrow(() -> new AppException(ResponseCode.INVALID_REQUEST, "Conversation not found"));
        conversationRepository.deletePendingImageContext(conversationId);
        String userMessage = rawUserMessage == null || rawUserMessage.isBlank()
                ? (containsChinese(session.title()) ? "\u4e0d\u4f7f\u7528\u8fd9\u6279\u56fe\u7247\u63d0\u53d6\u7ed3\u679c" : "Ignore the extracted image facts")
                : rawUserMessage;
        conversationRepository.saveMessage(new ConversationMessage(
                UUID.randomUUID().toString(),
                conversationId,
                MessageRole.USER,
                userMessage,
                null,
                Instant.now(),
                Map.of("imageContextStatus", IMAGE_CONTEXT_STATUS_DISMISSED)
        ));
        String assistantAnswer = containsChinese(userMessage) ? IMAGE_DISMISS_ACK_ZH : IMAGE_DISMISS_ACK_EN;
        String assistantMessageId = UUID.randomUUID().toString();
        conversationRepository.saveMessage(new ConversationMessage(
                assistantMessageId,
                conversationId,
                MessageRole.ASSISTANT,
                assistantAnswer,
                AgentType.GENERAL,
                Instant.now(),
                Map.of()
        ));
        conversationRepository.saveConversation(new ConversationSession(
                conversationId,
                session.title(),
                AgentType.GENERAL,
                session.summary(),
                session.createdAt(),
                Instant.now()
        ));
        publish(conversationId, ExecutionStage.COMPLETED, "Pending image context dismissed", Map.of());
        return buildChatResponse(
                conversationId,
                AgentType.GENERAL,
                assistantAnswer,
                conversationRepository.findTaskMemory(conversationId).orElse(TaskMemory.empty(conversationId)),
                null,
                conversationRepository.findTimeline(conversationId),
                assistantMessageId,
                null,
                Map.of(),
                null
        );
    }

    private PreparedConversation prepareConversation(
            String requestedConversationId,
            String rawUserMessage,
            String effectiveUserMessage,
            List<ImageAttachment> imageAttachments,
            String imageContextSummary,
            ConversationImageFacts imageFacts,
            String imageContextStatus,
            TaskMemory briefPatch
    ) {
        String conversationId = requestedConversationId == null || requestedConversationId.isBlank()
                ? UUID.randomUUID().toString()
                : requestedConversationId;
        String storedMessage = storedUserMessage(rawUserMessage, effectiveUserMessage, imageAttachments);

        ConversationSession session = conversationRepository.findConversation(conversationId)
                .orElseGet(() -> new ConversationSession(
                        conversationId,
                        titleOf(storedMessage),
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
                storedMessage,
                null,
                Instant.now(),
                userMessageMetadata(imageAttachments, imageContextSummary, imageFacts, imageContextStatus, briefPatch)
        ));

        Map<String, Object> analysisDetails = new LinkedHashMap<>();
        analysisDetails.put("message", storedMessage);
        if (!imageAttachments.isEmpty()) {
            analysisDetails.put("imageAttachmentCount", imageAttachments.size());
        }
        publish(conversationId, ExecutionStage.ANALYZE_QUERY, "Analyze user intent and missing slots", analysisDetails);
        if (imageContextSummary != null && !imageContextSummary.isBlank()) {
            publish(conversationId, ExecutionStage.ANALYZE_QUERY, "Extract travel context from uploaded images", Map.of(
                    "imageAttachmentCount", imageAttachments.size()
            ));
        }
        return new PreparedConversation(conversationId, session);
    }

    private MemoryContext buildMemoryContext(String conversationId, String userMessage, ConversationImageFacts confirmedFacts, TaskMemory briefPatch) {
        TaskMemory storedTaskMemory = conversationRepository.findTaskMemory(conversationId)
                .orElse(TaskMemory.empty(conversationId));
        List<ConversationMessage> recentMessages = enrichMessagesForPlanning(
                conversationRepository.findRecentMessages(conversationId, properties.getMemoryWindow())
        );
        TaskMemory seededMemory = storedTaskMemory;
        if (briefPatch != null) {
            seededMemory = seededMemory.merge(briefPatch);
        }
        if (confirmedFacts != null) {
            seededMemory = seededMemory.merge(taskMemoryPatchFromFacts(conversationId, confirmedFacts));
        }
        TaskMemory workingMemory = taskMemoryExtractor.extract(seededMemory, recentMessages);
        List<LongTermMemoryItem> longTermMemories = longTermMemoryRepository.searchRelevant(userMessage, 3);

        publish(conversationId, ExecutionStage.RECALL_MEMORY, "Recall short-term window, summary, and long-term memory", Map.of(
                "longTermCount", longTermMemories.size()
        ));

        return new MemoryContext(seededMemory, recentMessages, workingMemory, longTermMemories);
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
            AgentRouteDecision routeDecision,
            List<ImageAttachment> imageAttachments,
            String imageContextSummary
    ) {
        AgentType agentType = routeDecision.agentType() == null ? AgentType.GENERAL : routeDecision.agentType();
        if (routeDecision.clarificationRequired()) {
            return new AgentOutcome(
                    agentType,
                    clarificationQuestion(userMessage, routeDecision.clarificationQuestion()),
                    Map.of(),
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
                routeDecision.reason(),
                imageAttachments,
                imageContextSummary
        ));

        return new AgentOutcome(agentType, result.answer(), result.metadata(), result.travelPlan());
    }

    private ChatResponse finalizeConversation(
            PreparedConversation preparedConversation,
            MemoryContext memoryContext,
            AgentRouteDecision routeDecision,
            AgentOutcome agentOutcome,
            TravelPlan existingPlan,
            ReplanScopeSelection replanScope,
            String effectiveUserMessage
    ) {
        String conversationId = preparedConversation.conversationId();

        String assistantMessageId = UUID.randomUUID().toString();
        TravelPlan finalizedPlan = finalizeTravelPlan(agentOutcome.travelPlan(), memoryContext.workingMemory(), existingPlan, replanScope);
        String planVersion = null;
        if (finalizedPlan != null) {
            conversationRepository.saveTravelPlan(finalizedPlan);
            planVersion = persistPlanVersion(conversationId, finalizedPlan, effectiveUserMessage, replanScope);
        }
        ChatResponseFeedbackTarget feedbackTarget = ConversationResultSupport.buildFeedbackTarget(
                conversationId,
                assistantMessageId,
                agentOutcome.agentType(),
                finalizedPlan,
                planVersion
        );
        conversationRepository.saveMessage(new ConversationMessage(
                assistantMessageId,
                conversationId,
                MessageRole.ASSISTANT,
                agentOutcome.answer(),
                agentOutcome.agentType(),
                Instant.now(),
                ConversationResultSupport.assistantMetadata(feedbackTarget, agentOutcome.metadata())
        ));

        List<ConversationMessage> fullMessages = enrichMessagesForPlanning(conversationRepository.findMessages(conversationId));
        TaskMemory updatedMemory = taskMemoryExtractor.extract(memoryContext.storedTaskMemory(), fullMessages);
        if (routeDecision.clarificationRequired()) {
            updatedMemory = updatedMemory.merge(new TaskMemory(
                    conversationId,
                    null,
                    null,
                    null,
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
                "hasPlan", finalizedPlan != null
        ));
        publish(conversationId, ExecutionStage.COMPLETED, "Execution finished", Map.of("agent", agentOutcome.agentType().name()));

        return buildChatResponse(
                conversationId,
                agentOutcome.agentType(),
                agentOutcome.answer(),
                updatedMemory,
                finalizedPlan,
                conversationRepository.findTimeline(conversationId),
                assistantMessageId,
                planVersion,
                agentOutcome.metadata(),
                null
        );
    }

    private ChatResponse buildChatResponse(
            String conversationId,
            AgentType agentType,
            String answer,
            TaskMemory taskMemory,
            TravelPlan travelPlan,
            List<TimelineEvent> timeline,
            String feedbackTargetId,
            String planVersion,
            Map<String, Object> resultMetadata,
            ConversationImageContext imageContextCandidate
    ) {
        ChatResponseFeedbackTarget feedbackTarget = ConversationResultSupport.buildFeedbackTarget(
                conversationId,
                feedbackTargetId,
                agentType,
                travelPlan,
                planVersion
        );
        List<ConversationMissingInformationItem> missingInformation = ConversationResultSupport.buildMissingInformation(
                taskMemory,
                imageContextCandidate
        );
        ConversationConstraintSummary constraintSummary = ConversationResultSupport.buildConstraintSummary(
                agentType,
                travelPlan,
                resultMetadata
        );
        List<ChatResponseIssue> issues = ConversationResultSupport.buildIssues(
                taskMemory,
                imageContextCandidate,
                missingInformation,
                constraintSummary
        );
        return new ChatResponse(
                conversationId,
                agentType,
                answer,
                taskMemory,
                travelPlan,
                timeline,
                feedbackTarget,
                issues,
                missingInformation,
                constraintSummary
        );
    }

    private TravelPlan finalizeTravelPlan(
            TravelPlan candidatePlan,
            TaskMemory taskMemory,
            TravelPlan existingPlan,
            ReplanScopeSelection replanScope
    ) {
        if (candidatePlan == null) {
            return null;
        }
        TravelPlan datedPlan = applyTripDates(candidatePlan, taskMemory);
        TravelPlan mergedPlan = mergeScopedReplan(datedPlan, existingPlan, replanScope);
        List<TravelChecklistItem> checklist = buildChecklist(mergedPlan, taskMemory, existingPlan == null ? List.of() : existingPlan.checklist());
        return mergedPlan.withExecutionContext(checklist, refreshedSections(replanScope), Instant.now());
    }

    private TravelPlan applyTripDates(TravelPlan plan, TaskMemory taskMemory) {
        if (plan == null || taskMemory == null || taskMemory.startDate() == null || taskMemory.startDate().isBlank()) {
            return plan;
        }
        LocalDate startDate;
        try {
            startDate = LocalDate.parse(taskMemory.startDate().trim());
        } catch (DateTimeParseException ignored) {
            return plan;
        }
        List<TravelPlanDay> datedDays = new java.util.ArrayList<>();
        for (int i = 0; i < plan.days().size(); i++) {
            TravelPlanDay day = plan.days().get(i);
            datedDays.add(new TravelPlanDay(
                    day.dayNumber(),
                    startDate.plusDays(i).toString(),
                    day.theme(),
                    day.startTime(),
                    day.endTime(),
                    day.totalTransitMinutes(),
                    day.totalActivityMinutes(),
                    day.estimatedCost(),
                    day.stops(),
                    day.returnToHotel()
            ));
        }
        return new TravelPlan(
                plan.conversationId(),
                plan.title(),
                plan.summary(),
                plan.hotelArea(),
                plan.hotelAreaReason(),
                plan.hotels(),
                plan.totalBudget(),
                plan.estimatedTotalMin(),
                plan.estimatedTotalMax(),
                plan.highlights(),
                plan.budget(),
                plan.checks(),
                datedDays,
                plan.weatherSnapshot(),
                plan.knowledgeRetrieval(),
                plan.constraintRelaxed(),
                plan.adjustmentSuggestions(),
                plan.checklist(),
                plan.refreshedSections(),
                plan.updatedAt()
        );
    }

    private TravelPlan mergeScopedReplan(TravelPlan candidatePlan, TravelPlan existingPlan, ReplanScopeSelection replanScope) {
        if (candidatePlan == null || existingPlan == null || replanScope == null) {
            return candidatePlan;
        }
        return switch (replanScope.scope()) {
            case "HOTEL_AREA" -> new TravelPlan(
                    existingPlan.conversationId(),
                    candidatePlan.title(),
                    candidatePlan.summary(),
                    candidatePlan.hotelArea(),
                    candidatePlan.hotelAreaReason(),
                    candidatePlan.hotels(),
                    existingPlan.totalBudget(),
                    candidatePlan.estimatedTotalMin(),
                    candidatePlan.estimatedTotalMax(),
                    existingPlan.highlights(),
                    candidatePlan.budget(),
                    candidatePlan.checks(),
                    existingPlan.days(),
                    candidatePlan.weatherSnapshot(),
                    candidatePlan.knowledgeRetrieval(),
                    candidatePlan.constraintRelaxed(),
                    candidatePlan.adjustmentSuggestions(),
                    existingPlan.checklist(),
                    existingPlan.refreshedSections(),
                    candidatePlan.updatedAt()
            );
            case "DAY" -> new TravelPlan(
                    existingPlan.conversationId(),
                    candidatePlan.title(),
                    candidatePlan.summary(),
                    existingPlan.hotelArea(),
                    existingPlan.hotelAreaReason(),
                    existingPlan.hotels(),
                    existingPlan.totalBudget(),
                    candidatePlan.estimatedTotalMin(),
                    candidatePlan.estimatedTotalMax(),
                    existingPlan.highlights(),
                    candidatePlan.budget(),
                    candidatePlan.checks(),
                    mergeDays(existingPlan.days(), candidatePlan.days(), replanScope.dayNumber()),
                    candidatePlan.weatherSnapshot() == null ? existingPlan.weatherSnapshot() : candidatePlan.weatherSnapshot(),
                    candidatePlan.knowledgeRetrieval() == null ? existingPlan.knowledgeRetrieval() : candidatePlan.knowledgeRetrieval(),
                    candidatePlan.constraintRelaxed(),
                    candidatePlan.adjustmentSuggestions(),
                    existingPlan.checklist(),
                    existingPlan.refreshedSections(),
                    candidatePlan.updatedAt()
            );
            default -> candidatePlan;
        };
    }

    private List<TravelPlanDay> mergeDays(List<TravelPlanDay> existingDays, List<TravelPlanDay> candidateDays, Integer targetDayNumber) {
        if (existingDays == null || existingDays.isEmpty()) {
            return candidateDays == null ? List.of() : candidateDays;
        }
        if (targetDayNumber == null || candidateDays == null || candidateDays.isEmpty()) {
            return existingDays;
        }
        TravelPlanDay refreshedDay = candidateDays.stream()
                .filter(day -> targetDayNumber.equals(day.dayNumber()))
                .findFirst()
                .orElse(candidateDays.getFirst());
        return existingDays.stream()
                .map(day -> targetDayNumber.equals(day.dayNumber()) ? refreshedDay : day)
                .toList();
    }

    private List<TravelChecklistItem> buildChecklist(TravelPlan plan, TaskMemory taskMemory, List<TravelChecklistItem> existingChecklist) {
        Map<String, Boolean> confirmedByKey = existingChecklist.stream()
                .collect(Collectors.toMap(TravelChecklistItem::key, TravelChecklistItem::confirmed, (left, right) -> right));
        String hotelSummary = plan.hotels().isEmpty()
                ? "Confirm the preferred hotel area before booking."
                : "Lock the stay in " + plan.hotels().getFirst().area() + " and confirm the final hotel.";
        return List.of(
                checklistItem("accommodation", "Accommodation", hotelSummary, confirmedByKey),
                checklistItem("transport", "Transport", taskMemory.origin() == null || taskMemory.origin().isBlank()
                        ? "Confirm how you will arrive at the destination and return."
                        : "Confirm outbound transport from " + taskMemory.origin() + ".", confirmedByKey),
                checklistItem("tickets", "Tickets", plan.highlights().isEmpty()
                        ? "Review whether any timed entry tickets are needed."
                        : "Check advance booking needs for " + String.join(", ", plan.highlights().stream().limit(3).toList()) + ".", confirmedByKey),
                checklistItem("weather", "Weather", renderDateWindow(taskMemory) == null
                        ? "Recheck the local weather closer to departure."
                        : "Recheck the weather for " + renderDateWindow(taskMemory) + " before departure.", confirmedByKey),
                checklistItem("essentials", "Travel Essentials", "Check IDs, charging gear, medicines, and payment setup.", confirmedByKey)
        );
    }

    private TravelChecklistItem checklistItem(String key, String title, String details, Map<String, Boolean> confirmedByKey) {
        return new TravelChecklistItem(key, title, details, confirmedByKey.getOrDefault(key, false));
    }

    private String persistPlanVersion(
            String conversationId,
            TravelPlan travelPlan,
            String effectiveUserMessage,
            ReplanScopeSelection replanScope
    ) {
        String versionId = UUID.randomUUID().toString();
        Instant createdAt = travelPlan.updatedAt() == null ? Instant.now() : travelPlan.updatedAt();
        conversationRepository.saveTravelPlanVersion(new TravelPlanVersionSnapshot(
                versionId,
                conversationId,
                summarizePlanInput(effectiveUserMessage),
                replanScope == null ? "FULL_PLAN" : replanScope.scope(),
                travelPlan,
                createdAt
        ));
        return createdAt.toString();
    }

    private String summarizePlanInput(String effectiveUserMessage) {
        if (effectiveUserMessage == null || effectiveUserMessage.isBlank()) {
            return "Travel plan refresh";
        }
        String normalized = effectiveUserMessage.strip();
        return normalized.length() > 200 ? normalized.substring(0, 200) : normalized;
    }

    private String renderDateWindow(TaskMemory taskMemory) {
        if (taskMemory == null) {
            return null;
        }
        if (taskMemory.startDate() != null && taskMemory.endDate() != null) {
            return taskMemory.startDate() + " to " + taskMemory.endDate();
        }
        return taskMemory.startDate();
    }

    private List<String> refreshedSections(ReplanScopeSelection replanScope) {
        if (replanScope == null) {
            return List.of();
        }
        if ("DAY".equals(replanScope.scope()) && replanScope.dayNumber() != null) {
            return List.of("DAY:" + replanScope.dayNumber());
        }
        return List.of(replanScope.scope());
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

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ImageContextAction normalizeImageContextAction(String value) {
        if (value == null || value.isBlank()) {
            return ImageContextAction.NONE;
        }
        return switch (value.trim().toUpperCase()) {
            case "CONFIRM" -> ImageContextAction.CONFIRM;
            case "DISMISS" -> ImageContextAction.DISMISS;
            default -> throw new AppException(ResponseCode.INVALID_REQUEST, "imageContextAction must be CONFIRM or DISMISS");
        };
    }

    private ReplanScopeSelection normalizeReplanScope(String requestedConversationId, ReplanScopeRequest request) {
        if (request == null || request.scope() == null || request.scope().isBlank()) {
            return null;
        }
        if (requestedConversationId == null || requestedConversationId.isBlank()) {
            throw new AppException(ResponseCode.INVALID_REQUEST, "Scoped replans require an existing conversation");
        }
        String normalizedScope = request.scope().trim().toUpperCase();
        return switch (normalizedScope) {
            case "HOTEL_AREA" -> new ReplanScopeSelection("HOTEL_AREA", null);
            case "DAY" -> {
                if (request.dayNumber() == null || request.dayNumber() < 1) {
                    throw new AppException(ResponseCode.INVALID_REQUEST, "DAY replan scope requires a positive dayNumber");
                }
                yield new ReplanScopeSelection("DAY", request.dayNumber());
            }
            default -> throw new AppException(ResponseCode.INVALID_REQUEST, "replanScope must be HOTEL_AREA or DAY");
        };
    }

    private TravelPlan currentPlanForReplan(String requestedConversationId, ReplanScopeSelection replanScope) {
        if (replanScope == null) {
            return null;
        }
        return conversationRepository.findTravelPlan(requestedConversationId.trim())
                .orElseThrow(() -> new AppException(ResponseCode.INVALID_REQUEST, "No existing plan is available for the requested scoped replan"));
    }

    private List<ImageAttachment> normalizeImageAttachments(List<ChatImageAttachmentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        if (requests.size() > MAX_IMAGE_ATTACHMENTS) {
            throw new AppException(ResponseCode.INVALID_REQUEST, "At most 4 image attachments are supported");
        }
        return requests.stream()
                .map(this::normalizeImageAttachment)
                .toList();
    }

    private ImageAttachment normalizeImageAttachment(ChatImageAttachmentRequest request) {
        var matcher = DATA_URL_PATTERN.matcher(request.dataUrl().trim());
        if (!matcher.matches()) {
            throw new AppException(ResponseCode.INVALID_REQUEST, "Image attachments must be base64 data URLs");
        }
        String mediaType = matcher.group(1).trim().toLowerCase();
        if (!mediaType.equals(request.mediaType().trim().toLowerCase())) {
            throw new AppException(ResponseCode.INVALID_REQUEST, "Image media type does not match the data URL");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(mediaType)) {
            throw new AppException(ResponseCode.INVALID_REQUEST, "Only PNG, JPEG, WEBP, and GIF images are supported");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(matcher.group(2).trim());
        } catch (IllegalArgumentException exception) {
            throw new AppException(ResponseCode.INVALID_REQUEST, "Image attachment is not valid base64");
        }
        if (bytes.length > MAX_IMAGE_ATTACHMENT_BYTES) {
            throw new AppException(ResponseCode.INVALID_REQUEST, "Each image must be 5 MB or smaller");
        }
        String fallbackName = mediaType.substring(mediaType.indexOf('/') + 1) + "-upload";
        return new ImageAttachment(
                UUID.randomUUID().toString(),
                request.name() == null || request.name().isBlank() ? fallbackName : request.name().trim(),
                mediaType,
                request.dataUrl().trim(),
                bytes.length
        );
    }

    private String buildEffectiveUserMessage(
            String rawUserMessage,
            List<ImageAttachment> imageAttachments,
            String imageContextSummary,
            TaskMemory briefPatch,
            ReplanScopeSelection replanScope,
            TravelPlan existingPlan
    ) {
        if ((rawUserMessage == null || rawUserMessage.isBlank()) && imageAttachments.isEmpty() && briefPatch == null && replanScope == null) {
            throw new AppException(ResponseCode.INVALID_REQUEST, "Either a text message, a structured brief update, a scoped replan, or at least one image is required");
        }
        String baseMessage = rawUserMessage;
        if (baseMessage == null || baseMessage.isBlank()) {
            baseMessage = replanScope != null
                    ? defaultReplanMessage(replanScope)
                    : briefPatch == null
                    ? (containsChinese(imageContextSummary) ? IMAGE_ONLY_MESSAGE_ZH : IMAGE_ONLY_MESSAGE_EN)
                    : renderBriefAsMessage(briefPatch);
        }
        StringBuilder builder = new StringBuilder(baseMessage);
        if (replanScope != null) {
            builder.append("\n\nScoped replan request: ").append(renderReplanScope(replanScope));
            String replanContext = renderReplanContext(existingPlan, replanScope);
            if (replanContext != null && !replanContext.isBlank()) {
                builder.append("\nCurrent scoped context:\n").append(replanContext);
            }
        }
        if (!imageAttachments.isEmpty()) {
            builder.append("\n\nUploaded images: ").append(renderAttachmentNames(imageAttachments));
        }
        if (imageContextSummary != null && !imageContextSummary.isBlank()) {
            builder.append("\n\nImage context extracted from uploaded travel images:\n").append(imageContextSummary);
        }
        return builder.toString().trim();
    }

    private String storedUserMessage(String rawUserMessage, String effectiveUserMessage, List<ImageAttachment> imageAttachments) {
        if (rawUserMessage != null && !rawUserMessage.isBlank()) {
            return rawUserMessage;
        }
        if (imageAttachments.isEmpty()) {
            return effectiveUserMessage;
        }
        return containsChinese(effectiveUserMessage) ? IMAGE_UPLOAD_NOTE_ZH : IMAGE_UPLOAD_NOTE_EN;
    }

    private String defaultReplanMessage(ReplanScopeSelection replanScope) {
        return switch (replanScope.scope()) {
            case "HOTEL_AREA" -> "Refresh only the hotel area and stay recommendations. Keep the daily itinerary unchanged.";
            case "DAY" -> "Refresh only Day " + replanScope.dayNumber() + " and keep the other days stable.";
            default -> "Refresh the plan.";
        };
    }

    private String renderReplanScope(ReplanScopeSelection replanScope) {
        return switch (replanScope.scope()) {
            case "HOTEL_AREA" -> "Hotel area only";
            case "DAY" -> "Day " + replanScope.dayNumber() + " only";
            default -> replanScope.scope();
        };
    }

    private String renderReplanContext(TravelPlan existingPlan, ReplanScopeSelection replanScope) {
        if (existingPlan == null || replanScope == null) {
            return null;
        }
        if ("HOTEL_AREA".equals(replanScope.scope())) {
            String currentHotel = existingPlan.hotels().isEmpty() ? existingPlan.hotelArea() : existingPlan.hotels().getFirst().name();
            return "Current hotel area: " + existingPlan.hotelArea() + "\nCurrent stay anchor: " + currentHotel;
        }
        if ("DAY".equals(replanScope.scope())) {
            return existingPlan.days().stream()
                    .filter(day -> replanScope.dayNumber().equals(day.dayNumber()))
                    .findFirst()
                    .map(day -> {
                        String stops = day.stops().stream().map(stop -> stop.name()).limit(4).collect(Collectors.joining(", "));
                        return "Current Day " + day.dayNumber() + ": " + day.theme() + "\nCurrent stops: " + stops;
                    })
                    .orElse(null);
        }
        return null;
    }

    private String buildConfirmedUserMessage(String rawUserMessage, String summary) {
        if (rawUserMessage != null && !rawUserMessage.isBlank()) {
            return rawUserMessage + "\n\nConfirmed extracted image facts:\n" + summary;
        }
        return containsChinese(summary) ? IMAGE_CONFIRM_MESSAGE_ZH + "\n\n" + summary : IMAGE_CONFIRM_MESSAGE_EN + "\n\n" + summary;
    }

    private Map<String, Object> userMessageMetadata(
            List<ImageAttachment> imageAttachments,
            String imageContextSummary,
            ConversationImageFacts imageFacts,
            String imageContextStatus,
            TaskMemory briefPatch
    ) {
        if (imageAttachments.isEmpty()
                && (imageContextSummary == null || imageContextSummary.isBlank())
                && (imageFacts == null || !imageFacts.hasRecognizedFacts())
                && (imageContextStatus == null || imageContextStatus.isBlank())
                && briefPatch == null) {
            return Map.of();
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (imageContextStatus != null && !imageContextStatus.isBlank()) {
            metadata.put("imageContextStatus", imageContextStatus);
        }
        if (!imageAttachments.isEmpty()) {
            metadata.put("imageAttachments", imageAttachments.stream().map(ImageAttachment::metadata).toList());
            metadata.put("imageAttachmentCount", imageAttachments.size());
        }
        if (imageContextSummary != null && !imageContextSummary.isBlank()) {
            metadata.put("imageContextSummary", imageContextSummary);
        }
        if (imageFacts != null) {
            metadata.put("imageFacts", imageFacts);
        }
        if (briefPatch != null) {
            metadata.put("tripBrief", Map.of(
                    "origin", briefPatch.origin(),
                    "destination", briefPatch.destination(),
                    "startDate", briefPatch.startDate(),
                    "endDate", briefPatch.endDate(),
                    "days", briefPatch.days(),
                    "travelers", briefPatch.travelers(),
                    "budget", briefPatch.budget(),
                    "preferences", briefPatch.preferences()
            ));
        }
        return metadata;
    }

    private ConversationImageFacts emptyImageFacts() {
        return new ConversationImageFacts(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }

    private TaskMemory taskMemoryPatchFromFacts(String conversationId, ConversationImageFacts facts) {
        List<String> preferences = new java.util.ArrayList<>();
        if (facts.hotelName() != null && !facts.hotelName().isBlank()) {
            preferences.add("Hotel: " + facts.hotelName());
        }
        if (facts.hotelArea() != null && !facts.hotelArea().isBlank()) {
            preferences.add("Hotel area: " + facts.hotelArea());
        }
        preferences.addAll(facts.activities());
        String summary = buildImageFactsSummary(facts);
        return new TaskMemory(
                conversationId,
                facts.origin(),
                facts.destination(),
                facts.startDate(),
                facts.endDate(),
                facts.days(),
                null,
                facts.budget(),
                preferences,
                null,
                summary,
                Instant.now()
        );
    }

    private TaskMemory taskMemoryPatchFromBrief(String requestedConversationId, TripBriefRequest brief) {
        if (brief == null) {
            return null;
        }
        if ((brief.origin() == null || brief.origin().isBlank())
                && (brief.destination() == null || brief.destination().isBlank())
                && (brief.startDate() == null || brief.startDate().isBlank())
                && (brief.endDate() == null || brief.endDate().isBlank())
                && brief.days() == null
                && (brief.travelers() == null || brief.travelers().isBlank())
                && (brief.budget() == null || brief.budget().isBlank())
                && brief.preferences().isEmpty()) {
            return null;
        }
        String conversationId = requestedConversationId == null || requestedConversationId.isBlank()
                ? UUID.randomUUID().toString()
                : requestedConversationId.trim();
        return new TaskMemory(
                conversationId,
                normalizeText(brief.origin()),
                normalizeText(brief.destination()),
                normalizeText(brief.startDate()),
                normalizeText(brief.endDate()),
                brief.days(),
                normalizeText(brief.travelers()),
                normalizeText(brief.budget()),
                brief.preferences().stream().map(this::normalizeText).filter(java.util.Objects::nonNull).toList(),
                null,
                null,
                Instant.now()
        );
    }

    private String renderBriefAsMessage(TaskMemory briefPatch) {
        List<String> parts = new java.util.ArrayList<>();
        if (briefPatch.destination() != null) {
            parts.add("Destination: " + briefPatch.destination());
        }
        if (briefPatch.origin() != null) {
            parts.add("Origin: " + briefPatch.origin());
        }
        if (briefPatch.startDate() != null) {
            parts.add("Start Date: " + briefPatch.startDate());
        }
        if (briefPatch.endDate() != null) {
            parts.add("End Date: " + briefPatch.endDate());
        }
        if (briefPatch.days() != null) {
            parts.add("Days: " + briefPatch.days());
        }
        if (briefPatch.travelers() != null) {
            parts.add("Travelers: " + briefPatch.travelers());
        }
        if (briefPatch.budget() != null) {
            parts.add("Budget: " + briefPatch.budget());
        }
        if (!briefPatch.preferences().isEmpty()) {
            parts.add("Preferences: " + String.join(", ", briefPatch.preferences()));
        }
        return parts.isEmpty() ? IMAGE_ONLY_MESSAGE_EN : String.join(" | ", parts);
    }

    private String buildImageFactsSummary(ConversationImageFacts facts) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (facts.origin() != null && !facts.origin().isBlank()) {
            lines.add("Origin: " + facts.origin());
        }
        if (facts.destination() != null && !facts.destination().isBlank()) {
            lines.add("Destination: " + facts.destination());
        }
        if (facts.startDate() != null && !facts.startDate().isBlank()) {
            lines.add("Start Date: " + facts.startDate());
        }
        if (facts.endDate() != null && !facts.endDate().isBlank()) {
            lines.add("End Date: " + facts.endDate());
        }
        if (facts.days() != null) {
            lines.add("Days: " + facts.days());
        }
        if (facts.budget() != null && !facts.budget().isBlank()) {
            lines.add("Budget: " + facts.budget());
        }
        if (facts.hotelName() != null && !facts.hotelName().isBlank()) {
            lines.add("Hotel: " + facts.hotelName());
        }
        if (facts.hotelArea() != null && !facts.hotelArea().isBlank()) {
            lines.add("Hotel Area: " + facts.hotelArea());
        }
        if (!facts.activities().isEmpty()) {
            lines.add("Activities: " + String.join(", ", facts.activities()));
        }
        return lines.isEmpty() ? null : String.join(" | ", lines);
    }

    private List<ConversationMessage> enrichMessagesForPlanning(List<ConversationMessage> messages) {
        return messages.stream()
                .map(message -> {
                    String enrichedContent = enrichMessageContent(message);
                    if (enrichedContent.equals(message.content())) {
                        return message;
                    }
                    return new ConversationMessage(
                            message.id(),
                            message.conversationId(),
                            message.role(),
                            enrichedContent,
                            message.agentType(),
                            message.createdAt(),
                            message.metadata()
                    );
                })
                .toList();
    }

    private String enrichMessageContent(ConversationMessage message) {
        if (message.metadata().isEmpty()) {
            return message.content();
        }
        String imageContextStatus = metadataText(message.metadata().get("imageContextStatus"));
        if (IMAGE_CONTEXT_STATUS_PENDING.equals(imageContextStatus) || IMAGE_CONTEXT_STATUS_DISMISSED.equals(imageContextStatus)) {
            return message.content();
        }
        String attachmentNames = attachmentNames(message.metadata().get("imageAttachments"));
        String imageContextSummary = metadataText(message.metadata().get("imageContextSummary"));
        boolean confirmedImageContext = IMAGE_CONTEXT_STATUS_CONFIRMED.equals(imageContextStatus);
        if (!confirmedImageContext && attachmentNames != null && !attachmentNames.isBlank()) {
            return message.content();
        }
        if ((attachmentNames == null || attachmentNames.isBlank()) && (imageContextSummary == null || imageContextSummary.isBlank())) {
            return message.content();
        }
        StringBuilder builder = new StringBuilder(message.content());
        if (confirmedImageContext && attachmentNames != null && !attachmentNames.isBlank()) {
            builder.append("\n[Attached images: ").append(attachmentNames).append(']');
        }
        if (imageContextSummary != null && !imageContextSummary.isBlank()) {
            builder.append("\n[Extracted image context]\n").append(imageContextSummary);
        }
        return builder.toString();
    }

    private String attachmentNames(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(entry -> String.valueOf(entry.getOrDefault("name", "")))
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(", "));
    }

    private String metadataText(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        return text.isBlank() ? null : text;
    }

    private String renderAttachmentNames(List<ImageAttachment> imageAttachments) {
        return imageAttachments.stream().map(ImageAttachment::name).collect(Collectors.joining(", "));
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
            Map<String, Object> metadata,
            TravelPlan travelPlan
    ) {
    }

    private record ReplanScopeSelection(
            String scope,
            Integer dayNumber
    ) {
    }

    private enum ImageContextAction {
        NONE,
        CONFIRM,
        DISMISS
    }
}

