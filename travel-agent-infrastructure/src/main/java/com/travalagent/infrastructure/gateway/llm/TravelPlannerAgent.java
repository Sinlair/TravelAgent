package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.event.TimelinePublisher;
import com.travalagent.domain.gateway.AmapGateway;
import com.travalagent.domain.model.entity.TimelineEvent;
import com.travalagent.domain.model.entity.TravelBudgetItem;
import com.travalagent.domain.model.entity.TravelPlan;
import com.travalagent.domain.model.valobj.AgentExecutionContext;
import com.travalagent.domain.model.valobj.AgentExecutionResult;
import com.travalagent.domain.model.valobj.AgentType;
import com.travalagent.domain.model.valobj.ExecutionStage;
import com.travalagent.domain.model.valobj.TimelineEventStatus;
import com.travalagent.domain.model.valobj.TravelKnowledgeRetrievalResult;
import com.travalagent.domain.model.valobj.TravelKnowledgeSelection;
import com.travalagent.domain.model.valobj.WeatherSnapshot;
import com.travalagent.domain.repository.TravelKnowledgeRepository;
import com.travalagent.domain.service.SpecialistAgent;
import com.travalagent.domain.service.TravelPlanBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class TravelPlannerAgent implements SpecialistAgent {

    private static final int MAX_REPAIR_ATTEMPTS = 2;
    private static final int MAX_RELAXED_ATTEMPTS = 4;
    private static final int KNOWLEDGE_LIMIT = 5;

    private final TravelPlanBuilder travelPlanBuilder;
    private final AmapTravelPlanEnricher amapTravelPlanEnricher;
    private final HeuristicTravelPlanValidator travelPlanValidator;
    private final HeuristicTravelPlanRepairer travelPlanRepairer;
    private final TravelKnowledgeRepository travelKnowledgeRepository;
    private final AmapGateway amapGateway;
    private final TimelinePublisher timelinePublisher;

    public TravelPlannerAgent(
            TravelPlanBuilder travelPlanBuilder,
            AmapTravelPlanEnricher amapTravelPlanEnricher,
            HeuristicTravelPlanValidator travelPlanValidator,
            HeuristicTravelPlanRepairer travelPlanRepairer,
            TravelKnowledgeRepository travelKnowledgeRepository,
            AmapGateway amapGateway,
            TimelinePublisher timelinePublisher
    ) {
        this.travelPlanBuilder = travelPlanBuilder;
        this.amapTravelPlanEnricher = amapTravelPlanEnricher;
        this.travelPlanValidator = travelPlanValidator;
        this.travelPlanRepairer = travelPlanRepairer;
        this.travelKnowledgeRepository = travelKnowledgeRepository;
        this.amapGateway = amapGateway;
        this.timelinePublisher = timelinePublisher;
    }

    @Override
    public AgentType supports() {
        return AgentType.TRAVEL_PLANNER;
    }

    @Override
    public AgentExecutionResult execute(AgentExecutionContext context) {
        TravelPlan draftPlan = travelPlanBuilder.build(context);
        TravelPlan enrichedPlan = enrichPlan(draftPlan, context);
        TravelPlanValidationResult initialValidation = validatePlan(enrichedPlan, context, 0);

        RepairCycleResult strictCycle = repairUntilAccepted(initialValidation, context, MAX_REPAIR_ATTEMPTS, 0, false);
        if (strictCycle.validationResult().accepted()) {
            return acceptedResult(context, strictCycle.validationResult(), strictCycle.repairAttempts(), false, List.of());
        }

        RepairCycleResult relaxedCycle = repairUntilAccepted(strictCycle.validationResult(), context, MAX_RELAXED_ATTEMPTS, strictCycle.repairAttempts(), true);
        List<String> adjustmentSuggestions = buildAdjustmentSuggestions(strictCycle.validationResult(), relaxedCycle.validationResult(), context);
        if (relaxedCycle.validationResult().accepted()) {
            return acceptedResult(
                    context,
                    relaxedCycle.validationResult(),
                    strictCycle.repairAttempts() + relaxedCycle.repairAttempts(),
                    true,
                    adjustmentSuggestions
            );
        }

        return new AgentExecutionResult(
                supports(),
                rejectionAnswer(strictCycle.validationResult(), relaxedCycle.validationResult(), adjustmentSuggestions, context),
                metadata(
                        context,
                        relaxedCycle.validationResult(),
                        strictCycle.repairAttempts() + relaxedCycle.repairAttempts(),
                        true,
                        false,
                        adjustmentSuggestions,
                        0,
                        false
                ),
                null
        );
    }

    private AgentExecutionResult acceptedResult(
            AgentExecutionContext context,
            TravelPlanValidationResult validationResult,
            int repairAttempts,
            boolean constraintRelaxed,
            List<String> adjustmentSuggestions
    ) {
        TravelPlan finalPlan = validationResult.normalizedPlan();
        PlanCompanion companion = buildCompanion(context, finalPlan);
        TravelPlan planWithInsights = finalPlan.withPlannerInsights(
                companion.weather(),
                companion.knowledgeRetrieval(),
                constraintRelaxed,
                adjustmentSuggestions
        );
        String renderedPlan = travelPlanBuilder.render(planWithInsights, context);
        String answer = composeAnswer(renderedPlan, companion, adjustmentSuggestions, constraintRelaxed, planWithInsights, context);
        return new AgentExecutionResult(
                supports(),
                answer,
                metadata(
                        context,
                        validationResult,
                        repairAttempts,
                        false,
                        constraintRelaxed,
                        adjustmentSuggestions,
                        companion.knowledgeRetrieval().selections().size(),
                        companion.weather() != null
                ),
                planWithInsights
        );
    }

    private RepairCycleResult repairUntilAccepted(
            TravelPlanValidationResult initialValidation,
            AgentExecutionContext context,
            int maxAttempts,
            int attemptOffset,
            boolean relaxedMode
    ) {
        TravelPlanValidationResult current = initialValidation;
        int repairAttempts = 0;
        while (!current.accepted() && current.requiresRepair() && repairAttempts < maxAttempts) {
            repairAttempts++;
            publishRepair(context, current, attemptOffset + repairAttempts, relaxedMode);
            TravelPlan repairedPlan = travelPlanRepairer.repair(current.normalizedPlan(), current, context);
            TravelPlan enrichedPlan = enrichPlan(repairedPlan, context);
            current = validatePlan(enrichedPlan, context, attemptOffset + repairAttempts);
        }
        return new RepairCycleResult(current, repairAttempts);
    }

    private TravelPlan enrichPlan(TravelPlan plan, AgentExecutionContext context) {
        timelinePublisher.publish(TimelineEvent.of(
                context.conversationId(),
                ExecutionStage.CALL_TOOL,
                "Resolve itinerary places with Amap",
                Map.of("source", "planner-enrichment")
        ));
        return amapTravelPlanEnricher.enrich(plan, context);
    }

    private TravelPlanValidationResult validatePlan(TravelPlan plan, AgentExecutionContext context, int attempt) {
        TravelPlanValidationResult validationResult = travelPlanValidator.validate(plan, context);
        timelinePublisher.publish(TimelineEvent.of(
                context.conversationId(),
                ExecutionStage.VALIDATE_PLAN,
                validationResult.accepted() ? TimelineEventStatus.COMPLETED : TimelineEventStatus.FAILED,
                "Validate generated plan against budget, opening hours, and load",
                Map.of(
                        "attempt", attempt,
                        "accepted", validationResult.accepted(),
                        "failCount", validationResult.failCount(),
                        "warningCount", validationResult.warningCount(),
                        "repairCodes", validationResult.repairCodes()
                )
        ));
        return validationResult;
    }

    private void publishRepair(AgentExecutionContext context, TravelPlanValidationResult validationResult, int attempt, boolean relaxedMode) {
        timelinePublisher.publish(TimelineEvent.of(
                context.conversationId(),
                ExecutionStage.REPAIR_PLAN,
                TimelineEventStatus.REPAIRED,
                relaxedMode
                        ? "Build closest feasible alternative by relaxing constraints"
                        : "Repair plan against validation findings",
                Map.of(
                        "attempt", attempt,
                        "repairCodes", validationResult.repairCodes()
                )
        ));
    }

    private PlanCompanion buildCompanion(AgentExecutionContext context, TravelPlan plan) {
        String destination = resolveDestination(context, plan);
        TravelKnowledgeRetrievalResult knowledgeRetrieval = retrieveKnowledge(context, destination);
        WeatherSnapshot weather = fetchWeather(context, destination);
        return new PlanCompanion(knowledgeRetrieval, weather);
    }

    private TravelKnowledgeRetrievalResult retrieveKnowledge(AgentExecutionContext context, String destination) {
        if (destination == null || destination.isBlank()) {
            return TravelKnowledgeRetrievalResult.empty(null, List.of(), null);
        }
        timelinePublisher.publish(TimelineEvent.of(
                context.conversationId(),
                ExecutionStage.CALL_TOOL,
                "Retrieve destination knowledge from travel knowledge base",
                Map.of("destination", destination)
        ));
        return travelKnowledgeRepository.retrieve(destination, context.taskMemory().preferences(), context.userMessage(), KNOWLEDGE_LIMIT);
    }

    private WeatherSnapshot fetchWeather(AgentExecutionContext context, String destination) {
        if (destination == null || destination.isBlank()) {
            return null;
        }
        timelinePublisher.publish(TimelineEvent.of(
                context.conversationId(),
                ExecutionStage.CALL_TOOL,
                "Fetch destination weather snapshot",
                Map.of("destination", destination)
        ));
        return amapGateway.weather(destination);
    }

    private String composeAnswer(
            String renderedPlan,
            PlanCompanion companion,
            List<String> adjustmentSuggestions,
            boolean constraintRelaxed,
            TravelPlan feasiblePlan,
            AgentExecutionContext context
    ) {
        StringBuilder builder = new StringBuilder();
        boolean chinese = containsChinese(context.userMessage());

        if (constraintRelaxed) {
            if (chinese) {
                builder.append("原始约束没有完全通过校验。\n\n");
                builder.append("我先给你一版最接近可行的替代方案；如果你接受下面这些调整，这版方案可以直接执行：\n");
            } else {
                builder.append("The original constraints did not fully pass validation.\n\n");
                builder.append("I prepared the closest feasible alternative; if you accept the adjustments below, this version is executable:\n");
            }
            for (String suggestion : adjustmentSuggestions) {
                builder.append("- ").append(suggestion).append('\n');
            }
            if (feasiblePlan.estimatedTotalMax() != null) {
                builder.append('\n');
                if (chinese) {
                    builder.append("建议按至少 ").append(feasiblePlan.estimatedTotalMax()).append(" 元的总预算理解这版替代方案。\n");
                } else {
                    builder.append("Treat this alternative as a plan that needs at least ")
                            .append(feasiblePlan.estimatedTotalMax())
                            .append(" CNY in total budget.\n");
                }
            }
            builder.append('\n');
        }

        appendWeatherSection(builder, companion.weather(), chinese);
        appendKnowledgeSection(builder, companion.knowledgeRetrieval(), chinese);
        builder.append(renderedPlan);
        return builder.toString().trim();
    }

    private void appendWeatherSection(StringBuilder builder, WeatherSnapshot weather, boolean chinese) {
        if (weather == null) {
            return;
        }
        if (chinese) {
            builder.append("## 天气提示\n");
            builder.append("- 城市: ").append(defaultText(weather.city(), "目的地")).append('\n');
            builder.append("- 天气: ").append(defaultText(weather.description(), "未知")).append('\n');
            builder.append("- 温度: ").append(defaultText(weather.temperature(), "-")).append(" C\n");
            builder.append("- 风向/风力: ").append(defaultText(weather.windDirection(), "-")).append(" / ").append(defaultText(weather.windPower(), "-")).append('\n');
            builder.append("- 快照时间: ").append(defaultText(weather.reportTime(), "未提供")).append('\n');
            builder.append("- 说明: 这是当前时点快照，不代表未来多日预报。\n");
        } else {
            builder.append("## Weather Snapshot\n");
            builder.append("- City: ").append(defaultText(weather.city(), "destination")).append('\n');
            builder.append("- Weather: ").append(defaultText(weather.description(), "unknown")).append('\n');
            builder.append("- Temperature: ").append(defaultText(weather.temperature(), "-")).append(" C\n");
            builder.append("- Wind: ").append(defaultText(weather.windDirection(), "-")).append(" / ").append(defaultText(weather.windPower(), "-")).append('\n');
            builder.append("- Snapshot time: ").append(defaultText(weather.reportTime(), "not provided")).append('\n');
            builder.append("- Note: This is a point-in-time snapshot, not a multi-day forecast.\n");
        }
        builder.append('\n');
    }

    private void appendKnowledgeSection(StringBuilder builder, TravelKnowledgeRetrievalResult knowledgeRetrieval, boolean chinese) {
        if (knowledgeRetrieval == null || knowledgeRetrieval.selections().isEmpty()) {
            return;
        }

        if (chinese) {
            List<String> hints = knowledgeRetrieval.selections().stream()
                    .map(this::localizedKnowledgeHint)
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .limit(3)
                    .toList();
            if (hints.isEmpty()) {
                return;
            }
            builder.append("## 本地经验提示\n");
            for (String hint : hints) {
                builder.append("- ").append(hint).append('\n');
            }
            builder.append('\n');
            return;
        }

        builder.append("## Local Knowledge Hints\n");
        for (TravelKnowledgeSelection selection : knowledgeRetrieval.selections()) {
            builder.append("- ")
                    .append(defaultText(selection.title(), "Local hint"))
                    .append(": ")
                    .append(defaultText(selection.content(), ""))
                    .append('\n');
        }
        builder.append('\n');
    }

    private String localizedKnowledgeHint(TravelKnowledgeSelection selection) {
        String title = cleanKnowledgeText(selection == null ? null : selection.title());
        String content = cleanKnowledgeText(selection == null ? null : selection.content());
        String combined = title.isBlank()
                ? content
                : content.isBlank() ? title : title + "： " + content;
        if (!isMostlyChinese(combined)) {
            return "";
        }
        return combined;
    }

    private String cleanKnowledgeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replace("Planning hint:", "")
                .replace("Visit planning hint:", "")
                .replace("Food planning hint:", "")
                .replace("Best used as a stay area because", "")
                .replace("Representative stay option:", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isMostlyChinese(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        long chineseCount = value.codePoints()
                .filter(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                .count();
        long latinCount = value.codePoints()
                .filter(codePoint -> (codePoint >= 'A' && codePoint <= 'Z') || (codePoint >= 'a' && codePoint <= 'z'))
                .count();
        return chineseCount > 0 && chineseCount >= latinCount;
    }

    private Map<String, Object> metadata(
            AgentExecutionContext context,
            TravelPlanValidationResult validationResult,
            int repairAttempts,
            boolean rejected,
            boolean constraintRelaxed,
            List<String> adjustmentSuggestions,
            int knowledgeCount,
            boolean weatherIncluded
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (context.routeReason() != null && !context.routeReason().isBlank()) {
            metadata.put("routeReason", context.routeReason());
        }
        metadata.put("plannerMode", "constraint-driven");
        metadata.put("amapEnriched", true);
        metadata.put("amapProvider", "mcp-tools");
        metadata.put("validated", validationResult.accepted());
        metadata.put("repairAttempts", repairAttempts);
        metadata.put("validationFailures", validationResult.failCount());
        metadata.put("validationWarnings", validationResult.warningCount());
        metadata.put("rejected", rejected);
        metadata.put("constraintRelaxed", constraintRelaxed);
        metadata.put("knowledgeCount", knowledgeCount);
        metadata.put("weatherIncluded", weatherIncluded);
        if (!adjustmentSuggestions.isEmpty()) {
            metadata.put("adjustmentSuggestions", adjustmentSuggestions);
        }
        if (constraintRelaxed && validationResult.normalizedPlan() != null) {
            metadata.put("suggestedBudget", validationResult.normalizedPlan().estimatedTotalMax());
        }
        return metadata;
    }

    private List<String> buildAdjustmentSuggestions(
            TravelPlanValidationResult strictFailure,
            TravelPlanValidationResult latestValidation,
            AgentExecutionContext context
    ) {
        boolean chinese = containsChinese(context.userMessage());
        List<String> suggestions = new ArrayList<>();
        Set<String> issueCodes = new LinkedHashSet<>(strictFailure.repairCodes());
        TravelPlan failedPlan = strictFailure.normalizedPlan();
        TravelPlan latestPlan = latestValidation.normalizedPlan();

        if (issueCodes.contains("budget")) {
            Integer suggestedBudget = latestPlan == null ? null : latestPlan.estimatedTotalMax();
            if (suggestedBudget != null && suggestedBudget > 0) {
                suggestions.add(chinese
                        ? "将总预算至少提高到 " + suggestedBudget + " 元。"
                        : "Increase the total budget to at least " + suggestedBudget + " CNY.");
            } else {
                suggestions.add(chinese
                        ? "提高预算，给酒店和交通留出缓冲。"
                        : "Increase the budget to create room for hotel and transit costs.");
            }
        }

        List<String> removedStops = removedStops(failedPlan, latestPlan);
        if (!removedStops.isEmpty()) {
            suggestions.add(chinese
                    ? "删除这些景点或时段: " + String.join("、", removedStops) + "。"
                    : "Remove these stops or time blocks: " + String.join(", ", removedStops) + ".");
        } else if (issueCodes.contains("transit-load")) {
            suggestions.add(chinese
                    ? "把每天的活动范围控制在 1 到 2 个片区内，减少跨区移动。"
                    : "Keep each day within one or two districts to reduce cross-district transit.");
        }

        if (issueCodes.contains("pace")) {
            suggestions.add(chinese
                    ? "如果想保留更多景点，请接受更紧凑的节奏。"
                    : "If you want to keep more stops, accept a denser pace.");
        }

        TravelBudgetItem failedHotel = budgetItem(failedPlan, "Hotel");
        TravelBudgetItem latestHotel = budgetItem(latestPlan, "Hotel");
        if (failedHotel != null && latestHotel != null && safe(latestHotel.maxAmount()) < safe(failedHotel.maxAmount())) {
            suggestions.add(chinese
                    ? "选择更低档或更偏功能型的酒店。"
                    : "Choose a lower-tier or more functional hotel option.");
        }

        if (suggestions.isEmpty()) {
            suggestions.add(chinese
                    ? "放宽预算、减少必去点，或接受更少的跨区移动。"
                    : "Relax the budget, reduce must-see stops, or accept less cross-district movement.");
        }
        return List.copyOf(suggestions);
    }

    private List<String> removedStops(TravelPlan failedPlan, TravelPlan latestPlan) {
        if (failedPlan == null || latestPlan == null) {
            return List.of();
        }
        List<String> originalStops = stopNames(failedPlan);
        Set<String> relaxedStops = new LinkedHashSet<>(stopNames(latestPlan));
        List<String> removed = new ArrayList<>();
        for (String stop : originalStops) {
            if (!relaxedStops.contains(stop) && !removed.contains(stop)) {
                removed.add(stop);
            }
        }
        return List.copyOf(removed);
    }

    private List<String> stopNames(TravelPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (var day : plan.days()) {
            for (var stop : day.stops()) {
                if (stop.name() != null && !stop.name().isBlank()) {
                    names.add(stop.name());
                }
            }
        }
        return List.copyOf(names);
    }

    private TravelBudgetItem budgetItem(TravelPlan plan, String category) {
        if (plan == null) {
            return null;
        }
        return plan.budget().stream()
                .filter(item -> category.equals(item.category()))
                .findFirst()
                .orElse(null);
    }

    private String rejectionAnswer(
            TravelPlanValidationResult strictFailure,
            TravelPlanValidationResult latestFailure,
            List<String> adjustmentSuggestions,
            AgentExecutionContext context
    ) {
        String issues = latestFailure.normalizedPlan() == null
                ? (containsChinese(context.userMessage()) ? "当前计划缺少可验证的结构化结果。" : "The current plan did not produce a verifiable structured result.")
                : latestFailure.normalizedPlan().checks().stream()
                .filter(check -> check.status().name().equals("WARN") || check.status().name().equals("FAIL"))
                .map(check -> "- [" + check.status() + "] " + check.message())
                .reduce((left, right) -> left + "\n" + right)
                .orElse(containsChinese(context.userMessage()) ? "- 当前没有可展示的失败项。" : "- No detailed findings were captured.");

        StringBuilder builder = new StringBuilder();
        if (containsChinese(context.userMessage())) {
            builder.append("当前约束下，我还不能返回可执行行程，因为自动修正后仍未通过校验。\n\n");
            builder.append("你可以优先尝试这些调整：\n");
        } else {
            builder.append("I still cannot return an executable itinerary because it failed validation even after automated repair.\n\n");
            builder.append("Try these adjustments first:\n");
        }
        for (String suggestion : adjustmentSuggestions) {
            builder.append("- ").append(suggestion).append('\n');
        }
        builder.append('\n');
        if (containsChinese(context.userMessage())) {
            builder.append("当前未通过的校验项：\n");
        } else {
            builder.append("Current validation findings:\n");
        }
        builder.append(issues);
        return builder.toString().trim();
    }

    private String resolveDestination(AgentExecutionContext context, TravelPlan plan) {
        if (context.taskMemory().destination() != null && !context.taskMemory().destination().isBlank()) {
            return context.taskMemory().destination();
        }
        if (plan != null && plan.title() != null && !plan.title().isBlank()) {
            return plan.title();
        }
        return null;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean containsChinese(String value) {
        return value != null && value.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private record RepairCycleResult(
            TravelPlanValidationResult validationResult,
            int repairAttempts
    ) {
    }

    private record PlanCompanion(
            TravelKnowledgeRetrievalResult knowledgeRetrieval,
            WeatherSnapshot weather
    ) {
    }
}
