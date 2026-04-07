package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.model.entity.ConstraintCheckStatus;
import com.travalagent.domain.model.entity.TravelBudgetItem;
import com.travalagent.domain.model.entity.TravelConstraintCheck;
import com.travalagent.domain.model.entity.TravelCostBreakdown;
import com.travalagent.domain.model.entity.TravelHotelRecommendation;
import com.travalagent.domain.model.entity.TravelPlan;
import com.travalagent.domain.model.entity.TravelPlanDay;
import com.travalagent.domain.model.entity.TravelPlanStop;
import com.travalagent.domain.model.valobj.AgentExecutionContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class HeuristicTravelPlanValidator {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public TravelPlanValidationResult validate(TravelPlan plan, AgentExecutionContext context) {
        if (plan == null) {
            return new TravelPlanValidationResult(null, List.of("plan-missing"), false, 1, 0);
        }

        boolean chinese = containsChinese(context.userMessage()) || containsChinese(plan.title()) || containsChinese(context.taskMemory().destination());
        List<TravelPlanDay> normalizedDays = plan.days().stream()
                .map(this::normalizeDay)
                .toList();
        BudgetSnapshot budgetSnapshot = computeBudget(plan, normalizedDays, chinese);
        List<TravelConstraintCheck> checks = computeChecks(plan, normalizedDays, budgetSnapshot, context, chinese);
        TravelPlan normalizedPlan = new TravelPlan(
                plan.conversationId(),
                plan.title(),
                plan.summary(),
                plan.hotelArea(),
                plan.hotelAreaReason(),
                plan.hotels(),
                plan.totalBudget(),
                budgetSnapshot.totalMin(),
                budgetSnapshot.totalMax(),
                plan.highlights(),
                budgetSnapshot.items(),
                checks,
                normalizedDays,
                Instant.now()
        );

        List<String> repairCodes = determineRepairCodes(checks);
        int failCount = (int) checks.stream().filter(check -> check.status() == ConstraintCheckStatus.FAIL).count();
        int warningCount = (int) checks.stream().filter(check -> check.status() == ConstraintCheckStatus.WARN).count();
        boolean accepted = failCount == 0 && repairCodes.isEmpty();
        return new TravelPlanValidationResult(normalizedPlan, repairCodes, accepted, failCount, warningCount);
    }

    private List<String> determineRepairCodes(List<TravelConstraintCheck> checks) {
        List<String> repairCodes = new ArrayList<>();
        for (TravelConstraintCheck check : checks) {
            switch (check.code()) {
                case "budget", "transit-load", "pace" -> {
                    if (check.status() != ConstraintCheckStatus.PASS) {
                        repairCodes.add(check.code());
                    }
                }
                case "opening-hours", "dedupe" -> {
                    if (check.status() == ConstraintCheckStatus.FAIL) {
                        repairCodes.add(check.code());
                    }
                }
                default -> {
                }
            }
        }
        return List.copyOf(repairCodes);
    }

    private List<TravelConstraintCheck> computeChecks(
            TravelPlan plan,
            List<TravelPlanDay> days,
            BudgetSnapshot budgetSnapshot,
            AgentExecutionContext context,
            boolean chinese
    ) {
        Integer totalBudget = plan.totalBudget();
        int maxTransit = days.stream().mapToInt(day -> safe(day.totalTransitMinutes())).max().orElse(0);
        int maxActivity = days.stream().mapToInt(day -> safe(day.totalActivityMinutes())).max().orElse(0);
        boolean openingOk = days.stream().flatMap(day -> day.stops().stream()).allMatch(this::fitsOpeningWindow);
        Set<String> duplicates = duplicateStops(days);
        boolean relaxedPace = context.taskMemory().preferences().stream().map(this::normalize).anyMatch(value -> value.contains("relaxed"))
                || normalize(context.userMessage()).contains("relaxed")
                || normalize(context.userMessage()).contains("slow pace")
                || containsAny(context.userMessage(), "轻松", "慢节奏", "悠闲", "别太赶");

        List<TravelConstraintCheck> checks = new ArrayList<>();
        checks.add(new TravelConstraintCheck(
                "budget",
                totalBudget != null && budgetSnapshot.totalMax() > totalBudget ? ConstraintCheckStatus.WARN : ConstraintCheckStatus.PASS,
                totalBudget != null && budgetSnapshot.totalMax() > totalBudget
                        ? text(chinese,
                        "当前方案的预计上限为 " + budgetSnapshot.totalMax() + "，已超过你的预算 " + totalBudget + "。",
                        "The estimated upper bound is " + budgetSnapshot.totalMax() + " CNY, which exceeds the stated budget of " + totalBudget + " CNY.")
                        : text(chinese,
                        "当前方案的预计花费仍在预算范围内。",
                        "The estimated spend stays inside the stated budget.")
        ));
        checks.add(new TravelConstraintCheck(
                "opening-hours",
                openingOk ? ConstraintCheckStatus.PASS : ConstraintCheckStatus.FAIL,
                openingOk
                        ? text(chinese, "所有景点时段都在开放时间内。", "Every stop still fits its opening window.")
                        : text(chinese, "至少有一个景点的开始或结束时间超出了开放时段。", "At least one stop starts or ends outside its opening window.")
        ));
        checks.add(new TravelConstraintCheck(
                "transit-load",
                maxTransit > 210 ? ConstraintCheckStatus.FAIL : (maxTransit > 150 ? ConstraintCheckStatus.WARN : ConstraintCheckStatus.PASS),
                maxTransit > 210
                        ? text(chinese, "某一天的通勤时间超过 210 分钟，当前排程过重。", "One day exceeds 210 minutes of transit and is too heavy.")
                        : maxTransit > 150
                        ? text(chinese, "某一天的通勤时间超过 150 分钟，建议继续收缩跨区移动。", "One day exceeds 150 minutes of transit and should be tightened.")
                        : text(chinese, "每日通勤强度保持在可控范围内。", "Daily transit load remains manageable.")
        ));
        checks.add(new TravelConstraintCheck(
                "pace",
                relaxedPace && maxActivity > 420 ? ConstraintCheckStatus.WARN : ConstraintCheckStatus.PASS,
                relaxedPace && maxActivity > 420
                        ? text(chinese, "你要求轻松节奏，但某一天活动时长仍超过 420 分钟。", "You asked for a relaxed pace, but one day still exceeds 420 minutes of activity.")
                        : text(chinese, "整体节奏与当前需求基本匹配。", "The overall pace matches the current request.")
        ));
        checks.add(new TravelConstraintCheck(
                "dedupe",
                duplicates.isEmpty() ? ConstraintCheckStatus.PASS : ConstraintCheckStatus.FAIL,
                duplicates.isEmpty()
                        ? text(chinese, "行程中没有重复景点。", "The itinerary does not repeat the same attraction.")
                        : text(chinese, "检测到重复景点：" + String.join("、", duplicates) + "。", "Duplicate attractions were detected: " + String.join(", ", duplicates) + ".")
        ));
        return List.copyOf(checks);
    }

    private BudgetSnapshot computeBudget(TravelPlan plan, List<TravelPlanDay> days, boolean chinese) {
        int dayCount = Math.max(days.size(), 1);
        int nights = Math.max(dayCount - 1, 1);

        TravelHotelRecommendation primaryHotel = plan.hotels().isEmpty() ? null : plan.hotels().get(0);
        int hotelMin = primaryHotel != null && primaryHotel.nightlyMin() != null
                ? primaryHotel.nightlyMin() * nights
                : fallbackBudget(plan, "Hotel", 320 * nights);
        int hotelMax = primaryHotel != null && primaryHotel.nightlyMax() != null
                ? primaryHotel.nightlyMax() * nights
                : fallbackBudget(plan, "Hotel", 520 * nights);

        int intercityMin = existingBudgetItem(plan, "Intercity transport") != null ? safe(existingBudgetItem(plan, "Intercity transport").minAmount()) : 0;
        int intercityMax = existingBudgetItem(plan, "Intercity transport") != null ? safe(existingBudgetItem(plan, "Intercity transport").maxAmount()) : 0;

        int localTransitActual = days.stream()
                .mapToInt(day -> day.stops().stream().mapToInt(stop -> stop.costBreakdown() == null ? 0 : safe(stop.costBreakdown().localTransitCost())).sum()
                        + (day.returnToHotel() == null ? 0 : safe(day.returnToHotel().estimatedCost())))
                .sum();
        int foodActual = days.stream()
                .flatMap(day -> day.stops().stream())
                .map(TravelPlanStop::costBreakdown)
                .mapToInt(breakdown -> breakdown == null ? 0 : safe(breakdown.foodCost()))
                .sum();
        int attractionActual = days.stream()
                .flatMap(day -> day.stops().stream())
                .mapToInt(this::attractionCost)
                .sum();

        int localTransitMin = localTransitActual;
        int localTransitMax = localTransitActual + Math.max(40, 20 * dayCount);
        int foodMin = foodActual;
        int foodMax = foodActual + (60 * dayCount);
        int attractionMin = attractionActual;
        int attractionMax = attractionActual + 80;

        List<TravelBudgetItem> items = List.of(
                new TravelBudgetItem("Hotel", hotelMin, hotelMax, text(chinese, "按修正后的酒店建议估算。", "Estimated from the repaired primary hotel recommendation.")),
                new TravelBudgetItem("Intercity transport", intercityMin, intercityMax, text(chinese, "沿用当前跨城交通预算。", "Retains the current intercity transport allowance.")),
                new TravelBudgetItem("Local transit", localTransitMin, localTransitMax, text(chinese, "基于当前路线的实际市内通勤成本，并保留少量缓冲。", "Derived from actual in-city routing plus a small buffer.")),
                new TravelBudgetItem("Food", foodMin, foodMax, text(chinese, "基于当前停靠点的餐饮安排，并保留少量机动。", "Derived from the current meal stops plus a small buffer.")),
                new TravelBudgetItem("Attractions and buffer", attractionMin, attractionMax, text(chinese, "基于景点门票与体验成本重算。", "Recomputed from attraction tickets and experience costs."))
        );

        int totalMin = items.stream().mapToInt(item -> safe(item.minAmount())).sum();
        int totalMax = items.stream().mapToInt(item -> safe(item.maxAmount())).sum();
        return new BudgetSnapshot(items, totalMin, totalMax);
    }

    private TravelPlanDay normalizeDay(TravelPlanDay day) {
        List<TravelPlanStop> stops = day.stops() == null ? List.of() : day.stops();
        int totalActivity = stops.stream().mapToInt(stop -> safe(stop.durationMinutes())).sum();
        int totalTransit = stops.stream().mapToInt(stop -> safe(stop.transitMinutesFromPrevious())).sum()
                + (day.returnToHotel() == null ? 0 : safe(day.returnToHotel().durationMinutes()));
        int totalCost = stops.stream().mapToInt(stop -> safe(stop.estimatedCost())
                + (stop.routeFromPrevious() == null ? 0 : safe(stop.routeFromPrevious().estimatedCost()))).sum()
                + (day.returnToHotel() == null ? 0 : safe(day.returnToHotel().estimatedCost()));

        String startTime = stops.isEmpty() ? defaultTime(day.startTime(), "09:00") : defaultTime(stops.get(0).startTime(), defaultTime(day.startTime(), "09:00"));
        String endTime = stops.isEmpty() ? defaultTime(day.endTime(), "20:00") : defaultTime(stops.get(stops.size() - 1).endTime(), defaultTime(day.endTime(), "20:00"));
        return new TravelPlanDay(
                day.dayNumber(),
                day.theme(),
                startTime,
                endTime,
                totalTransit,
                totalActivity,
                totalCost,
                stops,
                day.returnToHotel()
        );
    }

    private boolean fitsOpeningWindow(TravelPlanStop stop) {
        LocalTime start = parseTime(stop.startTime(), LocalTime.of(9, 0));
        LocalTime end = parseTime(stop.endTime(), start);
        LocalTime open = parseTime(stop.openTime(), LocalTime.MIN);
        LocalTime close = parseTime(stop.closeTime(), LocalTime.MAX.minusMinutes(1));
        return !start.isBefore(open) && !end.isAfter(close) && start.isBefore(end);
    }

    private Set<String> duplicateStops(List<TravelPlanDay> days) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (TravelPlanDay day : days) {
            for (TravelPlanStop stop : day.stops()) {
                String normalized = normalize(stop.name());
                if (normalized.isBlank()) {
                    continue;
                }
                if (!seen.add(normalized)) {
                    duplicates.add(stop.name());
                }
            }
        }
        return duplicates;
    }

    private int attractionCost(TravelPlanStop stop) {
        TravelCostBreakdown breakdown = stop.costBreakdown();
        if (breakdown == null) {
            return safe(stop.estimatedCost());
        }
        return safe(breakdown.ticketCost()) + safe(breakdown.otherCost());
    }

    private TravelBudgetItem existingBudgetItem(TravelPlan plan, String category) {
        return plan.budget().stream()
                .filter(item -> category.equals(item.category()))
                .findFirst()
                .orElse(null);
    }

    private int fallbackBudget(TravelPlan plan, String category, int fallback) {
        TravelBudgetItem item = existingBudgetItem(plan, category);
        if (item == null) {
            return fallback;
        }
        return Math.max(fallback, safe(item.maxAmount()));
    }

    private String text(boolean chinese, String chineseText, String englishText) {
        return chinese ? chineseText : englishText;
    }

    private String defaultTime(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private LocalTime parseTime(String value, LocalTime fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsChinese(String value) {
        return value != null && value.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    private boolean containsAny(String value, String... terms) {
        if (value == null) {
            return false;
        }
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private record BudgetSnapshot(
            List<TravelBudgetItem> items,
            int totalMin,
            int totalMax
    ) {
    }
}