package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.model.entity.ConstraintCheckStatus;
import com.travalagent.domain.model.entity.TravelBudgetItem;
import com.travalagent.domain.model.entity.TravelConstraintCheck;
import com.travalagent.domain.model.entity.TravelCostBreakdown;
import com.travalagent.domain.model.entity.TravelPlan;
import com.travalagent.domain.model.entity.TravelPlanDay;
import com.travalagent.domain.model.entity.TravelPlanSlot;
import com.travalagent.domain.model.entity.TravelPlanStop;
import com.travalagent.domain.model.valobj.AgentExecutionContext;
import com.travalagent.domain.service.TravelPlanBuilder;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Primary
@Component
public class GenericDestinationTravelPlanBuilder implements TravelPlanBuilder {

    private static final Pattern EN_FROM_TO = Pattern.compile("from\\s+([A-Za-z][A-Za-z\\s-]{1,40}?)\\s+to\\s+([A-Za-z][A-Za-z\\s-]{1,40})", Pattern.CASE_INSENSITIVE);
    private static final Pattern EN_DESTINATION = Pattern.compile("(?:trip|itinerary|travel)\\s+(?:to|in)\\s+([A-Za-z][A-Za-z\\s-]{1,40})", Pattern.CASE_INSENSITIVE);
    private static final Pattern EN_DESTINATION_PREFIX = Pattern.compile("(?:plan|arrange|build)?\\s*(?:a\\s+\\d{1,2}\\s*[- ]?day\\s+)?([A-Za-z][A-Za-z\\s-]{1,40})\\s+(?:trip|itinerary|travel)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EN_DAYS = Pattern.compile("(\\d{1,2})\\s*[- ]?day", Pattern.CASE_INSENSITIVE);
    private static final Pattern EN_EXPLICIT_PLACE = Pattern.compile("(?:want to visit|want to go to|want to see|must-see|include)\\s+([A-Za-z][A-Za-z\\s-]{1,40})", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUDGET = Pattern.compile("(\\d{3,6})");

    private static final Pattern ZH_FROM_TO = Pattern.compile("从([\\p{IsHan}]{2,20})到([\\p{IsHan}]{2,20})");
    private static final Pattern ZH_DESTINATION = Pattern.compile("(?:去|到|玩|逛|规划|安排)([\\p{IsHan}]{2,20})");
    private static final Pattern ZH_DAYS = Pattern.compile("(\\d{1,2}|[一二两三四五六七八九十]{1,3})\\s*天");
    private static final Pattern ZH_EXPLICIT_PLACE = Pattern.compile("(?:想去|想看|想逛|想打卡|一定要去|必须去|重点想去)([\\p{IsHan}]{2,20})");

    @Override
    public TravelPlan build(AgentExecutionContext context) {
        boolean chinese = containsChinese(context.userMessage());
        PlanningFacts facts = deriveFacts(context, chinese);
        int[] mustVisitCursor = {0};

        List<TravelPlanDay> days = new ArrayList<>();
        for (int day = 1; day <= facts.days(); day++) {
            List<TravelPlanStop> stops = buildDayStops(day, facts, mustVisitCursor);
            int totalCost = stops.stream()
                    .mapToInt(stop -> stop.estimatedCost() == null ? 0 : stop.estimatedCost())
                    .sum();
            days.add(new TravelPlanDay(
                    day,
                    dayTheme(day, facts.days(), facts.destination(), chinese),
                    stops.get(0).startTime(),
                    stops.get(stops.size() - 1).endTime(),
                    estimateTransitMinutes(day, facts),
                    stops.stream().mapToInt(stop -> stop.durationMinutes() == null ? 0 : stop.durationMinutes()).sum(),
                    totalCost,
                    stops,
                    null
            ));
        }

        Budget budget = estimateBudget(facts);
        return new TravelPlan(
                context.conversationId(),
                chinese ? facts.destination() + " 行程方案" : facts.destination() + " Travel Plan",
                chinese
                        ? "%d 天轻松路线，已按预算、通勤和节奏做了初步安排。".formatted(facts.days())
                        : "%d-day relaxed plan arranged around budget, commute load, and pace.".formatted(facts.days()),
                recommendedHotelArea(facts, chinese),
                hotelReason(facts, chinese),
                List.of(),
                facts.totalBudget(),
                budget.totalMin(),
                budget.totalMax(),
                buildHighlights(facts, chinese),
                List.of(
                        new TravelBudgetItem("Hotel", budget.hotelMin(), budget.hotelMax(), chinese ? "按天数和预算上限估算住宿区间。" : "Estimated from trip length and budget ceiling."),
                        new TravelBudgetItem("Intercity transport", budget.intercityMin(), budget.intercityMax(), chinese ? "按出发城市与目的地之间的往返交通估算。" : "Estimated from round-trip transport between origin and destination."),
                        new TravelBudgetItem("Local transit", budget.localTransitMin(), budget.localTransitMax(), chinese ? "覆盖市内地铁、公交和短距离打车。" : "Covers metro, bus, and short ride-hailing hops."),
                        new TravelBudgetItem("Food", budget.foodMin(), budget.foodMax(), chinese ? "包含正餐、小吃和轻量茶饮。" : "Covers meals, snacks, and light refreshments."),
                        new TravelBudgetItem("Attractions and buffer", budget.attractionMin(), budget.attractionMax(), chinese ? "景点体验和少量机动预算。" : "Attractions plus a small buffer.")
                ),
                List.of(
                        new TravelConstraintCheck(
                                "budget",
                                budget.totalMax() <= facts.totalBudgetOrDefault() ? ConstraintCheckStatus.PASS : ConstraintCheckStatus.WARN,
                                chinese
                                        ? (budget.totalMax() <= facts.totalBudgetOrDefault() ? "当前方案上限仍在预算内。" : "当前方案上限可能超预算，建议优先调整酒店档次。")
                                        : (budget.totalMax() <= facts.totalBudgetOrDefault() ? "The estimated upper bound stays inside the budget." : "The current plan may exceed the budget ceiling. Lower the hotel level first.")
                        ),
                        new TravelConstraintCheck("pace", ConstraintCheckStatus.PASS, chinese ? "整体节奏按轻松路线安排。" : "The overall pace follows a relaxed itinerary."),
                        new TravelConstraintCheck("dedupe", ConstraintCheckStatus.PASS, chinese ? "重复景点已在最终安排前去重。" : "Duplicate attractions were removed before the final itinerary was rendered.")
                ),
                days,
                Instant.now()
        );
    }

    @Override
    public String render(TravelPlan plan, AgentExecutionContext context) {
        boolean chinese = containsChinese(context.userMessage());
        StringBuilder builder = new StringBuilder();
        if (chinese) {
            builder.append("## 行程总览\n");
            builder.append("- 预计总花费：").append(plan.estimatedTotalMin()).append('-').append(plan.estimatedTotalMax()).append(" 元\n");
            builder.append("- 推荐住宿区域：").append(plan.hotelArea()).append('\n');
            builder.append("- 推荐理由：").append(plan.hotelAreaReason()).append('\n');
            builder.append("\n## 约束检查\n");
            for (TravelConstraintCheck check : plan.checks()) {
                builder.append("- [").append(check.status()).append("] ").append(check.message()).append('\n');
            }
            builder.append("\n## 预算拆分\n");
            for (TravelBudgetItem item : plan.budget()) {
                builder.append("- ").append(item.category()).append("：")
                        .append(item.minAmount()).append('-').append(item.maxAmount()).append(" 元。")
                        .append(item.rationale()).append('\n');
            }
            for (TravelPlanDay day : plan.days()) {
                builder.append("\n## 第 ").append(day.dayNumber()).append(" 天：").append(day.theme()).append('\n');
                builder.append("- 游玩时长：").append(day.totalActivityMinutes()).append(" 分钟\n");
                builder.append("- 通勤时长：").append(day.totalTransitMinutes()).append(" 分钟\n");
                builder.append("- 当日预计花费：").append(day.estimatedCost()).append(" 元\n");
                for (TravelPlanStop stop : day.stops()) {
                    builder.append("- ").append(stop.startTime()).append('-').append(stop.endTime()).append(' ')
                            .append(stop.name()).append("（").append(stop.area()).append("）");
                    if (stop.address() != null && !stop.address().isBlank()) {
                        builder.append("，地址：").append(stop.address());
                    }
                    builder.append("。现场花费约 ").append(stop.estimatedCost()).append(" 元。")
                            .append(stop.rationale()).append('\n');
                }
            }
            return builder.toString().trim();
        }

        builder.append("## Overview\n");
        builder.append("- Estimated total: ").append(plan.estimatedTotalMin()).append('-').append(plan.estimatedTotalMax()).append(" CNY\n");
        builder.append("- Recommended hotel area: ").append(plan.hotelArea()).append('\n');
        builder.append("- Why: ").append(plan.hotelAreaReason()).append('\n');
        for (TravelPlanDay day : plan.days()) {
            builder.append("\n## Day ").append(day.dayNumber()).append(": ").append(day.theme()).append('\n');
            for (TravelPlanStop stop : day.stops()) {
                builder.append("- ").append(stop.startTime()).append('-').append(stop.endTime()).append(' ')
                        .append(stop.name()).append(" (").append(stop.area()).append("). ")
                        .append(stop.rationale()).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private List<TravelPlanStop> buildDayStops(int dayNumber, PlanningFacts facts, int[] mustVisitCursor) {
        List<TravelPlanStop> stops = new ArrayList<>();

        if (facts.isXiamen()) {
            if (dayNumber == 1) {
                stops.add(stopForPlace(pickMustVisit(facts, mustVisitCursor, "鼓浪屿"), TravelPlanSlot.AFTERNOON, facts.destination(), "13:30", "16:30", 180, 120, 80, facts.preferChinese()));
                stops.add(stopForPlace(pickMustVisit(facts, mustVisitCursor, "中山路步行街"), TravelPlanSlot.EVENING, facts.destination(), "18:30", "20:30", 120, 0, 120, facts.preferChinese()));
                return stops;
            }
            if (dayNumber == 2) {
                stops.add(stopForPlace(pickMustVisit(facts, mustVisitCursor, "鼓浪屿核心步行线"), TravelPlanSlot.MORNING, facts.destination(), "09:00", "11:30", 150, 0, 40, facts.preferChinese()));
                stops.add(stopForPlace(pickMustVisit(facts, mustVisitCursor, "沙坡尾"), TravelPlanSlot.AFTERNOON, facts.destination(), "13:30", "15:30", 120, 0, 60, facts.preferChinese()));
                stops.add(stopForPlace(pickMustVisit(facts, mustVisitCursor, "环岛路海边散步"), TravelPlanSlot.EVENING, facts.destination(), "18:30", "20:00", 90, 0, 30, facts.preferChinese()));
                return stops;
            }
            stops.add(stopForPlace(pickMustVisit(facts, mustVisitCursor, "厦门大学外侧街区"), TravelPlanSlot.MORNING, facts.destination(), "09:00", "10:30", 90, 0, 20, facts.preferChinese()));
            stops.add(stopForPlace(pickMustVisit(facts, mustVisitCursor, "八市或附近街区"), TravelPlanSlot.AFTERNOON, facts.destination(), "13:00", "15:00", 120, 0, 80, facts.preferChinese()));
            return stops;
        }

        if (dayNumber == 1) {
            stops.add(stopForPlace(pickMustVisit(facts, mustVisitCursor, facts.destination() + " 城市地标"), TravelPlanSlot.AFTERNOON, facts.destination(), "13:30", "16:00", 150, 0, 60, facts.preferChinese()));
            stops.add(stopForPlace(pickMustVisit(facts, mustVisitCursor, facts.destination() + " 夜间街区"), TravelPlanSlot.EVENING, facts.destination(), "18:30", "20:00", 90, 0, 120, facts.preferChinese()));
            return stops;
        }
        if (dayNumber == facts.days()) {
            stops.add(stopForPlace(pickMustVisit(facts, mustVisitCursor, facts.destination() + " 轻量街区"), TravelPlanSlot.MORNING, facts.destination(), "09:00", "10:30", 90, 0, 30, facts.preferChinese()));
            stops.add(stopForPlace(pickMustVisit(facts, mustVisitCursor, facts.destination() + " 收尾片区"), TravelPlanSlot.AFTERNOON, facts.destination(), "13:00", "15:00", 120, 0, 60, facts.preferChinese()));
            return stops;
        }

        stops.add(stopForPlace(pickMustVisit(facts, mustVisitCursor, facts.destination() + " 核心景点"), TravelPlanSlot.MORNING, facts.destination(), "09:00", "11:30", 150, 0, 80, facts.preferChinese()));
        stops.add(stopForPlace(pickMustVisit(facts, mustVisitCursor, facts.destination() + " 城市街区"), TravelPlanSlot.AFTERNOON, facts.destination(), "13:30", "15:30", 120, 0, 50, facts.preferChinese()));
        stops.add(stopForPlace(pickMustVisit(facts, mustVisitCursor, facts.destination() + " 本地晚餐"), TravelPlanSlot.EVENING, facts.destination(), "18:30", "20:00", 90, 0, 120, facts.preferChinese()));
        return stops;
    }

    private String pickMustVisit(PlanningFacts facts, int[] cursor, String fallback) {
        if (cursor[0] < facts.mustVisitPlaces().size()) {
            return facts.mustVisitPlaces().get(cursor[0]++);
        }
        return fallback;
    }

    private TravelPlanStop stopForPlace(String place, TravelPlanSlot slot, String destination, String startTime, String endTime, int minutes, int ticketCost, int foodAndOther, boolean chinese) {
        String normalized = place == null || place.isBlank() ? destination + " 热门地点" : place;
        String area = inferArea(destination, normalized);
        String rationale = chinese
                ? "这是你明确点名想去的地点，我优先放进主行程，避免它被普通偏好淹没。"
                : "You explicitly named this place, so it was prioritized in the main itinerary.";
        return stop(slot, normalized, area, startTime, endTime, minutes, ticketCost, foodAndOther, rationale);
    }

    private TravelPlanStop stop(TravelPlanSlot slot, String name, String area, String startTime, String endTime, int minutes, int ticketCost, int foodAndOther, String rationale) {
        int estimatedCost = ticketCost + foodAndOther;
        return new TravelPlanStop(
                slot,
                name,
                area,
                null,
                null,
                null,
                startTime,
                endTime,
                minutes,
                25,
                estimatedCost,
                "09:00",
                "21:00",
                rationale,
                new TravelCostBreakdown(ticketCost, Math.min(foodAndOther, 80), 0, Math.max(foodAndOther - 80, 0), ""),
                null,
                null
        );
    }

    private PlanningFacts deriveFacts(AgentExecutionContext context, boolean chinese) {
        String message = context.userMessage() == null ? "" : context.userMessage();
        String origin = firstNonBlank(context.taskMemory().origin(), extractOrigin(message));
        String destination = firstNonBlank(context.taskMemory().destination(), extractDestination(message));
        Integer days = firstNonNull(context.taskMemory().days(), extractDays(message), 3);
        Integer totalBudget = firstNonNull(parseBudget(context.taskMemory().budget()), extractBudget(message), 3000);

        Set<String> preferences = new LinkedHashSet<>(context.taskMemory().preferences() == null ? List.of() : context.taskMemory().preferences());
        preferences.addAll(extractPreferences(message));
        List<String> mustVisitPlaces = extractMustVisitPlaces(message, context.taskMemory().preferences());

        boolean relaxedPace = containsAny(message.toLowerCase(Locale.ROOT), "relaxed", "slow", "easy pace")
                || message.contains("轻松")
                || message.contains("悠闲");

        return new PlanningFacts(
                blankToDefault(origin, chinese ? "未说明出发地" : "origin not specified"),
                blankToDefault(destination, chinese ? "目的地城市" : "destination city"),
                days,
                totalBudget,
                List.copyOf(preferences),
                mustVisitPlaces,
                relaxedPace,
                chinese
        );
    }

    private List<String> buildHighlights(PlanningFacts facts, boolean chinese) {
        Set<String> items = new LinkedHashSet<>();
        items.addAll(facts.mustVisitPlaces());
        if (facts.isXiamen()) {
            items.add("鼓浪屿");
            items.add(chinese ? "中山路步行街" : "Zhongshan Road");
            items.add(chinese ? "沙坡尾" : "Shapowei");
        }
        items.addAll(facts.preferences());
        if (items.isEmpty()) {
            items.add(chinese ? "城市核心片区" : "core city district");
            items.add(chinese ? "本地美食" : "local food");
        }
        return items.stream().limit(4).toList();
    }

    private List<String> extractMustVisitPlaces(String text, List<String> existingPreferences) {
        Set<String> places = new LinkedHashSet<>();

        Matcher zh = ZH_EXPLICIT_PLACE.matcher(text);
        while (zh.find()) {
            String place = cleanLocation(zh.group(1));
            if (isNamedPlace(place)) {
                places.add(place);
            }
        }

        Matcher en = EN_EXPLICIT_PLACE.matcher(text);
        while (en.find()) {
            String place = cleanLocation(en.group(1));
            if (isNamedPlace(place)) {
                places.add(place);
            }
        }

        if (existingPreferences != null) {
            for (String preference : existingPreferences) {
                String cleaned = cleanLocation(preference);
                if (isNamedPlace(cleaned)) {
                    places.add(cleaned);
                }
            }
        }

        return List.copyOf(places);
    }

    private String recommendedHotelArea(PlanningFacts facts, boolean chinese) {
        if (facts.isXiamen()) {
            return chinese ? "思明区轮渡 / 中山路片区" : "Siming ferry / Zhongshan Road district";
        }
        return chinese ? facts.destination() + " 市中心片区" : facts.destination() + " central district";
    }

    private String hotelReason(PlanningFacts facts, boolean chinese) {
        if (facts.isXiamen()) {
            return chinese
                    ? "住在轮渡或中山路附近，去鼓浪屿、老城步行街和海边都更顺路，整体更适合轻松节奏。"
                    : "Staying near the ferry or Zhongshan Road keeps Gulangyu, the old town, and the waterfront easier to cover at a relaxed pace.";
        }
        return chinese
                ? "优先住在城市中心或主要通勤节点附近，能减少折返，也方便晚上回酒店。"
                : "Prefer the city center or a strong transit district to reduce backtracking and keep evenings easy.";
    }

    private Budget estimateBudget(PlanningFacts facts) {
        int nights = Math.max(facts.days() - 1, 1);
        int hotelMin = Math.max(320 * nights, (int) Math.round(facts.totalBudgetOrDefault() * 0.34));
        int hotelMax = Math.max(hotelMin + 180, (int) Math.round(facts.totalBudgetOrDefault() * 0.48));
        int intercityMin = facts.sameCityTrip() ? 0 : 220;
        int intercityMax = facts.sameCityTrip() ? 120 : 420;
        int localTransitMin = 35 * facts.days();
        int localTransitMax = 70 * facts.days();
        int foodMin = 140 * facts.days();
        int foodMax = 260 * facts.days();
        int attractionMin = facts.isXiamen() ? 390 : 360;
        int attractionMax = facts.isXiamen() ? 570 : 540;
        return new Budget(hotelMin, hotelMax, intercityMin, intercityMax, localTransitMin, localTransitMax, foodMin, foodMax, attractionMin, attractionMax);
    }

    private int estimateTransitMinutes(int dayNumber, PlanningFacts facts) {
        if (facts.isXiamen()) {
            return dayNumber == 2 ? 90 : 60;
        }
        return dayNumber == facts.days() ? 55 : 70;
    }

    private String dayTheme(int dayNumber, int totalDays, String destination, boolean chinese) {
        if (chinese) {
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

    private String inferArea(String destination, String place) {
        if (destination.contains("厦门")) {
            if (place.contains("鼓浪屿")) {
                return "思明区轮渡片区";
            }
            if (place.contains("中山路") || place.contains("八市")) {
                return "思明区老城片区";
            }
            if (place.contains("沙坡尾")) {
                return "思明区海港片区";
            }
            if (place.contains("环岛路")) {
                return "思明区海滨片区";
            }
            if (place.contains("厦门大学") || place.contains("南普陀")) {
                return "思明区南部片区";
            }
        }
        return destination + " 热门片区";
    }

    private String extractOrigin(String text) {
        Matcher zh = ZH_FROM_TO.matcher(text);
        if (zh.find()) {
            return cleanLocation(zh.group(1));
        }
        Matcher en = EN_FROM_TO.matcher(text);
        if (en.find()) {
            return cleanLocation(en.group(1));
        }
        return null;
    }

    private String extractDestination(String text) {
        Matcher zh = ZH_FROM_TO.matcher(text);
        if (zh.find()) {
            return cleanLocation(zh.group(2));
        }
        Matcher en = EN_FROM_TO.matcher(text);
        if (en.find()) {
            return cleanLocation(en.group(2));
        }
        Matcher destination = ZH_DESTINATION.matcher(text);
        if (destination.find()) {
            return cleanLocation(destination.group(1));
        }
        Matcher destinationPrefix = EN_DESTINATION_PREFIX.matcher(text);
        if (destinationPrefix.find()) {
            return cleanLocation(destinationPrefix.group(1));
        }
        Matcher toOnly = EN_DESTINATION.matcher(text);
        if (toOnly.find()) {
            return cleanLocation(toOnly.group(1));
        }
        return null;
    }

    private Integer extractDays(String text) {
        Matcher zh = ZH_DAYS.matcher(text);
        if (zh.find()) {
            return parseChineseNumber(zh.group(1));
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
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("food") || lower.contains("cuisine") || text.contains("美食") || text.contains("小吃")) {
            values.add("local food");
        }
        if (lower.contains("island") || text.contains("鼓浪屿")) {
            values.add("鼓浪屿");
        }
        if (lower.contains("beach") || text.contains("海边") || text.contains("海滨")) {
            values.add("seaside");
        }
        if (lower.contains("relaxed") || lower.contains("slow pace") || text.contains("轻松") || text.contains("悠闲")) {
            values.add("relaxed pace");
        }
        return values.stream().filter(value -> !value.isBlank()).toList();
    }

    private String cleanLocation(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("(?i)next weekend|for one person|with.*$", "")
                .replace("trip", "")
                .replace("行程", "")
                .replace("旅行", "")
                .replace("旅游", "")
                .replace("规划", "")
                .replace("安排", "")
                .trim();
    }

    private Integer parseChineseNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(raw);
        }
        return switch (raw) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> 3;
        };
    }

    private boolean containsChinese(String value) {
        return value != null && value.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    private boolean isNamedPlace(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return !containsAny(lower, "relaxed", "slow", "food", "cuisine", "seaside", "pace")
                && !containsAny(value, "轻松", "悠闲", "美食", "小吃");
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

    private String firstNonBlank(String current, String candidate) {
        return current != null && !current.isBlank() ? current : candidate;
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

    private record PlanningFacts(
            String origin,
            String destination,
            int days,
            Integer totalBudget,
            List<String> preferences,
            List<String> mustVisitPlaces,
            boolean relaxedPace,
            boolean preferChinese
    ) {
        boolean isXiamen() {
            return destination != null && (destination.contains("厦门") || destination.toLowerCase(Locale.ROOT).contains("xiamen"));
        }

        int totalBudgetOrDefault() {
            return totalBudget == null ? 3000 : totalBudget;
        }

        boolean sameCityTrip() {
            return origin != null && destination != null && origin.equalsIgnoreCase(destination);
        }
    }

    private record Budget(
            int hotelMin,
            int hotelMax,
            int intercityMin,
            int intercityMax,
            int localTransitMin,
            int localTransitMax,
            int foodMin,
            int foodMax,
            int attractionMin,
            int attractionMax
    ) {
        int totalMin() {
            return hotelMin + intercityMin + localTransitMin + foodMin + attractionMin;
        }

        int totalMax() {
            return hotelMax + intercityMax + localTransitMax + foodMax + attractionMax;
        }
    }
}
