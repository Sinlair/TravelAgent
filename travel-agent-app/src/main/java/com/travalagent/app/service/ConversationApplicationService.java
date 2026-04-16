package com.travalagent.app.service;

import com.travalagent.app.dto.ChatRequest;
import com.travalagent.app.dto.ChatResponse;
import com.travalagent.app.dto.ConversationChecklistUpdateRequest;
import com.travalagent.app.dto.ConversationDetailResponse;
import com.travalagent.app.dto.ConversationFeedbackRequest;
import com.travalagent.app.dto.FeedbackBreakdownItem;
import com.travalagent.app.dto.FeedbackDatasetRecord;
import com.travalagent.app.dto.FeedbackLoopFinding;
import com.travalagent.app.dto.FeedbackLoopSummaryResponse;
import com.travalagent.app.dto.TravelPlanVersionDiffResponse;
import com.travalagent.domain.model.entity.ConversationFeedback;
import com.travalagent.domain.model.entity.ConversationSession;
import com.travalagent.domain.model.entity.TaskMemory;
import com.travalagent.domain.model.entity.TravelChecklistItem;
import com.travalagent.domain.model.entity.TravelPlan;
import com.travalagent.domain.model.entity.TravelPlanDay;
import com.travalagent.domain.model.entity.TravelPlanVersionSnapshot;
import com.travalagent.domain.model.valobj.AgentType;
import com.travalagent.domain.repository.ConversationRepository;
import com.travalagent.infrastructure.gateway.tool.AmapMcpGateway;
import com.travalagent.types.enums.ResponseCode;
import com.travalagent.types.exception.AppException;
import io.micrometer.observation.annotation.Observed;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@Observed(name = "travel.agent.conversation.service")
public class ConversationApplicationService {

    private final ConversationWorkflow conversationWorkflow;
    private final ConversationRepository conversationRepository;
    private final AmapMcpGateway amapMcpGateway;

    public ConversationApplicationService(
            ConversationWorkflow conversationWorkflow,
            ConversationRepository conversationRepository,
            AmapMcpGateway amapMcpGateway
    ) {
        this.conversationWorkflow = conversationWorkflow;
        this.conversationRepository = conversationRepository;
        this.amapMcpGateway = amapMcpGateway;
    }

    public ChatResponse chat(ChatRequest request) {
        return conversationWorkflow.execute(request);
    }

    @Observed(name = "travel.agent.list-conversations")
    public List<ConversationSession> listConversations() {
        return conversationRepository.listConversations();
    }

    @Observed(name = "travel.agent.conversation-detail")
    public ConversationDetailResponse conversationDetail(String conversationId) {
        var session = conversationRepository.findConversation(conversationId).orElseThrow();
        var messages = conversationRepository.findMessages(conversationId);
        var timeline = conversationRepository.findTimeline(conversationId);
        var taskMemory = conversationRepository.findTaskMemory(conversationId).orElse(TaskMemory.empty(conversationId));
        var travelPlan = conversationRepository.findTravelPlan(conversationId).orElse(null);
        var feedback = conversationRepository.findFeedback(conversationId).orElse(null);
        var imageContextCandidate = conversationRepository.findPendingImageContext(conversationId).orElse(null);
        var recentVersions = conversationRepository.listTravelPlanVersions(conversationId, 2);
        var currentPlanVersion = recentVersions.isEmpty() ? null : recentVersions.getFirst().createdAt().toString();
        var resultMetadata = ConversationResultSupport.extractResultMetadata(messages);
        return new ConversationDetailResponse(
                session,
                messages,
                timeline,
                taskMemory,
                travelPlan,
                feedback,
                imageContextCandidate,
                ConversationResultSupport.buildFeedbackTarget(
                        conversationId,
                        latestAssistantMessageId(messages),
                        session.lastAgent(),
                        travelPlan,
                        currentPlanVersion
                ),
                ConversationResultSupport.buildIssues(
                        taskMemory,
                        imageContextCandidate,
                        ConversationResultSupport.buildMissingInformation(taskMemory, imageContextCandidate),
                        ConversationResultSupport.buildConstraintSummary(session.lastAgent(), travelPlan, resultMetadata)
                ),
                ConversationResultSupport.buildMissingInformation(taskMemory, imageContextCandidate),
                ConversationResultSupport.buildConstraintSummary(session.lastAgent(), travelPlan, resultMetadata),
                buildRecentVersionDiff(recentVersions)
        );
    }

    @Observed(name = "travel.agent.update-checklist")
    public TravelPlan updateChecklist(String conversationId, ConversationChecklistUpdateRequest request) {
        if (request.itemKey() == null || request.itemKey().isBlank()) {
            throw new AppException(ResponseCode.INVALID_REQUEST, "Checklist item key is required");
        }
        TravelPlan travelPlan = conversationRepository.findTravelPlan(conversationId)
                .orElseThrow(() -> new AppException(ResponseCode.INVALID_REQUEST, "Conversation plan not found"));
        boolean matched = travelPlan.checklist().stream().anyMatch(item -> item.key().equals(request.itemKey().trim()));
        if (!matched) {
            throw new AppException(ResponseCode.INVALID_REQUEST, "Checklist item not found");
        }
        List<TravelChecklistItem> updatedChecklist = travelPlan.checklist().stream()
                .map(item -> item.key().equals(request.itemKey().trim())
                        ? new TravelChecklistItem(item.key(), item.title(), item.details(), request.confirmed())
                        : item)
                .toList();
        TravelPlan updatedPlan = travelPlan.withExecutionContext(updatedChecklist, travelPlan.refreshedSections(), Instant.now());
        conversationRepository.saveTravelPlan(updatedPlan);
        return updatedPlan;
    }

    @Observed(name = "travel.agent.save-feedback")
    public ConversationFeedback saveFeedback(String conversationId, ConversationFeedbackRequest request) {
        var session = conversationRepository.findConversation(conversationId)
                .orElseThrow(() -> new AppException(ResponseCode.INVALID_REQUEST, "Conversation not found"));
        var taskMemory = conversationRepository.findTaskMemory(conversationId).orElse(TaskMemory.empty(conversationId));
        var travelPlan = conversationRepository.findTravelPlan(conversationId).orElse(null);
        String label = normalizeLabel(request.label());

        ConversationFeedback existing = conversationRepository.findFeedback(conversationId).orElse(null);
        Instant createdAt = existing == null ? Instant.now() : existing.createdAt();
        String targetScope = normalizeTargetScope(request.targetScope(), travelPlan != null);
        List<String> reasonLabels = normalizeReasonLabels(request.reasonLabels(), request.reasonCode());
        String reasonCode = reasonLabels.isEmpty() ? normalizeOptional(request.reasonCode()) : reasonLabels.getFirst();
        Map<String, Object> feedbackMetadata = new LinkedHashMap<>(feedbackMetadata(session.lastAgent(), taskMemory, travelPlan));
        if (request.targetId() != null && !request.targetId().isBlank()) {
            feedbackMetadata.put("targetId", request.targetId().trim());
        }
        feedbackMetadata.put("targetScope", targetScope);
        if (request.planVersion() != null && !request.planVersion().isBlank()) {
            feedbackMetadata.put("planVersion", request.planVersion().trim());
        }
        if (!reasonLabels.isEmpty()) {
            feedbackMetadata.put("reasonLabels", reasonLabels);
        }
        ConversationFeedback feedback = new ConversationFeedback(
                conversationId,
                label,
                normalizeOptional(request.targetId()),
                targetScope,
                normalizeOptional(request.planVersion()),
                reasonLabels,
                reasonCode,
                normalizeOptional(request.note()),
                session.lastAgent(),
                taskMemory.destination(),
                taskMemory.days(),
                taskMemory.budget(),
                travelPlan != null,
                feedbackMetadata,
                createdAt,
                Instant.now()
        );
        conversationRepository.saveFeedback(feedback);
        return feedback;
    }

    @Observed(name = "travel.agent.export-feedback-dataset")
    public List<FeedbackDatasetRecord> exportFeedbackDataset(
            int limit,
            String destination,
            String agentType,
            String targetScope,
            String reasonLabel,
            String planVersionFrom,
            String planVersionTo
    ) {
        int normalizedLimit = normalizeLimit(limit);
        return filterFeedback(
                conversationRepository.listFeedback(1000),
                destination,
                agentType,
                targetScope,
                reasonLabel,
                planVersionFrom,
                planVersionTo
        ).stream()
                .limit(normalizedLimit)
                .map(feedback -> {
                    String conversationId = feedback.conversationId();
                    var conversation = conversationRepository.findConversation(conversationId).orElseThrow();
                    var taskMemory = conversationRepository.findTaskMemory(conversationId).orElse(TaskMemory.empty(conversationId));
                    var travelPlan = conversationRepository.findTravelPlan(conversationId).orElse(null);
                    var messages = conversationRepository.findMessages(conversationId);
                    return new FeedbackDatasetRecord(
                            conversation,
                            feedback,
                            taskMemory,
                            travelPlan,
                            messages
                    );
                })
                .toList();
    }

    @Observed(name = "travel.agent.feedback-loop-summary")
    public FeedbackLoopSummaryResponse feedbackLoopSummary(
            int limit,
            String destination,
            String agentType,
            String targetScope,
            String reasonLabel,
            String planVersionFrom,
            String planVersionTo
    ) {
        int normalizedLimit = normalizeLimit(limit);
        List<ConversationFeedback> feedbacks = filterFeedback(
                conversationRepository.listFeedback(1000),
                destination,
                agentType,
                targetScope,
                reasonLabel,
                planVersionFrom,
                planVersionTo
        ).stream().limit(normalizedLimit).toList();
        long acceptedCount = feedbacks.stream().filter(this::isAccepted).count();
        long partialCount = feedbacks.stream().filter(this::isPartial).count();
        long rejectedCount = feedbacks.stream().filter(this::isRejected).count();
        long structuredPlanCount = feedbacks.stream().filter(ConversationFeedback::hasTravelPlan).count();

        return new FeedbackLoopSummaryResponse(
                Instant.now(),
                normalizedLimit,
                feedbacks.size(),
                acceptedCount,
                partialCount,
                rejectedCount,
                percentage(acceptedCount, feedbacks.size()),
                percentage(acceptedCount + partialCount, feedbacks.size()),
                structuredPlanCount,
                percentage(structuredPlanCount, feedbacks.size()),
                buildBreakdown(feedbacks, feedback -> normalizeBucket(feedback.reasonCode(), "UNSPECIFIED"), 5),
                buildBreakdown(feedbacks, feedback -> normalizeBucket(feedback.destination(), "UNKNOWN"), 5),
                buildBreakdown(feedbacks, feedback -> feedback.agentType() == null ? "UNKNOWN" : feedback.agentType().name(), 5),
                buildFindings(feedbacks)
        );
    }

    @Observed(name = "travel.agent.delete-conversation")
    public void deleteConversation(String conversationId) {
        conversationRepository.deleteConversation(conversationId);
        amapMcpGateway.clearConversationCache(conversationId);
    }

    private String normalizeLabel(String raw) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ACCEPTED", "PARTIAL", "REJECTED" -> normalized;
            default -> throw new AppException(ResponseCode.INVALID_REQUEST, "Feedback label must be ACCEPTED, PARTIAL, or REJECTED");
        };
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<String> normalizeReasonLabels(List<String> requestReasonLabels, String requestReasonCode) {
        List<String> normalized = new ArrayList<>();
        if (requestReasonLabels != null) {
            normalized.addAll(requestReasonLabels.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .distinct()
                    .toList());
        }
        String normalizedReasonCode = normalizeOptional(requestReasonCode);
        if (normalizedReasonCode != null) {
            normalized.add(normalizedReasonCode.toLowerCase(Locale.ROOT));
        }
        return normalized.stream().distinct().toList();
    }

    private String normalizeTargetScope(String rawScope, boolean hasTravelPlan) {
        String normalized = rawScope == null || rawScope.isBlank()
                ? (hasTravelPlan ? "OVERALL" : "ANSWER")
                : rawScope.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ANSWER", "PLAN", "OVERALL" -> normalized;
            default -> throw new AppException(ResponseCode.INVALID_REQUEST, "targetScope must be ANSWER, PLAN, or OVERALL");
        };
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 200;
        }
        return Math.min(limit, 1000);
    }

    private List<FeedbackBreakdownItem> buildBreakdown(
            List<ConversationFeedback> feedbacks,
            Function<ConversationFeedback, String> classifier,
            int maxItems
    ) {
        return feedbacks.stream()
                .collect(Collectors.groupingBy(classifier, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> toBreakdown(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(FeedbackBreakdownItem::totalCount).reversed()
                        .thenComparing(FeedbackBreakdownItem::key))
                .limit(maxItems)
                .toList();
    }

    private FeedbackBreakdownItem toBreakdown(String key, List<ConversationFeedback> feedbacks) {
        long acceptedCount = feedbacks.stream().filter(this::isAccepted).count();
        long partialCount = feedbacks.stream().filter(this::isPartial).count();
        long rejectedCount = feedbacks.stream().filter(this::isRejected).count();
        return new FeedbackBreakdownItem(
                key,
                feedbacks.size(),
                acceptedCount,
                partialCount,
                rejectedCount,
                percentage(acceptedCount, feedbacks.size()),
                percentage(acceptedCount + partialCount, feedbacks.size())
        );
    }

    private List<FeedbackLoopFinding> buildFindings(List<ConversationFeedback> feedbacks) {
        List<FeedbackLoopFinding> findings = new ArrayList<>();
        addFinding(
                findings,
                "TRAVEL_PLAN_COVERAGE",
                "no_structured_plan",
                feedbacks,
                feedback -> !feedback.hasTravelPlan(),
                "Inspect planner fallbacks and capture why the flow failed to return a structured plan."
        );
        addFinding(
                findings,
                "VALIDATION_FAIL",
                "validationFailCount>0",
                feedbacks,
                feedback -> metadataLong(feedback, "validationFailCount") > 0,
                "Review failing constraint checks first; these plans are disproportionately likely to be rejected."
        );
        addFinding(
                findings,
                "HIGH_WARNING_LOAD",
                "validationWarnCount>=2",
                feedbacks,
                feedback -> metadataLong(feedback, "validationWarnCount") >= 2,
                "Tighten repair prompts when the planner returns multiple warnings instead of letting them accumulate."
        );
        addFinding(
                findings,
                "CONSTRAINT_RELAXATION",
                "constraintRelaxed=true",
                feedbacks,
                feedback -> metadataBoolean(feedback, "constraintRelaxed"),
                "Track which constraints were relaxed and decide whether routing or repair should be adjusted earlier."
        );
        addFinding(
                findings,
                "LOW_KNOWLEDGE_COVERAGE",
                "knowledgeHintCount=0",
                feedbacks,
                feedback -> feedback.hasTravelPlan() && metadataLong(feedback, "knowledgeHintCount") == 0,
                "Check whether retrieval missed city-specific hints before the planner generated the itinerary."
        );
        findings.addAll(buildNegativeReasonFindings(feedbacks));

        return findings.stream()
                .sorted(Comparator.comparingLong(FeedbackLoopFinding::rejectedCount).reversed()
                        .thenComparing(Comparator.comparingLong(FeedbackLoopFinding::partialCount).reversed())
                        .thenComparing(Comparator.comparingLong(FeedbackLoopFinding::totalCount).reversed())
                        .thenComparing(FeedbackLoopFinding::type)
                        .thenComparing(FeedbackLoopFinding::key))
                .limit(5)
                .toList();
    }

    private void addFinding(
            List<FeedbackLoopFinding> findings,
            String type,
            String key,
            List<ConversationFeedback> feedbacks,
            Predicate<ConversationFeedback> predicate,
            String recommendation
    ) {
        List<ConversationFeedback> matches = feedbacks.stream().filter(predicate).toList();
        if (!matches.isEmpty()) {
            findings.add(toFinding(type, key, matches, recommendation));
        }
    }

    private List<FeedbackLoopFinding> buildNegativeReasonFindings(List<ConversationFeedback> feedbacks) {
        return buildBreakdown(feedbacks.stream().filter(this::isNegative).toList(),
                feedback -> normalizeBucket(feedback.reasonCode(), "UNSPECIFIED"),
                2).stream()
                .map(item -> new FeedbackLoopFinding(
                        "REASON_CODE",
                        item.key(),
                        item.totalCount(),
                        item.acceptedCount(),
                        item.partialCount(),
                        item.rejectedCount(),
                        item.usableRatePct(),
                        recommendationForReasonCode(item.key())
                ))
                .toList();
    }

    private FeedbackLoopFinding toFinding(String type, String key, List<ConversationFeedback> feedbacks, String recommendation) {
        long acceptedCount = feedbacks.stream().filter(this::isAccepted).count();
        long partialCount = feedbacks.stream().filter(this::isPartial).count();
        long rejectedCount = feedbacks.stream().filter(this::isRejected).count();
        return new FeedbackLoopFinding(
                type,
                key,
                feedbacks.size(),
                acceptedCount,
                partialCount,
                rejectedCount,
                percentage(acceptedCount + partialCount, feedbacks.size()),
                recommendation
        );
    }

    private boolean metadataBoolean(ConversationFeedback feedback, String key) {
        Object value = feedback.metadata().get(key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return false;
    }

    private long metadataLong(ConversationFeedback feedback, String key) {
        Object value = feedback.metadata().get(key);
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Long.parseLong(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String normalizeBucket(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String recommendationForReasonCode(String reasonCode) {
        return switch (reasonCode) {
            case "edited_before_use" -> "Compare partial accepts against accepted plans to see which fields users edit most often.";
            case "not_useful" -> "Inspect rejected conversations and route, POI, or budget assumptions before changing prompts globally.";
            case "UNSPECIFIED" -> "Ask for a short analyst note on negative feedback so future batches are easier to diagnose.";
            default -> "Review this reason bucket directly and decide whether routing, retrieval, validation, or repair should change.";
        };
    }

    private boolean isAccepted(ConversationFeedback feedback) {
        return "ACCEPTED".equals(feedback.label());
    }

    private boolean isPartial(ConversationFeedback feedback) {
        return "PARTIAL".equals(feedback.label());
    }

    private boolean isRejected(ConversationFeedback feedback) {
        return "REJECTED".equals(feedback.label());
    }

    private boolean isNegative(ConversationFeedback feedback) {
        return !isAccepted(feedback);
    }

    private double percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return Math.round((numerator * 10000.0) / denominator) / 100.0;
    }

    private Map<String, Object> feedbackMetadata(AgentType agentType, TaskMemory taskMemory, TravelPlan travelPlan) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("agentType", agentType == null ? null : agentType.name());
        metadata.put("origin", taskMemory.origin());
        metadata.put("destination", taskMemory.destination());
        metadata.put("startDate", taskMemory.startDate());
        metadata.put("endDate", taskMemory.endDate());
        metadata.put("days", taskMemory.days());
        metadata.put("travelers", taskMemory.travelers());
        metadata.put("budget", taskMemory.budget());
        metadata.put("preferences", taskMemory.preferences());
        if (travelPlan != null) {
            metadata.put("estimatedTotalMin", travelPlan.estimatedTotalMin());
            metadata.put("estimatedTotalMax", travelPlan.estimatedTotalMax());
            metadata.put("hotelArea", travelPlan.hotelArea());
            metadata.put("constraintRelaxed", travelPlan.constraintRelaxed());
            metadata.put("adjustmentSuggestionCount", travelPlan.adjustmentSuggestions().size());
            metadata.put("knowledgeHintCount", travelPlan.knowledgeRetrieval() == null ? 0 : travelPlan.knowledgeRetrieval().selections().size());
            metadata.put("dayCount", travelPlan.days().size());
            metadata.put("validationFailCount", travelPlan.checks().stream().filter(check -> check.status().name().equals("FAIL")).count());
            metadata.put("validationWarnCount", travelPlan.checks().stream().filter(check -> check.status().name().equals("WARN")).count());
        }
        return metadata;
    }

    private String latestAssistantMessageId(List<com.travalagent.domain.model.entity.ConversationMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            var message = messages.get(i);
            if (message.role() == com.travalagent.domain.model.valobj.MessageRole.ASSISTANT) {
                return message.id();
            }
        }
        return null;
    }

    private List<ConversationFeedback> filterFeedback(
            List<ConversationFeedback> feedbacks,
            String destination,
            String agentType,
            String targetScope,
            String reasonLabel,
            String planVersionFrom,
            String planVersionTo
    ) {
        return feedbacks.stream()
                .filter(feedback -> matchesText(feedback.destination(), destination))
                .filter(feedback -> agentType == null || agentType.isBlank()
                        || (feedback.agentType() != null && feedback.agentType().name().equalsIgnoreCase(agentType.trim())))
                .filter(feedback -> targetScope == null || targetScope.isBlank()
                        || (feedback.targetScope() != null && feedback.targetScope().equalsIgnoreCase(targetScope.trim())))
                .filter(feedback -> {
                    if (reasonLabel == null || reasonLabel.isBlank()) {
                        return true;
                    }
                    String normalized = reasonLabel.trim().toLowerCase(Locale.ROOT);
                    return feedback.reasonLabels().stream().anyMatch(label -> label.equalsIgnoreCase(normalized))
                            || (feedback.reasonCode() != null && feedback.reasonCode().equalsIgnoreCase(normalized));
                })
                .filter(feedback -> withinVersionWindow(feedback.planVersion(), planVersionFrom, planVersionTo))
                .toList();
    }

    private boolean matchesText(String source, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return source != null && source.toLowerCase(Locale.ROOT).contains(filter.trim().toLowerCase(Locale.ROOT));
    }

    private boolean withinVersionWindow(String planVersion, String from, String to) {
        if ((from == null || from.isBlank()) && (to == null || to.isBlank())) {
            return true;
        }
        if (planVersion == null || planVersion.isBlank()) {
            return false;
        }
        Instant value = parseInstant(planVersion);
        Instant fromValue = parseInstant(from);
        Instant toValue = parseInstant(to);
        if (value == null) {
            return false;
        }
        if (fromValue != null && value.isBefore(fromValue)) {
            return false;
        }
        return toValue == null || !value.isAfter(toValue);
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private TravelPlanVersionDiffResponse buildRecentVersionDiff(List<TravelPlanVersionSnapshot> recentVersions) {
        if (recentVersions == null || recentVersions.size() < 2) {
            return null;
        }
        TravelPlanVersionSnapshot latest = recentVersions.get(0);
        TravelPlanVersionSnapshot previous = recentVersions.get(1);
        return new TravelPlanVersionDiffResponse(
                latest.versionId(),
                previous.versionId(),
                latest.createdAt().toString(),
                previous.createdAt().toString(),
                describeDateChange(previous.travelPlan(), latest.travelPlan()),
                describeHotelChange(previous.travelPlan(), latest.travelPlan()),
                describeBudgetChange(previous.travelPlan(), latest.travelPlan()),
                describeStopChanges(previous.travelPlan(), latest.travelPlan())
        );
    }

    private String describeDateChange(TravelPlan previous, TravelPlan latest) {
        String previousWindow = dateWindow(previous);
        String latestWindow = dateWindow(latest);
        if (previousWindow.equals(latestWindow)) {
            return "Dates unchanged";
        }
        return previousWindow + " -> " + latestWindow;
    }

    private String describeHotelChange(TravelPlan previous, TravelPlan latest) {
        String previousHotel = previous.hotelArea() == null ? "Unknown" : previous.hotelArea();
        String latestHotel = latest.hotelArea() == null ? "Unknown" : latest.hotelArea();
        if (previousHotel.equals(latestHotel)) {
            return "Hotel area unchanged";
        }
        return previousHotel + " -> " + latestHotel;
    }

    private String describeBudgetChange(TravelPlan previous, TravelPlan latest) {
        String previousBudget = budgetWindow(previous);
        String latestBudget = budgetWindow(latest);
        if (previousBudget.equals(latestBudget)) {
            return "Budget range unchanged";
        }
        return previousBudget + " -> " + latestBudget;
    }

    private List<String> describeStopChanges(TravelPlan previous, TravelPlan latest) {
        List<String> changes = new ArrayList<>();
        int maxDays = Math.max(previous.days().size(), latest.days().size());
        for (int i = 0; i < maxDays; i++) {
            TravelPlanDay previousDay = i < previous.days().size() ? previous.days().get(i) : null;
            TravelPlanDay latestDay = i < latest.days().size() ? latest.days().get(i) : null;
            String previousStops = previousDay == null ? "" : previousDay.stops().stream().map(stop -> stop.name()).limit(3).collect(Collectors.joining(", "));
            String latestStops = latestDay == null ? "" : latestDay.stops().stream().map(stop -> stop.name()).limit(3).collect(Collectors.joining(", "));
            if (!previousStops.equals(latestStops)) {
                String label = "Day " + (latestDay != null ? latestDay.dayNumber() : previousDay.dayNumber());
                changes.add(label + ": " + normalizeDiffText(previousStops) + " -> " + normalizeDiffText(latestStops));
            }
        }
        return changes.stream().limit(4).toList();
    }

    private String dateWindow(TravelPlan plan) {
        if (plan.days().isEmpty()) {
            return "No dated window";
        }
        String start = plan.days().getFirst().date();
        String end = plan.days().getLast().date();
        if (start == null || start.isBlank()) {
            return "Estimate mode";
        }
        if (end == null || end.isBlank() || start.equals(end)) {
            return start;
        }
        return start + " to " + end;
    }

    private String budgetWindow(TravelPlan plan) {
        return plan.estimatedTotalMin() + "-" + plan.estimatedTotalMax();
    }

    private String normalizeDiffText(String value) {
        return value == null || value.isBlank() ? "No key stops" : value;
    }
}
