package com.travalagent.app.service;

import com.travalagent.app.dto.ChatResponseFeedbackTarget;
import com.travalagent.app.dto.ChatResponseIssue;
import com.travalagent.app.dto.ConversationConstraintIssue;
import com.travalagent.app.dto.ConversationConstraintSummary;
import com.travalagent.app.dto.ConversationMissingInformationItem;
import com.travalagent.domain.model.entity.ConversationImageContext;
import com.travalagent.domain.model.entity.ConversationMessage;
import com.travalagent.domain.model.entity.TaskMemory;
import com.travalagent.domain.model.entity.TravelConstraintCheck;
import com.travalagent.domain.model.entity.TravelPlan;
import com.travalagent.domain.model.valobj.AgentType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConversationResultSupport {

    private static final List<String> PLAN_SCOPES = List.of("ANSWER", "PLAN", "OVERALL");
    private static final List<String> DEFAULT_SCOPES = List.of("ANSWER", "OVERALL");

    private ConversationResultSupport() {
    }

    public static ChatResponseFeedbackTarget buildFeedbackTarget(
            String conversationId,
            String assistantMessageId,
            AgentType agentType,
            TravelPlan travelPlan,
            String planVersion
    ) {
        boolean hasTravelPlan = travelPlan != null;
        return new ChatResponseFeedbackTarget(
                assistantMessageId == null || assistantMessageId.isBlank() ? conversationId : assistantMessageId,
                conversationId,
                hasTravelPlan ? "OVERALL" : "ANSWER",
                hasTravelPlan ? planVersion : null,
                agentType,
                hasTravelPlan,
                hasTravelPlan ? PLAN_SCOPES : DEFAULT_SCOPES
        );
    }

    public static List<ConversationMissingInformationItem> buildMissingInformation(
            TaskMemory taskMemory,
            ConversationImageContext imageContextCandidate
    ) {
        Set<String> codes = new LinkedHashSet<>();
        if (taskMemory == null || isBlank(taskMemory.destination())) {
            codes.add("destination");
        }
        if (taskMemory == null || isBlank(taskMemory.startDate())) {
            codes.add("startDate");
        }
        if (taskMemory == null || taskMemory.days() == null) {
            codes.add("days");
        }
        if (taskMemory == null || isBlank(taskMemory.travelers())) {
            codes.add("travelers");
        }
        if (taskMemory == null || isBlank(taskMemory.budget())) {
            codes.add("budget");
        }
        if (taskMemory == null || taskMemory.preferences().isEmpty()) {
            codes.add("preferences");
        }
        if (imageContextCandidate != null && imageContextCandidate.facts() != null) {
            codes.addAll(imageContextCandidate.facts().missingFields());
        }
        return codes.stream()
                .map(ConversationResultSupport::missingItem)
                .toList();
    }

    public static ConversationConstraintSummary buildConstraintSummary(
            AgentType agentType,
            TravelPlan travelPlan,
            Map<String, Object> resultMetadata
    ) {
        if (agentType != AgentType.TRAVEL_PLANNER && travelPlan == null) {
            return ConversationConstraintSummary.none();
        }

        List<ConversationConstraintIssue> issues = new ArrayList<>();
        boolean repaired = false;
        boolean hasRisk = false;
        String status = "PASS";

        if (travelPlan != null) {
            repaired = travelPlan.constraintRelaxed();
            issues.addAll(travelPlan.checks().stream()
                    .filter(check -> check.status() != null)
                    .map(ConversationResultSupport::constraintIssue)
                    .toList());
            hasRisk = travelPlan.checks().stream().anyMatch(check -> "FAIL".equals(check.status().name()));
            if (hasRisk) {
                status = "RISK";
            } else if (repaired) {
                status = "REPAIRED";
            }
        } else {
            Map<String, Object> metadata = resultMetadata == null ? Map.of() : resultMetadata;
            repaired = metadataBoolean(metadata, "constraintRelaxed");
            hasRisk = agentType == AgentType.TRAVEL_PLANNER;
            @SuppressWarnings("unchecked")
            List<String> repairCodes = metadata.get("repairCodes") instanceof List<?> list
                    ? (List<String>) list.stream().map(String::valueOf).toList()
                    : List.of();
            issues.addAll(repairCodes.stream()
                    .map(code -> new ConversationConstraintIssue(code, "HIGH", repairCodeMessage(code)))
                    .toList());
            status = hasRisk ? "RISK" : repaired ? "REPAIRED" : "PASS";
        }

        if (repaired && !"RISK".equals(status)) {
            status = "REPAIRED";
        }
        return new ConversationConstraintSummary(status, repaired, hasRisk, issues);
    }

    public static List<ChatResponseIssue> buildIssues(
            TaskMemory taskMemory,
            ConversationImageContext imageContextCandidate,
            List<ConversationMissingInformationItem> missingInformation,
            ConversationConstraintSummary constraintSummary
    ) {
        List<ChatResponseIssue> issues = new ArrayList<>();
        if (imageContextCandidate != null) {
            issues.add(new ChatResponseIssue(
                    "IMAGE_CONTEXT_CONFIRMATION_REQUIRED",
                    "INFO",
                    "Confirm or dismiss extracted image facts before the next planning pass."
            ));
        }
        if (!missingInformation.isEmpty()) {
            issues.add(new ChatResponseIssue(
                    "CLARIFICATION_REQUIRED",
                    "INFO",
                    clarificationMessage(taskMemory, missingInformation)
            ));
        }
        if (constraintSummary.hasRisk()) {
            issues.add(new ChatResponseIssue(
                    "PLAN_REQUIRES_REVIEW",
                    "WARN",
                    "The current plan still has unresolved validation risk."
            ));
        } else if (constraintSummary.repaired()) {
            issues.add(new ChatResponseIssue(
                    "PLAN_REPAIRED",
                    "INFO",
                    "The current plan passed after repair and includes adjustment tradeoffs."
            ));
        }
        return List.copyOf(issues);
    }

    public static Map<String, Object> assistantMetadata(
            ChatResponseFeedbackTarget feedbackTarget,
            Map<String, Object> resultMetadata
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("feedbackTargetScope", feedbackTarget.scope());
        metadata.put("feedbackAvailableScopes", feedbackTarget.availableScopes());
        if (feedbackTarget.planVersion() != null && !feedbackTarget.planVersion().isBlank()) {
            metadata.put("planVersion", feedbackTarget.planVersion());
        }
        if (resultMetadata != null && !resultMetadata.isEmpty()) {
            metadata.put("resultMetadata", resultMetadata);
        }
        return Map.copyOf(metadata);
    }

    public static Map<String, Object> extractResultMetadata(List<ConversationMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Map.of();
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ConversationMessage message = messages.get(i);
            if (message.metadata().get("resultMetadata") instanceof Map<?, ?> metadata) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                metadata.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                return Map.copyOf(normalized);
            }
        }
        return Map.of();
    }

    private static ConversationMissingInformationItem missingItem(String code) {
        return switch (code) {
            case "origin" -> new ConversationMissingInformationItem("origin", "Origin", "Add the origin city if intercity routing matters.");
            case "destination" -> new ConversationMissingInformationItem("destination", "Destination", "Add the destination city or area.");
            case "startDate" -> new ConversationMissingInformationItem("startDate", "Start Date", "Add the departure or arrival date.");
            case "endDate" -> new ConversationMissingInformationItem("endDate", "End Date", "Add the return date if it is fixed.");
            case "days" -> new ConversationMissingInformationItem("days", "Trip Length", "Add how many days this trip should take.");
            case "travelers" -> new ConversationMissingInformationItem("travelers", "Travelers", "Add who is traveling, such as solo, couple, family, or friends.");
            case "budget" -> new ConversationMissingInformationItem("budget", "Budget", "Add a budget cap or target range.");
            case "hotelName" -> new ConversationMissingInformationItem("hotelName", "Hotel", "Confirm the hotel name if the stay is already booked.");
            case "hotelArea" -> new ConversationMissingInformationItem("hotelArea", "Hotel Area", "Add the preferred hotel area.");
            case "activities", "preferences" -> new ConversationMissingInformationItem("preferences", "Preferences", "Add interests, pace, or must-see priorities.");
            default -> new ConversationMissingInformationItem(code, code, "Add the missing travel detail.");
        };
    }

    private static ConversationConstraintIssue constraintIssue(TravelConstraintCheck check) {
        String severity = switch (check.status()) {
            case FAIL -> "HIGH";
            case WARN -> "MEDIUM";
            case PASS -> "LOW";
        };
        return new ConversationConstraintIssue(check.code(), severity, check.message());
    }

    private static String repairCodeMessage(String code) {
        return switch (code) {
            case "budget" -> "Budget pressure is still blocking a clean pass.";
            case "pace" -> "Daily pacing is still too tight.";
            case "transit-load" -> "Transit load is still too heavy.";
            case "plan-missing" -> "The planner did not produce a stable structured plan.";
            default -> "A planner validation issue still needs review.";
        };
    }

    private static String clarificationMessage(
            TaskMemory taskMemory,
            List<ConversationMissingInformationItem> missingInformation
    ) {
        if (taskMemory != null && taskMemory.pendingQuestion() != null && !taskMemory.pendingQuestion().isBlank()) {
            return taskMemory.pendingQuestion();
        }
        return "Add the remaining trip details: " + missingInformation.stream()
                .map(ConversationMissingInformationItem::label)
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("trip details") + ".";
    }

    private static boolean metadataBoolean(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
