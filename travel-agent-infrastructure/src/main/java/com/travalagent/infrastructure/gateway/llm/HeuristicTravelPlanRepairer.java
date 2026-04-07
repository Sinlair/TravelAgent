package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.model.entity.TravelBudgetItem;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class HeuristicTravelPlanRepairer {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public TravelPlan repair(TravelPlan plan, TravelPlanValidationResult validationResult, AgentExecutionContext context) {
        if (plan == null) {
            return null;
        }

        List<TravelPlanDay> days = plan.days();
        Set<String> repairCodes = new LinkedHashSet<>(validationResult.repairCodes());
        if (repairCodes.contains("dedupe")) {
            days = dedupeStops(days);
        }
        if (repairCodes.contains("opening-hours")) {
            days = fixOpeningHours(days);
        }
        if (repairCodes.contains("pace")) {
            days = trimBusiestDay(days, true, 420);
        }
        if (repairCodes.contains("transit-load")) {
            days = trimBusiestDay(days, false, 150);
        }
        if (repairCodes.contains("budget")) {
            days = trimMostExpensiveDay(days);
        }

        List<TravelBudgetItem> budgetItems = repairCodes.contains("budget")
                ? tightenHotelBudget(plan.budget(), containsChinese(context.userMessage()))
                : plan.budget();

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
                budgetItems,
                plan.checks(),
                days,
                Instant.now()
        );
    }

    private List<TravelPlanDay> dedupeStops(List<TravelPlanDay> days) {
        Set<String> seen = new LinkedHashSet<>();
        List<TravelPlanDay> results = new ArrayList<>();
        for (TravelPlanDay day : days) {
            List<TravelPlanStop> filtered = new ArrayList<>();
            for (TravelPlanStop stop : day.stops()) {
                String normalized = normalize(stop.name());
                if (!normalized.isBlank() && seen.contains(normalized) && day.stops().size() > 1) {
                    continue;
                }
                if (!normalized.isBlank()) {
                    seen.add(normalized);
                }
                filtered.add(resetRouting(stop, filtered.isEmpty()));
            }
            results.add(rebuildDay(day, filtered.isEmpty() ? day.stops() : filtered));
        }
        return List.copyOf(results);
    }

    private List<TravelPlanDay> fixOpeningHours(List<TravelPlanDay> days) {
        List<TravelPlanDay> results = new ArrayList<>();
        for (TravelPlanDay day : days) {
            List<TravelPlanStop> repairedStops = new ArrayList<>();
            for (TravelPlanStop stop : day.stops()) {
                TravelPlanStop repaired = repairOpeningWindow(stop, repairedStops.isEmpty());
                if (repaired != null) {
                    repairedStops.add(repaired);
                }
            }
            results.add(rebuildDay(day, repairedStops.isEmpty() ? day.stops() : repairedStops));
        }
        return List.copyOf(results);
    }

    private List<TravelPlanDay> trimBusiestDay(List<TravelPlanDay> days, boolean activityMode, int limit) {
        List<TravelPlanDay> working = new ArrayList<>(days);
        while (true) {
            int index = busiestDayIndex(working, activityMode);
            if (index < 0) {
                return List.copyOf(working);
            }
            TravelPlanDay day = working.get(index);
            int metric = activityMode ? safe(day.totalActivityMinutes()) : safe(day.totalTransitMinutes());
            if (metric <= limit || day.stops().size() <= 1) {
                return List.copyOf(working);
            }
            List<TravelPlanStop> trimmedStops = new ArrayList<>(day.stops().subList(0, day.stops().size() - 1));
            List<TravelPlanStop> sanitizedStops = new ArrayList<>();
            for (TravelPlanStop stop : trimmedStops) {
                sanitizedStops.add(resetRouting(stop, sanitizedStops.isEmpty()));
            }
            working.set(index, rebuildDay(day, sanitizedStops));
        }
    }

    private List<TravelPlanDay> trimMostExpensiveDay(List<TravelPlanDay> days) {
        if (days.isEmpty()) {
            return List.of();
        }
        List<TravelPlanDay> working = new ArrayList<>(days);
        int index = working.stream()
                .filter(day -> day.stops().size() > 1)
                .max(Comparator.comparingInt(day -> safe(day.estimatedCost())))
                .map(working::indexOf)
                .orElse(-1);
        if (index < 0) {
            return List.copyOf(working);
        }
        TravelPlanDay day = working.get(index);
        List<TravelPlanStop> trimmedStops = new ArrayList<>(day.stops().subList(0, day.stops().size() - 1));
        List<TravelPlanStop> sanitizedStops = new ArrayList<>();
        for (TravelPlanStop stop : trimmedStops) {
            sanitizedStops.add(resetRouting(stop, sanitizedStops.isEmpty()));
        }
        working.set(index, rebuildDay(day, sanitizedStops));
        return List.copyOf(working);
    }

    private int busiestDayIndex(List<TravelPlanDay> days, boolean activityMode) {
        int bestIndex = -1;
        int bestValue = -1;
        for (int index = 0; index < days.size(); index++) {
            TravelPlanDay day = days.get(index);
            int value = activityMode ? safe(day.totalActivityMinutes()) : safe(day.totalTransitMinutes());
            if (value > bestValue) {
                bestValue = value;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private TravelPlanStop repairOpeningWindow(TravelPlanStop stop, boolean firstStop) {
        LocalTime start = parseTime(stop.startTime(), LocalTime.of(9, 0));
        LocalTime end = parseTime(stop.endTime(), start.plusMinutes(Math.max(60, safe(stop.durationMinutes()))));
        LocalTime open = parseTime(stop.openTime(), LocalTime.MIN);
        LocalTime close = parseTime(stop.closeTime(), LocalTime.MAX.minusMinutes(1));

        LocalTime adjustedStart = start.isBefore(open) ? open : start;
        LocalTime adjustedEnd = end.isAfter(close) ? close : end;
        if (!adjustedStart.isBefore(adjustedEnd)) {
            return null;
        }

        int duration = Math.max((int) Duration.between(adjustedStart, adjustedEnd).toMinutes(), 0);
        if (duration < 30) {
            return null;
        }

        return new TravelPlanStop(
                stop.slot(),
                stop.name(),
                stop.area(),
                stop.address(),
                stop.longitude(),
                stop.latitude(),
                formatTime(adjustedStart),
                formatTime(adjustedEnd),
                duration,
                firstStop ? safe(stop.transitMinutesFromPrevious()) : safe(stop.transitMinutesFromPrevious()),
                stop.estimatedCost(),
                stop.openTime(),
                stop.closeTime(),
                stop.rationale(),
                stop.costBreakdown(),
                stop.poiMatch(),
                null
        );
    }

    private TravelPlanStop resetRouting(TravelPlanStop stop, boolean firstStop) {
        return new TravelPlanStop(
                stop.slot(),
                stop.name(),
                stop.area(),
                stop.address(),
                stop.longitude(),
                stop.latitude(),
                stop.startTime(),
                stop.endTime(),
                stop.durationMinutes(),
                firstStop ? safe(stop.transitMinutesFromPrevious()) : safe(stop.transitMinutesFromPrevious()),
                stop.estimatedCost(),
                stop.openTime(),
                stop.closeTime(),
                stop.rationale(),
                stop.costBreakdown(),
                stop.poiMatch(),
                null
        );
    }

    private TravelPlanDay rebuildDay(TravelPlanDay original, List<TravelPlanStop> stops) {
        int totalActivity = stops.stream().mapToInt(stop -> safe(stop.durationMinutes())).sum();
        int totalTransit = stops.stream().mapToInt(stop -> safe(stop.transitMinutesFromPrevious())).sum();
        int totalCost = stops.stream().mapToInt(stop -> safe(stop.estimatedCost())).sum();
        String startTime = stops.isEmpty() ? defaultTime(original.startTime(), "09:00") : defaultTime(stops.get(0).startTime(), defaultTime(original.startTime(), "09:00"));
        String endTime = stops.isEmpty() ? defaultTime(original.endTime(), "20:00") : defaultTime(stops.get(stops.size() - 1).endTime(), defaultTime(original.endTime(), "20:00"));
        return new TravelPlanDay(
                original.dayNumber(),
                original.theme(),
                startTime,
                endTime,
                totalTransit,
                totalActivity,
                totalCost,
                List.copyOf(stops),
                null
        );
    }

    private List<TravelBudgetItem> tightenHotelBudget(List<TravelBudgetItem> items, boolean chinese) {
        List<TravelBudgetItem> repaired = new ArrayList<>();
        for (TravelBudgetItem item : items) {
            if (!"Hotel".equals(item.category())) {
                repaired.add(item);
                continue;
            }
            int minAmount = Math.max(0, (int) Math.floor(safe(item.minAmount()) * 0.9));
            int maxAmount = Math.max(minAmount, (int) Math.floor(safe(item.maxAmount()) * 0.85));
            repaired.add(new TravelBudgetItem(
                    item.category(),
                    minAmount,
                    maxAmount,
                    chinese ? "已在修正阶段主动下调酒店档次，优先保住总预算。" : "Lowered the hotel tier during repair to bring the plan back toward budget."
            ));
        }
        return List.copyOf(repaired);
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

    private String formatTime(LocalTime value) {
        return value.format(TIME_FORMATTER);
    }

    private String defaultTime(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsChinese(String value) {
        return value != null && value.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }
}