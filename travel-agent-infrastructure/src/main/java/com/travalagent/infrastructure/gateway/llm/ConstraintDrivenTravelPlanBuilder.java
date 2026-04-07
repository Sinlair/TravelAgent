package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.model.entity.ConstraintCheckStatus;
import com.travalagent.domain.model.entity.TravelBudgetItem;
import com.travalagent.domain.model.entity.TravelConstraintCheck;
import com.travalagent.domain.model.entity.TravelCostBreakdown;
import com.travalagent.domain.model.entity.TravelHotelRecommendation;
import com.travalagent.domain.model.entity.TravelPlan;
import com.travalagent.domain.model.entity.TravelPlanDay;
import com.travalagent.domain.model.entity.TravelPlanSlot;
import com.travalagent.domain.model.entity.TravelPlanStop;
import com.travalagent.domain.model.entity.TravelTransitLeg;
import com.travalagent.domain.model.entity.TravelTransitStep;
import com.travalagent.domain.model.valobj.AgentExecutionContext;
import com.travalagent.domain.service.TravelPlanBuilder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ConstraintDrivenTravelPlanBuilder implements TravelPlanBuilder {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern EN_FROM_TO = Pattern.compile("from\\s+([A-Za-z][A-Za-z\\s-]{1,40}?)\\s+to\\s+([A-Za-z][A-Za-z\\s-]{1,40})", Pattern.CASE_INSENSITIVE);
    private static final Pattern EN_DESTINATION = Pattern.compile("(?:plan|arrange|build)?\\s*(?:a\\s+\\d{1,2}\\s*[- ]?day\\s+)?([A-Za-z][A-Za-z\\s-]{1,40})\\s+(?:trip|itinerary|travel)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZH_FROM_TO = Pattern.compile("从([^，。,.]{1,20})到([^，。,.]{1,20})");
    private static final Pattern EN_DAYS = Pattern.compile("(\\d{1,2})\\s*[- ]?day", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZH_DAYS = Pattern.compile("(\\d{1,2})\\s*天");
    private static final Pattern BUDGET = Pattern.compile("(\\d{3,6})");
    private static final Pattern ZH_DAYS_FALLBACK = Pattern.compile("(\\d{1,2}|[一二两三四五六七八九十]{1,3})\\s*天");
    private static final Pattern ZH_DESTINATION = Pattern.compile("([\\u4E00-\\u9FFF]{2,20})(?:行程|旅行|旅游|攻略)");
    private static final Pattern ZH_GO_TO = Pattern.compile("[去到]([\\u4E00-\\u9FFF]{2,20})");
    private static final Map<TravelPlanSlot, LocalTime> SLOT_START = Map.of(
            TravelPlanSlot.MORNING, LocalTime.of(9, 0),
            TravelPlanSlot.AFTERNOON, LocalTime.of(13, 30),
            TravelPlanSlot.EVENING, LocalTime.of(18, 30)
    );

    @Override
    public TravelPlan build(AgentExecutionContext context) {
        boolean preferChinese = preferChinese(context);
        PlanningFacts facts = deriveFacts(context, preferChinese);
        List<StopTemplate> candidates = buildCandidates(facts);
        List<DayTemplate> dayTemplates = initializeDays(facts.days());
        placeStops(dayTemplates, candidates, facts);
        fillDefaults(dayTemplates);

        String hotelAreaKey = recommendHotelArea(dayTemplates, facts);
        List<TravelPlanDay> days = materializeDays(dayTemplates, hotelAreaKey, facts);
        BudgetSummary budget = estimateBudget(days, facts);

        return new TravelPlan(
                context.conversationId(),
                title(facts),
                summary(facts),
                hotelAreaLabel(hotelAreaKey, preferChinese),
                hotelReason(hotelAreaKey, preferChinese),
                List.of(),
                facts.totalBudget(),
                budget.totalMin(),
                budget.totalMax(),
                highlights(candidates, facts),
                budgetItems(budget, preferChinese),
                checks(days, budget, facts),
                days,
                Instant.now()
        );
    }

    @Override
    public String render(TravelPlan plan, AgentExecutionContext context) {
        boolean preferChinese = preferChinese(context);
        StringBuilder builder = new StringBuilder();
        if (preferChinese) {
            builder.append("## 行程总览\n");
            builder.append("- 预计总花费：").append(plan.estimatedTotalMin()).append('-').append(plan.estimatedTotalMax()).append(" 元\n");
            builder.append("- 推荐住宿区域：").append(plan.hotelArea()).append('\n');
            builder.append("- 推荐理由：").append(plan.hotelAreaReason()).append('\n');
            if (!plan.hotels().isEmpty()) {
                builder.append("- 建议优先入住：").append(plan.hotels().get(0).name()).append("，")
                        .append(plan.hotels().get(0).address()).append('\n');
            }
            builder.append("\n## 约束检查\n");
            for (TravelConstraintCheck check : plan.checks()) {
                builder.append("- [").append(check.status()).append("] ").append(check.message()).append('\n');
            }
            builder.append("\n## 预算拆分\n");
            for (TravelBudgetItem item : plan.budget()) {
                builder.append("- ").append(categoryLabel(item.category(), true)).append("：")
                        .append(item.minAmount()).append('-').append(item.maxAmount()).append(" 元。")
                        .append(item.rationale()).append('\n');
            }
            if (!plan.hotels().isEmpty()) {
                builder.append("\n## 酒店建议\n");
                for (TravelHotelRecommendation hotel : plan.hotels()) {
                    builder.append("- ").append(hotel.name()).append("（").append(hotel.area()).append("），")
                            .append(hotel.address()).append("，每晚约 ")
                            .append(hotel.nightlyMin()).append('-').append(hotel.nightlyMax()).append(" 元。")
                            .append(hotel.rationale()).append('\n');
                }
            }
            for (TravelPlanDay day : plan.days()) {
                builder.append("\n## 第 ").append(day.dayNumber()).append(" 天：").append(day.theme()).append('\n');
                builder.append("- 游玩时长：").append(day.totalActivityMinutes()).append(" 分钟\n");
                builder.append("- 通勤时长：").append(day.totalTransitMinutes()).append(" 分钟\n");
                builder.append("- 当日预计花费：").append(day.estimatedCost()).append(" 元\n");
                for (TravelPlanStop stop : day.stops()) {
                    builder.append("- ").append(stop.startTime()).append('-').append(stop.endTime()).append(' ')
                            .append(stop.name()).append("（").append(stop.area()).append('）')
                            .append(stop.address() == null || stop.address().isBlank() ? "" : "，地址：" + stop.address())
                            .append("。现场花费约 ").append(stop.estimatedCost()).append(" 元");
                    if (stop.routeFromPrevious() != null) {
                        builder.append("；到达方式：").append(routeSummary(stop.routeFromPrevious(), true));
                    }
                    builder.append("。").append(stop.rationale()).append('\n');
                    if (stop.costBreakdown() != null) {
                        builder.append("  费用细分：门票 ").append(safe(stop.costBreakdown().ticketCost()))
                                .append(" 元，餐饮 ").append(safe(stop.costBreakdown().foodCost()))
                                .append(" 元，通勤 ").append(safe(stop.costBreakdown().localTransitCost()))
                                .append(" 元，其它 ").append(safe(stop.costBreakdown().otherCost()))
                                .append(" 元。\n");
                    }
                    if (stop.routeFromPrevious() != null) {
                        for (TravelTransitStep step : stop.routeFromPrevious().steps()) {
                            builder.append("  路线步骤：").append(stepLabel(step, true)).append('\n');
                        }
                    }
                }
                if (day.returnToHotel() != null) {
                    builder.append("- 返程回酒店：").append(routeSummary(day.returnToHotel(), true)).append('\n');
                }
            }
            return builder.toString().trim();
        }

        builder.append("## Trip Overview\n");
        builder.append("- Estimated total: ").append(plan.estimatedTotalMin()).append('-').append(plan.estimatedTotalMax()).append(" CNY\n");
        builder.append("- Recommended hotel area: ").append(plan.hotelArea()).append('\n');
        builder.append("- Why: ").append(plan.hotelAreaReason()).append('\n');
        if (!plan.hotels().isEmpty()) {
            builder.append("- Primary hotel pick: ").append(plan.hotels().get(0).name()).append(", ")
                    .append(plan.hotels().get(0).address()).append('\n');
        }
        builder.append("\n## Constraint Checks\n");
        for (TravelConstraintCheck check : plan.checks()) {
            builder.append("- [").append(check.status()).append("] ").append(check.message()).append('\n');
        }
        builder.append("\n## Budget Breakdown\n");
        for (TravelBudgetItem item : plan.budget()) {
            builder.append("- ").append(item.category()).append(": ")
                    .append(item.minAmount()).append('-').append(item.maxAmount()).append(" CNY. ")
                    .append(item.rationale()).append('\n');
        }
        if (!plan.hotels().isEmpty()) {
            builder.append("\n## Hotel Options\n");
            for (TravelHotelRecommendation hotel : plan.hotels()) {
                builder.append("- ").append(hotel.name()).append(" (").append(hotel.area()).append("), ")
                        .append(hotel.address()).append(", ")
                        .append(hotel.nightlyMin()).append('-').append(hotel.nightlyMax()).append(" CNY/night. ")
                        .append(hotel.rationale()).append('\n');
            }
        }
        for (TravelPlanDay day : plan.days()) {
            builder.append("\n## Day ").append(day.dayNumber()).append(": ").append(day.theme()).append('\n');
            builder.append("- Activity load: ").append(day.totalActivityMinutes()).append(" min\n");
            builder.append("- Transit load: ").append(day.totalTransitMinutes()).append(" min\n");
            builder.append("- Estimated day total: ").append(day.estimatedCost()).append(" CNY\n");
            for (TravelPlanStop stop : day.stops()) {
                builder.append("- ").append(stop.startTime()).append('-').append(stop.endTime()).append(' ')
                        .append(stop.name()).append(" (").append(stop.area()).append(')')
                        .append(stop.address() == null || stop.address().isBlank() ? "" : ", address: " + stop.address())
                        .append(". On-site spend about ").append(stop.estimatedCost()).append(" CNY");
                if (stop.routeFromPrevious() != null) {
                    builder.append("; access: ").append(routeSummary(stop.routeFromPrevious(), false));
                }
                builder.append(". ").append(stop.rationale()).append('\n');
                if (stop.costBreakdown() != null) {
                    builder.append("  Cost detail: ticket ").append(safe(stop.costBreakdown().ticketCost()))
                            .append(", food ").append(safe(stop.costBreakdown().foodCost()))
                            .append(", transit ").append(safe(stop.costBreakdown().localTransitCost()))
                            .append(", other ").append(safe(stop.costBreakdown().otherCost())).append(" CNY.\n");
                }
                if (stop.routeFromPrevious() != null) {
                    for (TravelTransitStep step : stop.routeFromPrevious().steps()) {
                        builder.append("  Route step: ").append(stepLabel(step, false)).append('\n');
                    }
                }
            }
            if (day.returnToHotel() != null) {
                builder.append("- Back to hotel: ").append(routeSummary(day.returnToHotel(), false)).append('\n');
            }
        }
        return builder.toString().trim();
    }

    // planning

    private PlanningFacts deriveFacts(AgentExecutionContext context, boolean preferChinese) {
        String message = context.userMessage() == null ? "" : context.userMessage();
        String origin = firstNonBlank(context.taskMemory().origin(), extractOrigin(message));
        String destination = firstNonBlank(context.taskMemory().destination(), extractDestination(message));
        Integer days = firstNonNull(context.taskMemory().days(), extractDays(message), 3);
        Integer totalBudget = firstNonNull(parseBudget(context.taskMemory().budget()), extractBudget(message));

        Set<String> preferences = new LinkedHashSet<>();
        preferences.addAll(context.taskMemory().preferences());
        preferences.addAll(extractPreferences(message));
        if (preferences.isEmpty()) {
            preferences.add(preferChinese ? "核心景点" : "signature sights");
            preferences.add(preferChinese ? "本地美食" : "local food");
        }

        boolean relaxedPace = containsAny(message.toLowerCase(Locale.ROOT), "relaxed", "slow pace", "easy pace", "leisurely")
                || containsAny(message, "轻松", "慢节奏", "悠闲", "不赶", "别太累");

        return new PlanningFacts(
                blankToDefault(origin, preferChinese ? "未说明出发地" : "origin not specified"),
                blankToDefault(destination, preferChinese ? "目的地城市" : "destination city"),
                days,
                totalBudget,
                List.copyOf(preferences),
                relaxedPace,
                preferChinese
        );
    }

    private List<StopTemplate> buildCandidates(PlanningFacts facts) {
        Map<String, StopTemplate> templates = new LinkedHashMap<>();
        for (String preference : facts.preferences()) {
            StopTemplate template = templateFromPreference(preference);
            if (template != null) {
                templates.putIfAbsent(template.key(), template);
            }
        }

        if (isHangzhou(facts.destination())) {
            templates.putIfAbsent("WEST_LAKE", new StopTemplate("WEST_LAKE", TravelPlanSlot.AFTERNOON, 180, 0, 0, 20, "06:00", "22:00", 100, true));
            templates.putIfAbsent("LINGYIN_TEMPLE", new StopTemplate("LINGYIN_TEMPLE", TravelPlanSlot.MORNING, 150, 75, 0, 15, "07:00", "18:00", 96, true));
            templates.putIfAbsent("HEFANG_STREET", new StopTemplate("HEFANG_STREET", TravelPlanSlot.EVENING, 120, 0, 120, 10, "17:00", "22:30", 84, false));
            templates.putIfAbsent("BROKEN_BRIDGE", new StopTemplate("BROKEN_BRIDGE", TravelPlanSlot.MORNING, 90, 0, 0, 0, "06:30", "20:00", 72, false));
            templates.putIfAbsent("LONGJING_VILLAGE", new StopTemplate("LONGJING_VILLAGE", TravelPlanSlot.AFTERNOON, 120, 0, 30, 50, "09:00", "18:00", 68, false));
            templates.putIfAbsent("CITY_FOOD", new StopTemplate("CITY_FOOD", TravelPlanSlot.EVENING, 120, 0, 150, 0, "17:00", "23:00", 60, false));
        }

        templates.putIfAbsent("LOCAL_DINNER", new StopTemplate("LOCAL_DINNER", TravelPlanSlot.EVENING, 90, 0, 120, 0, "17:30", "22:00", 25, false));
        templates.putIfAbsent("FLEXIBLE_BLOCK", new StopTemplate("FLEXIBLE_BLOCK", TravelPlanSlot.AFTERNOON, 90, 0, 40, 30, "09:00", "21:00", 20, false));
        return templates.values().stream()
                .sorted(Comparator.comparingInt(StopTemplate::priority).reversed())
                .toList();
    }

    private List<DayTemplate> initializeDays(int totalDays) {
        List<DayTemplate> days = new ArrayList<>();
        for (int day = 1; day <= totalDays; day++) {
            EnumMap<TravelPlanSlot, StopTemplate> slots = new EnumMap<>(TravelPlanSlot.class);
            if (totalDays == 1) {
                slots.put(TravelPlanSlot.MORNING, null);
                slots.put(TravelPlanSlot.AFTERNOON, null);
                slots.put(TravelPlanSlot.EVENING, null);
            } else if (day == 1) {
                slots.put(TravelPlanSlot.AFTERNOON, null);
                slots.put(TravelPlanSlot.EVENING, null);
            } else if (day == totalDays) {
                slots.put(TravelPlanSlot.MORNING, null);
                slots.put(TravelPlanSlot.AFTERNOON, null);
            } else {
                slots.put(TravelPlanSlot.MORNING, null);
                slots.put(TravelPlanSlot.AFTERNOON, null);
                slots.put(TravelPlanSlot.EVENING, null);
            }
            days.add(new DayTemplate(day, slots));
        }
        return days;
    }

    private void placeStops(List<DayTemplate> days, List<StopTemplate> candidates, PlanningFacts facts) {
        for (StopTemplate candidate : candidates) {
            for (DayTemplate day : days) {
                if (!day.hasSlot(candidate.slot()) || day.get(candidate.slot()) != null) {
                    continue;
                }
                if (facts.relaxedPace() && day.assignedCount() >= (day.dayNumber() == 1 || day.dayNumber() == days.size() ? 2 : 3)) {
                    continue;
                }
                day.assign(candidate.slot(), candidate);
                break;
            }
        }
    }

    private void fillDefaults(List<DayTemplate> days) {
        for (DayTemplate day : days) {
            for (TravelPlanSlot slot : day.slots().keySet()) {
                if (day.get(slot) == null) {
                    day.assign(slot, defaultStop(slot));
                }
            }
        }
    }

    private String recommendHotelArea(List<DayTemplate> days, PlanningFacts facts) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (DayTemplate day : days) {
            for (StopTemplate stop : day.slots().values()) {
                if (stop == null) {
                    continue;
                }
                scores.merge(areaKey(stop.key()), stop.mustSee() ? 3 : 1, Integer::sum);
            }
        }
        if (facts.totalBudget() != null && facts.totalBudget() <= 2200) {
            scores.merge("TRANSIT_HUB", 2, Integer::sum);
        }
        return scores.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("CITY_CENTER");
    }

    private List<TravelPlanDay> materializeDays(List<DayTemplate> dayTemplates, String hotelAreaKey, PlanningFacts facts) {
        List<TravelPlanDay> result = new ArrayList<>();
        for (DayTemplate dayTemplate : dayTemplates) {
            List<TravelPlanStop> stops = new ArrayList<>();
            StopTemplate previous = null;
            int totalTransit = 0;
            int totalActivity = 0;
            int totalCost = 0;

            for (TravelPlanSlot slot : List.of(TravelPlanSlot.MORNING, TravelPlanSlot.AFTERNOON, TravelPlanSlot.EVENING)) {
                StopTemplate stop = dayTemplate.get(slot);
                if (stop == null) {
                    continue;
                }
                int transitMinutes = previous == null
                        ? estimateTransit(hotelAreaKey, areaKey(stop.key()), facts.destination())
                        : estimateTransit(areaKey(previous.key()), areaKey(stop.key()), facts.destination());
                LocalTime start = SLOT_START.get(slot);
                if (!stops.isEmpty()) {
                    start = laterOf(start, parseTime(stops.get(stops.size() - 1).endTime()).plusMinutes(transitMinutes));
                }
                start = laterOf(start, parseTime(stop.openTime()));
                LocalTime end = start.plusMinutes(stop.durationMinutes());
                if (end.isAfter(parseTime(stop.closeTime()))) {
                    end = parseTime(stop.closeTime());
                }

                int durationMinutes = Math.max((int) Duration.between(start, end).toMinutes(), 0);
                totalTransit += transitMinutes;
                totalActivity += durationMinutes;
                totalCost += stop.totalCost();

                stops.add(new TravelPlanStop(
                        slot,
                        stopName(stop.key(), facts),
                        areaLabel(areaKey(stop.key()), facts.preferChinese()),
                        null,
                        null,
                        null,
                        formatTime(start),
                        formatTime(end),
                        durationMinutes,
                        transitMinutes,
                        stop.totalCost(),
                        stop.openTime(),
                        stop.closeTime(),
                        stopRationale(stop.key(), facts.preferChinese()),
                        new TravelCostBreakdown(
                                stop.ticketCost(),
                                stop.foodCost(),
                                0,
                                stop.otherCost(),
                                stopCostNote(stop.key(), facts.preferChinese())
                        ),
                        null,
                        null
                ));
                previous = stop;
            }

            result.add(new TravelPlanDay(
                    dayTemplate.dayNumber(),
                    theme(dayTemplate.dayNumber(), dayTemplates.size(), facts.destination(), facts.preferChinese()),
                    stops.isEmpty() ? "09:00" : stops.get(0).startTime(),
                    stops.isEmpty() ? "20:00" : stops.get(stops.size() - 1).endTime(),
                    totalTransit,
                    totalActivity,
                    totalCost,
                    stops,
                    null
            ));
        }
        return result;
    }

    private BudgetSummary estimateBudget(List<TravelPlanDay> days, PlanningFacts facts) {
        int nights = Math.max(facts.days() - 1, 1);
        int hotelMin = facts.totalBudget() == null ? 420 * nights : Math.max(320 * nights, (int) Math.round(facts.totalBudget() * 0.34));
        int hotelMax = facts.totalBudget() == null ? 680 * nights : Math.max(hotelMin + 180, (int) Math.round(facts.totalBudget() * 0.48));
        int intercityMin = facts.origin().contains("未说明") || facts.origin().contains("not specified") || facts.origin().equalsIgnoreCase(facts.destination()) ? 0 : 220;
        int intercityMax = facts.origin().contains("未说明") || facts.origin().contains("not specified") || facts.origin().equalsIgnoreCase(facts.destination()) ? 120 : 420;
        int localTransitMin = 35 * facts.days();
        int localTransitMax = 70 * facts.days();
        int foodMin = 140 * facts.days();
        int foodMax = 260 * facts.days();
        int attractionBase = days.stream().flatMap(day -> day.stops().stream()).mapToInt(stop -> stop.estimatedCost() == null ? 0 : stop.estimatedCost()).sum();
        int attractionMin = Math.max(0, attractionBase - 60);
        int attractionMax = attractionBase + 120;
        return new BudgetSummary(hotelMin, hotelMax, intercityMin, intercityMax, localTransitMin, localTransitMax, foodMin, foodMax, attractionMin, attractionMax);
    }

    private List<TravelBudgetItem> budgetItems(BudgetSummary budget, boolean preferChinese) {
        return List.of(
                new TravelBudgetItem("Hotel", budget.hotelMin(), budget.hotelMax(), preferChinese ? "根据天数、商圈和预算上限估算酒店区间。" : "Estimated from trip length, district choice, and budget headroom."),
                new TravelBudgetItem("Intercity transport", budget.intercityMin(), budget.intercityMax(), preferChinese ? "按往返高铁或同级城际交通估算。" : "Assumes rail or similar round-trip intercity transport."),
                new TravelBudgetItem("Local transit", budget.localTransitMin(), budget.localTransitMax(), preferChinese ? "覆盖地铁、公交、短距离打车和接驳。" : "Covers metro, bus, and short ride-hailing hops."),
                new TravelBudgetItem("Food", budget.foodMin(), budget.foodMax(), preferChinese ? "包含正餐、小吃和茶饮机动空间。" : "Leaves room for meals, snacks, and a small signature splurge."),
                new TravelBudgetItem("Attractions and buffer", budget.attractionMin(), budget.attractionMax(), preferChinese ? "按景点门票、体验和少量机动金估算。" : "Derived from scheduled tickets, light experiences, and a resilience buffer.")
        );
    }

    private List<TravelConstraintCheck> checks(List<TravelPlanDay> days, BudgetSummary budget, PlanningFacts facts) {
        int maxTransit = days.stream().mapToInt(TravelPlanDay::totalTransitMinutes).max().orElse(0);
        int maxActivity = days.stream().mapToInt(TravelPlanDay::totalActivityMinutes).max().orElse(0);
        boolean openingOk = days.stream()
                .flatMap(day -> day.stops().stream())
                .allMatch(stop -> parseTime(stop.endTime()).compareTo(parseTime(stop.closeTime())) <= 0);

        if (facts.preferChinese()) {
            return List.of(
                    new TravelConstraintCheck("budget", facts.totalBudget() != null && budget.totalMax() <= facts.totalBudget() ? ConstraintCheckStatus.PASS : ConstraintCheckStatus.WARN, facts.totalBudget() != null && budget.totalMax() <= facts.totalBudget() ? "当前方案的上限花费仍在预算内。" : "当前方案上限可能超预算，优先通过降低酒店档次或减少付费项目来压缩。"),
                    new TravelConstraintCheck("opening-hours", openingOk ? ConstraintCheckStatus.PASS : ConstraintCheckStatus.FAIL, openingOk ? "所有景点时间都落在开放时段内。" : "至少有一个景点超过了开放时间，需要调整。"),
                    new TravelConstraintCheck("transit-load", maxTransit <= 150 ? ConstraintCheckStatus.PASS : ConstraintCheckStatus.WARN, maxTransit <= 150 ? "每日通勤强度基本可控。" : "某一天的通勤时间偏长，建议收缩跨区移动。"),
                    new TravelConstraintCheck("pace", facts.relaxedPace() && maxActivity > 420 ? ConstraintCheckStatus.WARN : ConstraintCheckStatus.PASS, facts.relaxedPace() && maxActivity > 420 ? "你要求轻松节奏，但某一天活动时长仍然偏满。" : "整体节奏与当前需求基本匹配。"),
                    new TravelConstraintCheck("dedupe", ConstraintCheckStatus.PASS, "重复景点已经在最终排程前去重。")
            );
        }

        return List.of(
                new TravelConstraintCheck("budget", facts.totalBudget() != null && budget.totalMax() <= facts.totalBudget() ? ConstraintCheckStatus.PASS : ConstraintCheckStatus.WARN, facts.totalBudget() != null && budget.totalMax() <= facts.totalBudget() ? "The estimated upper bound stays inside the stated budget." : "The current plan may exceed the budget ceiling. Lower the hotel level or trim paid stops first."),
                new TravelConstraintCheck("opening-hours", openingOk ? ConstraintCheckStatus.PASS : ConstraintCheckStatus.FAIL, openingOk ? "Every stop still fits its opening window." : "At least one stop exceeds its opening window."),
                new TravelConstraintCheck("transit-load", maxTransit <= 150 ? ConstraintCheckStatus.PASS : ConstraintCheckStatus.WARN, maxTransit <= 150 ? "Daily transit load stays manageable." : "One day contains too much cross-zone transit."),
                new TravelConstraintCheck("pace", facts.relaxedPace() && maxActivity > 420 ? ConstraintCheckStatus.WARN : ConstraintCheckStatus.PASS, facts.relaxedPace() && maxActivity > 420 ? "The trip is still a bit dense for a relaxed pace." : "The overall pace matches the current request."),
                new TravelConstraintCheck("dedupe", ConstraintCheckStatus.PASS, "Duplicate attractions were removed before the final schedule was rendered.")
        );
    }

    private List<String> highlights(List<StopTemplate> candidates, PlanningFacts facts) {
        return candidates.stream()
                .filter(StopTemplate::mustSee)
                .map(stop -> stopName(stop.key(), facts))
                .distinct()
                .limit(4)
                .toList();
    }

    private StopTemplate templateFromPreference(String preference) {
        String lower = preference.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "relaxed", "pace", "slow") || containsAny(preference, "轻松", "慢节奏", "悠闲")) {
            return null;
        }
        if (containsAny(lower, "lake") || preference.contains("湖")) {
            return new StopTemplate("WEST_LAKE", TravelPlanSlot.AFTERNOON, 180, 0, 0, 20, "06:00", "22:00", 90, true);
        }
        if (containsAny(lower, "temple") || preference.contains("寺")) {
            return new StopTemplate("LINGYIN_TEMPLE", TravelPlanSlot.MORNING, 150, 75, 0, 15, "07:00", "18:00", 92, true);
        }
        if (containsAny(lower, "food", "cuisine") || containsAny(preference, "美食", "小吃", "夜市")) {
            return new StopTemplate("CITY_FOOD", TravelPlanSlot.EVENING, 120, 0, 150, 0, "17:00", "23:00", 70, false);
        }
        if (containsAny(lower, "tea") || preference.contains("茶")) {
            return new StopTemplate("LONGJING_VILLAGE", TravelPlanSlot.AFTERNOON, 120, 0, 30, 50, "09:00", "18:00", 68, false);
        }
        return new StopTemplate("FLEXIBLE_BLOCK", TravelPlanSlot.AFTERNOON, 90, 0, 40, 30, "09:00", "21:00", 40, false);
    }

    private StopTemplate defaultStop(TravelPlanSlot slot) {
        return switch (slot) {
            case MORNING -> new StopTemplate("BROKEN_BRIDGE", slot, 90, 0, 0, 0, "06:30", "20:00", 30, false);
            case AFTERNOON -> new StopTemplate("FLEXIBLE_BLOCK", slot, 90, 0, 40, 30, "09:00", "21:00", 20, false);
            case EVENING -> new StopTemplate("LOCAL_DINNER", slot, 90, 0, 120, 0, "17:30", "22:00", 20, false);
        };
    }

    // localization and rendering

    private String title(PlanningFacts facts) {
        return facts.preferChinese()
                ? facts.destination() + " 行程方案"
                : facts.destination() + " Decision Plan";
    }

    private String summary(PlanningFacts facts) {
        return facts.preferChinese()
                ? "%d 天方案，已经按预算、营业时间、景点密度和跨区通勤做过收敛。".formatted(facts.days())
                : "%d-day plan shaped around budget, opening hours, attraction density, and transit load.".formatted(facts.days());
    }

    private String hotelAreaLabel(String key, boolean preferChinese) {
        return areaLabel(key, preferChinese);
    }

    private String hotelReason(String key, boolean preferChinese) {
        if (preferChinese) {
            return switch (key) {
                case "WEST_LAKE" -> "离西湖主景区和夜游步行段更近，适合第一次来杭州且希望少折返的人。";
                case "TRANSIT_HUB" -> "更适合预算敏感型安排，兼顾地铁和高铁站接驳。";
                default -> "在通勤效率、餐饮便利和景点覆盖之间更均衡。";
            };
        }
        return switch (key) {
            case "WEST_LAKE" -> "Best for scenic access, easy evening walks, and minimal backtracking.";
            case "TRANSIT_HUB" -> "Best when budget control matters but metro and station access still need to stay strong.";
            default -> "Balances commute efficiency, food access, and resilience around the core stops.";
        };
    }

    private String theme(int dayNumber, int totalDays, String destination, boolean preferChinese) {
        if (preferChinese) {
            if (dayNumber == 1 && totalDays > 1) {
                return "抵达 " + destination + "，先轻松进入状态";
            }
            if (dayNumber == totalDays && totalDays > 1) {
                return "收尾游览与返程安排";
            }
            return destination + " 核心游览日";
        }
        if (dayNumber == 1 && totalDays > 1) {
            return "Arrival and easy start in " + destination;
        }
        if (dayNumber == totalDays && totalDays > 1) {
            return "Flexible finish and departure from " + destination;
        }
        return "Core sightseeing in " + destination;
    }

    private String stopName(String key, PlanningFacts facts) {
        boolean preferChinese = facts != null && facts.preferChinese();
        return switch (key) {
            case "WEST_LAKE" -> preferChinese ? "西湖" : "West Lake";
            case "LINGYIN_TEMPLE" -> preferChinese ? "灵隐寺" : "Lingyin Temple";
            case "HEFANG_STREET" -> preferChinese ? "河坊街" : "Hefang Street";
            case "BROKEN_BRIDGE" -> preferChinese ? "断桥残雪" : "Broken Bridge";
            case "LONGJING_VILLAGE" -> preferChinese ? "龙井村" : "Longjing Village";
            case "CITY_FOOD" -> preferChinese ? "武林夜市" : "Wulin Night Market";
            case "LOCAL_DINNER" -> preferChinese ? "知味观味庄" : "Zhiweiguan Weizhuang";
            case "FLEXIBLE_BLOCK" -> preferChinese ? "湖滨步行街" : "Hubin Pedestrian Street";
            default -> preferChinese ? "自定义停留点" : "Custom stop";
        };
    }

    private String stopRationale(String key, boolean preferChinese) {
        if (preferChinese) {
            return switch (key) {
                case "WEST_LAKE" -> "把西湖放在主时段，可以稳定承担这次行程的观景核心。";
                case "LINGYIN_TEMPLE" -> "寺庙类点位更适合上午进入，体感和人流都会好一些。";
                case "HEFANG_STREET" -> "用夜间时段覆盖河坊街，更适合边走边吃。";
                case "BROKEN_BRIDGE" -> "清晨去断桥，拍照和步行体验都更好。";
                case "LONGJING_VILLAGE" -> "把龙井村放在下午，更适合喝茶和留白。";
                case "CITY_FOOD" -> "夜市段放在晚上，既顺路又不挤占白天景点时间。";
                case "LOCAL_DINNER" -> "给本地晚餐留一个明确时段，行程执行时更稳。";
                default -> "留出一个机动街区，便于根据天气和体力微调。";
            };
        }
        return switch (key) {
            case "WEST_LAKE" -> "Use West Lake as the scenic anchor block for the trip.";
            case "LINGYIN_TEMPLE" -> "Temple visits fit best in the morning before heat and crowds build.";
            case "HEFANG_STREET" -> "Evening is the cleanest way to cover food street energy without blocking daylight sightseeing.";
            case "BROKEN_BRIDGE" -> "Broken Bridge works best as an easy, photogenic morning walk.";
            case "LONGJING_VILLAGE" -> "Keep Longjing Village for a slower tea-focused afternoon.";
            case "CITY_FOOD" -> "Use the night-market slot for food instead of spending a full daylight block on dining.";
            case "LOCAL_DINNER" -> "Lock one evening block for a dependable local dinner.";
            default -> "Leave one adaptable district block for weather or energy-level changes.";
        };
    }

    private String stopCostNote(String key, boolean preferChinese) {
        if (preferChinese) {
            return switch (key) {
                case "LINGYIN_TEMPLE" -> "主要开销来自门票和香花券。";
                case "HEFANG_STREET", "CITY_FOOD", "LOCAL_DINNER" -> "主要开销来自餐饮和小吃。";
                case "LONGJING_VILLAGE" -> "主要开销来自茶饮、休息和小体验。";
                default -> "以轻度消费和机动支出为主。";
            };
        }
        return switch (key) {
            case "LINGYIN_TEMPLE" -> "Most of the spend comes from tickets.";
            case "HEFANG_STREET", "CITY_FOOD", "LOCAL_DINNER" -> "Most of the spend comes from food and snacks.";
            case "LONGJING_VILLAGE" -> "Most of the spend comes from tea breaks and light experiences.";
            default -> "Mostly light discretionary spend.";
        };
    }

    private String areaKey(String stopKey) {
        return switch (stopKey) {
            case "WEST_LAKE", "BROKEN_BRIDGE", "LOCAL_DINNER" -> "WEST_LAKE";
            case "LINGYIN_TEMPLE" -> "HISTORIC_CORE";
            case "HEFANG_STREET" -> "OLD_TOWN";
            case "LONGJING_VILLAGE" -> "LONGJING";
            case "CITY_FOOD", "FLEXIBLE_BLOCK" -> "CITY_CENTER";
            default -> "CITY_CENTER";
        };
    }

    private String areaLabel(String key, boolean preferChinese) {
        if (preferChinese) {
            return switch (key) {
                case "WEST_LAKE" -> "西湖湖滨";
                case "HISTORIC_CORE" -> "灵隐片区";
                case "OLD_TOWN" -> "吴山河坊街";
                case "LONGJING" -> "龙井茶村";
                case "TRANSIT_HUB" -> "东站/地铁枢纽";
                case "HOTEL_DISTRICT" -> "酒店周边";
                default -> "市中心";
            };
        }
        return switch (key) {
            case "WEST_LAKE" -> "West Lake Waterfront";
            case "HISTORIC_CORE" -> "Historic Core";
            case "OLD_TOWN" -> "Old Town";
            case "LONGJING" -> "Longjing";
            case "TRANSIT_HUB" -> "Transit Hub";
            case "HOTEL_DISTRICT" -> "Hotel District";
            default -> "City Center";
        };
    }

    private String categoryLabel(String category, boolean preferChinese) {
        if (!preferChinese) {
            return category;
        }
        return switch (category) {
            case "Hotel" -> "住宿";
            case "Intercity transport" -> "跨城交通";
            case "Local transit" -> "本地通勤";
            case "Food" -> "餐饮";
            case "Attractions and buffer" -> "景点与机动预算";
            default -> category;
        };
    }

    private String routeSummary(TravelTransitLeg route, boolean preferChinese) {
        String lineText = route.lineNames().isEmpty() ? route.mode() : String.join(" / ", route.lineNames());
        if (preferChinese) {
            return lineText + "，约 " + safe(route.durationMinutes()) + " 分钟，步行 " + safe(route.walkingMinutes()) + " 分钟，约 " + safe(route.estimatedCost()) + " 元";
        }
        return lineText + ", about " + safe(route.durationMinutes()) + " min, walk " + safe(route.walkingMinutes()) + " min, around " + safe(route.estimatedCost()) + " CNY";
    }

    private String stepLabel(TravelTransitStep step, boolean preferChinese) {
        String base = step.lineName() != null && !step.lineName().isBlank() ? step.lineName() : step.title();
        if (step.fromName() != null && !step.fromName().isBlank() && step.toName() != null && !step.toName().isBlank()) {
            base += preferChinese ? "，" + step.fromName() + " -> " + step.toName() : ", " + step.fromName() + " -> " + step.toName();
        }
        return preferChinese ? base + "，约 " + safe(step.durationMinutes()) + " 分钟" : base + ", about " + safe(step.durationMinutes()) + " min";
    }

    // parsing and helpers

    private String extractOrigin(String text) {
        Matcher zh = ZH_FROM_TO.matcher(text);
        if (zh.find()) {
            return cleanupLocation(zh.group(1));
        }
        Matcher en = EN_FROM_TO.matcher(text);
        if (en.find()) {
            return cleanupLocation(en.group(1));
        }
        return null;
    }

    private String extractDestination(String text) {
        Matcher zh = ZH_FROM_TO.matcher(text);
        if (zh.find()) {
            return cleanupLocation(zh.group(2));
        }
        Matcher en = EN_FROM_TO.matcher(text);
        if (en.find()) {
            return cleanupLocation(en.group(2));
        }
        Matcher destinationOnly = EN_DESTINATION.matcher(text);
        if (destinationOnly.find()) {
            return cleanupLocation(destinationOnly.group(1));
        }
        Matcher toOnly = Pattern.compile("to\\s+([A-Za-z][A-Za-z\\s-]{1,40})", Pattern.CASE_INSENSITIVE).matcher(text);
        if (toOnly.find()) {
            return cleanupLocation(toOnly.group(1));
        }
        Matcher goOnly = Pattern.compile("去([^，。,.]{1,20})").matcher(text);
        if (goOnly.find()) {
            return cleanupLocation(goOnly.group(1));
        }
        Matcher arriveOnly = Pattern.compile("到([^，。,.]{1,20})").matcher(text);
        return arriveOnly.find() ? cleanupLocation(arriveOnly.group(1)) : null;
    }

    private Integer extractDays(String text) {
        Matcher zh = ZH_DAYS.matcher(text);
        if (zh.find()) {
            return Integer.parseInt(zh.group(1));
        }
        Matcher en = EN_DAYS.matcher(text);
        return en.find() ? Integer.parseInt(en.group(1)) : null;
    }

    private Integer extractBudget(String text) {
        Matcher matcher = BUDGET.matcher(text.replace(",", ""));
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            if (value >= 300) {
                return value;
            }
        }
        return null;
    }

    private Integer parseBudget(String budget) {
        return budget == null || budget.isBlank() ? null : extractBudget(budget);
    }

    private List<String> extractPreferences(String text) {
        Set<String> values = new LinkedHashSet<>();
        for (String segment : text.split("[.;\\n。；]")) {
            String extracted = extractPreferenceTail(segment.trim());
            if (extracted == null || extracted.isBlank()) {
                continue;
            }
            for (String item : extracted.split("(?i)\\band\\b|,|/|、|和")) {
                String cleaned = item.trim();
                if (!cleaned.isBlank()) {
                    values.add(cleaned);
                }
            }
        }
        if (values.isEmpty()) {
            if (containsAny(text.toLowerCase(Locale.ROOT), "food", "cuisine") || containsAny(text, "美食", "小吃", "夜市")) {
                values.add("local food");
            }
            if (containsAny(text.toLowerCase(Locale.ROOT), "relaxed", "slow pace") || containsAny(text, "轻松", "慢节奏")) {
                values.add("relaxed pace");
            }
        }
        return values.stream().filter(value -> !value.isBlank()).toList();
    }

    private String extractPreferenceTail(String segment) {
        String lower = segment.toLowerCase(Locale.ROOT);
        for (String marker : List.of("focus on", "include", "prefer", "must-see", "want")) {
            int index = lower.indexOf(marker);
            if (index >= 0) {
                return segment.substring(index + marker.length()).trim();
            }
        }
        for (String marker : List.of("想去", "想看", "重点", "偏好", "包含", "希望")) {
            int index = segment.indexOf(marker);
            if (index >= 0) {
                return segment.substring(index + marker.length()).trim();
            }
        }
        return null;
    }

    private int estimateTransit(String fromArea, String toArea, String destination) {
        if (fromArea.equals(toArea)) {
            return 15;
        }
        if (isHangzhou(destination)) {
            Map<String, Integer> matrix = new LinkedHashMap<>();
            matrix.put("WEST_LAKE->HISTORIC_CORE", 35);
            matrix.put("HISTORIC_CORE->WEST_LAKE", 35);
            matrix.put("WEST_LAKE->OLD_TOWN", 22);
            matrix.put("OLD_TOWN->WEST_LAKE", 22);
            matrix.put("WEST_LAKE->LONGJING", 30);
            matrix.put("LONGJING->WEST_LAKE", 30);
            matrix.put("CITY_CENTER->OLD_TOWN", 18);
            matrix.put("OLD_TOWN->CITY_CENTER", 18);
            matrix.put("HISTORIC_CORE->CITY_CENTER", 28);
            matrix.put("CITY_CENTER->HISTORIC_CORE", 28);
            return matrix.getOrDefault(fromArea + "->" + toArea, 25);
        }
        return 35;
    }

    private boolean isHangzhou(String destination) {
        return destination != null && (destination.toLowerCase(Locale.ROOT).contains("hangzhou") || destination.contains("杭州"));
    }

    private String cleanupLocation(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("(?i)next weekend|for one person|with.*$", "")
                .replace("trip", "")
                .replace("旅游", "")
                .replace("旅行", "")
                .replace("行", "")
                .trim();
    }

    private boolean preferChinese(AgentExecutionContext context) {
        if (context.userMessage() != null && context.userMessage().codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF)) {
            return true;
        }
        return context.taskMemory().destination() != null
                && context.taskMemory().destination().codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private String firstNonBlank(String current, String candidate) {
        return current != null && !current.isBlank() ? current : candidate;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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

    private LocalTime parseTime(String value) {
        return LocalTime.parse(value, TIME_FORMATTER);
    }

    private String formatTime(LocalTime value) {
        return value.format(TIME_FORMATTER);
    }

    private LocalTime laterOf(LocalTime left, LocalTime right) {
        return left.isAfter(right) ? left : right;
    }

    private record PlanningFacts(String origin, String destination, int days, Integer totalBudget, List<String> preferences, boolean relaxedPace, boolean preferChinese) {
    }

    private record StopTemplate(String key, TravelPlanSlot slot, int durationMinutes, int ticketCost, int foodCost, int otherCost, String openTime, String closeTime, int priority, boolean mustSee) {
        int totalCost() { return ticketCost + foodCost + otherCost; }
    }

    private record DayTemplate(int dayNumber, EnumMap<TravelPlanSlot, StopTemplate> slots) {
        boolean hasSlot(TravelPlanSlot slot) { return slots.containsKey(slot); }
        StopTemplate get(TravelPlanSlot slot) { return slots.get(slot); }
        void assign(TravelPlanSlot slot, StopTemplate stop) { slots.put(slot, stop); }
        int assignedCount() { return (int) slots.values().stream().filter(item -> item != null).count(); }
    }

    private record BudgetSummary(int hotelMin, int hotelMax, int intercityMin, int intercityMax, int localTransitMin, int localTransitMax, int foodMin, int foodMax, int attractionMin, int attractionMax) {
        int totalMin() { return hotelMin + intercityMin + localTransitMin + foodMin + attractionMin; }
        int totalMax() { return hotelMax + intercityMax + localTransitMax + foodMax + attractionMax; }
    }
}
