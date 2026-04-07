package com.travalagent.infrastructure.repository;

import com.travalagent.domain.model.valobj.TravelKnowledgeRetrievalResult;
import com.travalagent.domain.model.valobj.TravelKnowledgeSelection;
import com.travalagent.domain.model.valobj.TravelKnowledgeSnippet;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class TravelKnowledgeRetrievalSupport {

    private static final Map<String, List<String>> TOPIC_KEYWORDS = new LinkedHashMap<>();
    private static final Map<String, List<String>> TRIP_STYLE_KEYWORDS = new LinkedHashMap<>();
    private static final List<String> TOPIC_PRIORITY = List.of("scenic", "food", "hotel", "transit", "activity", "nightlife");
    private static final Map<String, Integer> TOPIC_TARGETS = Map.of(
            "scenic", 2,
            "food", 1,
            "hotel", 1,
            "transit", 1,
            "activity", 1,
            "nightlife", 1
    );
    private static final List<String> HOTEL_AREA_HINTS = List.of(
            "where to stay", "best area", "best areas", "good base", "base yourself", "stay in",
            "district", "districts", "neighborhood", "neighbourhood", "area", "areas",
            "west lake", "old town", "downtown", "city center", "city centre", "close to", "near",
            "walking distance", "convenient for", "good for", "recommended area"
    );
    private static final List<String> HOTEL_AREA_GUIDANCE_HINTS = List.of(
            "where to stay", "best area", "best areas", "good base", "base yourself", "stay in",
            "district", "districts", "neighborhood", "neighbourhood", "recommended area"
    );
    private static final List<String> TRANSIT_ARRIVAL_HINTS = List.of(
            "airport", "airports", "railway station", "train station", "south station", "north station",
            "east station", "west station", "ferry terminal", "arrive", "arrival", "from the airport",
            "from airport", "from the railway", "from railway", "from the train station"
    );
    private static final List<String> TRANSIT_HUB_HINTS = List.of(
            "terminal", "wharf", "port", "interchange", "transfer", "hub", "bus station",
            "railway station", "ferry", "metro station", "subway station"
    );
    private static final List<String> TRANSIT_ROUTE_HINTS = List.of(
            "line", "route", "north-south", "west-east", "district", "center", "centre",
            "linking", "passing through", "connect", "bus", "metro", "subway"
    );
    private static final List<String> FOOD_CLUSTER_HINTS = List.of(
            "cluster", "street", "lane", "night market", "food court", "quarter", "around", "near", "district"
    );

    static {
        TOPIC_KEYWORDS.put("food", List.of("food", "eat", "restaurant", "snack", "cafe", "coffee", "night market", "美食", "小吃", "餐厅", "咖啡"));
        TOPIC_KEYWORDS.put("hotel", List.of("hotel", "stay", "hostel", "guesthouse", "guest house", "accommodation", "where to stay", "住宿", "酒店", "民宿", "住哪里"));
        TOPIC_KEYWORDS.put("nightlife", List.of("bar", "pub", "nightlife", "drink", "night view", "club", "酒吧", "夜生活", "夜景", "喝酒"));
        TOPIC_KEYWORDS.put("transit", List.of("metro", "subway", "transit", "station", "airport", "ferry", "how to get around", "交通", "地铁", "机场", "码头", "怎么去"));
        TOPIC_KEYWORDS.put("activity", List.of("activity", "do", "hike", "ski", "show", "hot spring", "golf", "玩法", "体验", "徒步", "演出", "温泉"));
        TOPIC_KEYWORDS.put("scenic", List.of("see", "sight", "attraction", "museum", "temple", "park", "lake", "old town", "景点", "博物馆", "寺", "公园", "古镇"));

        TRIP_STYLE_KEYWORDS.put("relaxed", List.of("relaxed", "slow pace", "easy pace", "leisurely", "轻松", "慢节奏", "休闲"));
        TRIP_STYLE_KEYWORDS.put("family", List.of("family", "kids", "children", "child-friendly", "亲子", "家庭", "小朋友"));
        TRIP_STYLE_KEYWORDS.put("nightlife", List.of("nightlife", "bar", "pub", "late night", "夜生活", "酒吧", "夜景"));
        TRIP_STYLE_KEYWORDS.put("museum", List.of("museum", "gallery", "history museum", "博物馆", "美术馆", "展览"));
        TRIP_STYLE_KEYWORDS.put("shopping", List.of("shopping", "mall", "market", "walk street", "购物", "商场", "步行街"));
        TRIP_STYLE_KEYWORDS.put("foodie", List.of("foodie", "food", "eat", "restaurant", "snack", "美食", "小吃", "吃"));
        TRIP_STYLE_KEYWORDS.put("heritage", List.of("heritage", "historic", "old town", "temple", "古迹", "历史", "寺", "古镇"));
        TRIP_STYLE_KEYWORDS.put("outdoors", List.of("outdoors", "hike", "park", "lake", "mountain", "户外", "徒步", "公园", "湖", "山"));
        TRIP_STYLE_KEYWORDS.put("budget", List.of("budget", "cheap", "affordable", "hostel", "low cost", "预算", "便宜", "实惠"));
    }

    private TravelKnowledgeRetrievalSupport() {
    }

    static RetrievalPlan plan(String destination, List<String> preferences, String query) {
        String normalizedDestination = normalize(destination);
        Set<String> inferredTopics = inferTopics(preferences, query);
        Set<String> inferredTripStyles = inferTripStyles(preferences, query);
        String combinedQuery = combinedQuery(destination, preferences, query);
        Filter.Expression filterExpression = buildFilterExpression(normalizedDestination, inferredTopics);
        return new RetrievalPlan(normalizedDestination, combinedQuery, List.copyOf(inferredTopics), List.copyOf(inferredTripStyles), filterExpression);
    }

    static TravelKnowledgeRetrievalResult emptyResult(String destination, RetrievalPlan plan, String retrievalSource) {
        return TravelKnowledgeRetrievalResult.empty(
                destination,
                plan == null ? List.of() : plan.inferredTopics(),
                plan == null ? List.of() : plan.inferredTripStyles(),
                retrievalSource
        );
    }

    static TravelKnowledgeSnippet enrichSnippet(TravelKnowledgeSnippet snippet) {
        if (snippet == null) {
            return null;
        }
        String schemaSubtype = (snippet.schemaSubtype() == null || snippet.schemaSubtype().isBlank())
                ? inferSchemaSubtype(snippet.topic(), snippet.title(), snippet.content(), snippet.tags())
                : snippet.schemaSubtype();
        Integer qualityScore = snippet.qualityScore() == null || snippet.qualityScore() <= 0
                ? inferQualityScore(snippet.topic(), snippet.title(), snippet.content(), snippet.tags(), schemaSubtype)
                : snippet.qualityScore();
        List<String> cityAliases = dedupeValues(snippet.cityAliases(), snippet.city());
        List<String> tripStyleTags = snippet.tripStyleTags() == null || snippet.tripStyleTags().isEmpty()
                ? inferTripStyleTags(snippet.topic(), snippet.title(), snippet.content(), snippet.tags(), schemaSubtype)
                : dedupeValues(snippet.tripStyleTags());
        return new TravelKnowledgeSnippet(
                snippet.city(),
                snippet.topic(),
                snippet.title(),
                snippet.content(),
                snippet.tags(),
                snippet.source(),
                schemaSubtype,
                qualityScore,
                cityAliases,
                tripStyleTags
        );
    }

    static int plannerPreferenceScore(TravelKnowledgeSnippet snippet, RetrievalPlan plan) {
        TravelKnowledgeSnippet enriched = enrichSnippet(snippet);
        String topic = normalize(enriched.topic());
        String subtype = normalize(enriched.schemaSubtype());
        String searchable = searchableText(enriched);
        Set<String> terms = termsOf(plan.combinedQuery());
        int score = Math.min(enriched.qualityScore() == null ? 0 : enriched.qualityScore(), 40);

        if ("hotel".equals(topic)) {
            score += "hotel_area".equals(subtype) ? 28 : 4;
        }
        if ("transit".equals(topic)) {
            score += switch (subtype) {
                case "transit_arrival" -> 18;
                case "transit_hub" -> 16;
                case "transit_district" -> 12;
                default -> 4;
            };
        }
        if ("food".equals(topic) && containsAny(searchable, FOOD_CLUSTER_HINTS)) {
            score += 10;
        }
        if (containsAny(searchable, HOTEL_AREA_HINTS)) {
            score += 8;
        }
        if ("transit".equals(topic) && containsAny(searchable, TRANSIT_ROUTE_HINTS)) {
            score += 8;
        }

        for (String term : terms) {
            if (term.isBlank()) {
                continue;
            }
            if (searchable.contains(term)) {
                score += 2;
            }
        }

        if (containsAny(normalize(plan.combinedQuery()), TOPIC_KEYWORDS.getOrDefault("hotel", List.of())) && "hotel_area".equals(subtype)) {
            score += 10;
        }
        if (containsAny(normalize(plan.combinedQuery()), TRANSIT_ARRIVAL_HINTS) && "transit_arrival".equals(subtype)) {
            score += 8;
        }
        if (containsAny(normalize(plan.combinedQuery()), List.of("transfer", "interchange", "station", "hub", "换乘", "车站")) && "transit_hub".equals(subtype)) {
            score += 8;
        }

        Set<String> matchedTripStyles = matchedTripStyles(enriched, plan.inferredTripStyles());
        score += matchedTripStyles.size() * 8;
        if (matchedTripStyles.contains("relaxed") && containsAny(searchable, List.of("easy", "slow", "walk", "quiet", "轻松", "休闲"))) {
            score += 4;
        }
        if (matchedTripStyles.contains("family") && containsAny(searchable, List.of("family", "kids", "safe", "child", "亲子", "家庭"))) {
            score += 4;
        }
        if (matchedTripStyles.contains("museum") && "scenic".equals(topic)) {
            score += 3;
        }
        return score;
    }

    static TravelKnowledgeRetrievalResult buildResult(
            String destination,
            RetrievalPlan plan,
            String retrievalSource,
            List<TravelKnowledgeSnippet> rankedSnippets,
            int limit
    ) {
        if (plan == null || limit <= 0 || rankedSnippets == null || rankedSnippets.isEmpty()) {
            return emptyResult(destination, plan, retrievalSource);
        }

        List<TravelKnowledgeSnippet> uniqueSnippets = prioritize(dedupeSnippets(rankedSnippets), plan);
        Map<String, List<TravelKnowledgeSnippet>> snippetsByTopic = new LinkedHashMap<>();
        for (TravelKnowledgeSnippet snippet : uniqueSnippets) {
            snippetsByTopic.computeIfAbsent(normalize(snippet.topic()), ignored -> new ArrayList<>()).add(snippet);
        }

        List<TravelKnowledgeSelection> selections = new ArrayList<>();
        Set<String> selectedKeys = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : topicAllocation(plan.inferredTopics(), limit).entrySet()) {
            List<TravelKnowledgeSnippet> topicSnippets = snippetsByTopic.getOrDefault(entry.getKey(), List.of());
            int added = 0;
            for (TravelKnowledgeSnippet snippet : topicSnippets) {
                if (added >= entry.getValue()) {
                    break;
                }
                if (selectedKeys.add(dedupeKey(snippet))) {
                    selections.add(toSelection(snippet, destination, plan));
                    added++;
                }
            }
        }

        if (selections.size() < limit) {
            for (TravelKnowledgeSnippet snippet : uniqueSnippets) {
                if (selections.size() >= limit) {
                    break;
                }
                if (selectedKeys.add(dedupeKey(snippet))) {
                    selections.add(toSelection(snippet, destination, plan));
                }
            }
        }

        return new TravelKnowledgeRetrievalResult(destination, plan.inferredTopics(), plan.inferredTripStyles(), retrievalSource, selections);
    }

    static Set<String> inferTopics(List<String> preferences, String query) {
        Set<String> topics = new LinkedHashSet<>();
        String normalizedQuery = normalize(query);
        for (Map.Entry<String, List<String>> entry : TOPIC_KEYWORDS.entrySet()) {
            if (containsAny(normalizedQuery, entry.getValue())) {
                topics.add(entry.getKey());
            }
        }
        if (preferences != null) {
            for (String preference : preferences) {
                String normalizedPreference = normalize(preference);
                for (Map.Entry<String, List<String>> entry : TOPIC_KEYWORDS.entrySet()) {
                    if (containsAny(normalizedPreference, entry.getValue())) {
                        topics.add(entry.getKey());
                    }
                }
            }
        }
        if (topics.isEmpty()) {
            topics.add("scenic");
            topics.add("food");
        }
        return topics;
    }

    static Set<String> inferTripStyles(List<String> preferences, String query) {
        Set<String> styles = new LinkedHashSet<>();
        collectTripStyles(styles, normalize(query));
        if (preferences != null) {
            for (String preference : preferences) {
                collectTripStyles(styles, normalize(preference));
            }
        }
        return styles;
    }

    static String combinedQuery(String destination, List<String> preferences, String query) {
        StringBuilder builder = new StringBuilder();
        append(builder, destination);
        append(builder, query);
        if (preferences != null) {
            preferences.forEach(value -> append(builder, value));
        }
        return builder.toString().trim();
    }

    static boolean matchesDestination(TravelKnowledgeSnippet snippet, String normalizedDestination) {
        if (snippet == null) {
            return false;
        }
        if (normalizedDestination == null || normalizedDestination.isBlank()) {
            return true;
        }
        if (matchesDestination(snippet.city(), normalizedDestination)) {
            return true;
        }
        for (String alias : snippet.cityAliases()) {
            if (matchesDestination(alias, normalizedDestination)) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesDestination(String snippetCity, String normalizedDestination) {
        String city = cityComparable(snippetCity);
        String destination = cityComparable(normalizedDestination);
        return destination.isBlank() || city.equals(destination);
    }

    static boolean matchesTopics(String snippetTopic, List<String> inferredTopics) {
        if (inferredTopics == null || inferredTopics.isEmpty()) {
            return true;
        }
        String topic = normalize(snippetTopic);
        return inferredTopics.stream().map(TravelKnowledgeRetrievalSupport::normalize).anyMatch(topic::equals);
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    static String cityComparable(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return normalized;
        }
        return normalized
                .replace("市", "")
                .replace(" city", "")
                .replace("shi", "")
                .trim();
    }

    static String dedupeKey(TravelKnowledgeSnippet snippet) {
        TravelKnowledgeSnippet enriched = enrichSnippet(snippet);
        return normalize(enriched.city()) + "::" + normalize(enriched.topic()) + "::" + normalize(enriched.title());
    }

    static String inferSchemaSubtype(String topic, String title, String content, List<String> tags) {
        String normalizedTopic = normalize(topic);
        String searchable = searchableText(title, content, tags);
        return switch (normalizedTopic) {
            case "hotel" -> inferHotelSubtype(searchable);
            case "transit" -> inferTransitSubtype(searchable);
            default -> normalizedTopic;
        };
    }

    private static String inferHotelSubtype(String searchable) {
        boolean hasAreaGuidance = containsAny(searchable, HOTEL_AREA_GUIDANCE_HINTS);
        return hasAreaGuidance ? "hotel_area" : "hotel_listing";
    }

    private static String inferTransitSubtype(String searchable) {
        boolean hasExplicitArrivalAdvice = containsAny(searchable, List.of(
                "arrive", "arrival", "from airport", "from the airport", "from railway",
                "from the railway", "from the train station", "from the station", "to downtown", "to the city"
        ));
        boolean hasArrivalNode = containsAny(searchable, List.of(
                "airport", "railway station", "train station", "south station", "north station", "east station", "west station", "ferry terminal"
        ));
        if (hasExplicitArrivalAdvice || (hasArrivalNode && containsAny(searchable, List.of("take", "get off", "depart", "terminates")))) {
            return "transit_arrival";
        }
        if (containsAny(searchable, TRANSIT_HUB_HINTS)) {
            return "transit_hub";
        }
        return "transit_district";
    }

    private static int inferQualityScore(String topic, String title, String content, List<String> tags, String schemaSubtype) {
        String normalizedTopic = normalize(topic);
        String searchable = searchableText(title, content, tags);
        int score = 0;
        score += Math.min(content == null ? 0 : normalize(content).length() / 40, 12);
        score += Math.min(tags == null ? 0 : tags.size(), 6);
        score += switch (normalizedTopic) {
            case "scenic" -> 8;
            case "activity" -> 6;
            case "food" -> 5;
            case "hotel" -> 5;
            default -> 3;
        };
        if ("hotel_area".equals(schemaSubtype)) {
            score += 10;
        }
        if ("transit_arrival".equals(schemaSubtype) || "transit_hub".equals(schemaSubtype)) {
            score += 8;
        }
        if (containsAny(searchable, HOTEL_AREA_HINTS)) {
            score += 6;
        }
        if (containsAny(searchable, TRANSIT_ROUTE_HINTS)) {
            score += 5;
        }
        if (containsAny(searchable, FOOD_CLUSTER_HINTS)) {
            score += 4;
        }
        if (containsAny(searchable, List.of("best", "worth", "view", "historic", "walking", "local", "popular", "traditional"))) {
            score += 3;
        }
        return score;
    }

    static List<String> inferTripStyleTags(String topic, String title, String content, List<String> tags, String schemaSubtype) {
        Set<String> styles = new LinkedHashSet<>();
        String searchable = searchableText(title, content, tags) + " " + normalize(schemaSubtype);
        collectTripStyles(styles, searchable);
        String normalizedTopic = normalize(topic);
        if ("nightlife".equals(normalizedTopic)) {
            styles.add("nightlife");
        }
        if ("food".equals(normalizedTopic)) {
            styles.add("foodie");
        }
        if ("hotel".equals(normalizedTopic) && "hotel_listing".equals(normalize(schemaSubtype))) {
            if (containsAny(searchable, List.of("hostel", "budget", "cheap", "affordable", "青年旅舍"))) {
                styles.add("budget");
            }
        }
        if ("scenic".equals(normalizedTopic) && containsAny(searchable, List.of("museum", "history", "gallery", "博物馆"))) {
            styles.add("museum");
        }
        return List.copyOf(styles);
    }

    private static void collectTripStyles(Set<String> styles, String searchable) {
        for (Map.Entry<String, List<String>> entry : TRIP_STYLE_KEYWORDS.entrySet()) {
            if (containsAny(searchable, entry.getValue())) {
                styles.add(entry.getKey());
            }
        }
    }

    private static Set<String> matchedTripStyles(TravelKnowledgeSnippet snippet, List<String> inferredTripStyles) {
        if (inferredTripStyles == null || inferredTripStyles.isEmpty()) {
            return Set.of();
        }
        Set<String> snippetStyles = new LinkedHashSet<>(normalizeValues(snippet.tripStyleTags()));
        Set<String> matched = new LinkedHashSet<>();
        for (String style : normalizeValues(inferredTripStyles)) {
            if (snippetStyles.contains(style)) {
                matched.add(style);
            }
        }
        return Set.copyOf(matched);
    }

    private static TravelKnowledgeSelection toSelection(TravelKnowledgeSnippet snippet, String destination, RetrievalPlan plan) {
        TravelKnowledgeSnippet enriched = enrichSnippet(snippet);
        String matchedTopic = matchesTopics(enriched.topic(), plan.inferredTopics()) ? enriched.topic() : null;
        String matchedCity = destination == null || destination.isBlank() ? enriched.city() : destination;
        return new TravelKnowledgeSelection(
                enriched.city(),
                enriched.topic(),
                enriched.title(),
                enriched.content(),
                enriched.tags(),
                enriched.source(),
                enriched.schemaSubtype(),
                enriched.qualityScore(),
                List.copyOf(matchedTripStyles(enriched, plan.inferredTripStyles())),
                matchedCity,
                matchedTopic
        );
    }

    private static List<TravelKnowledgeSnippet> prioritize(List<TravelKnowledgeSnippet> snippets, RetrievalPlan plan) {
        List<ScoredSnippet> scored = new ArrayList<>();
        int order = 0;
        for (TravelKnowledgeSnippet snippet : snippets) {
            scored.add(new ScoredSnippet(enrichSnippet(snippet), plannerPreferenceScore(snippet, plan), order++));
        }
        scored.sort((left, right) -> {
            int scoreComparison = Integer.compare(right.score(), left.score());
            if (scoreComparison != 0) {
                return scoreComparison;
            }
            return Integer.compare(left.order(), right.order());
        });
        return scored.stream().map(ScoredSnippet::snippet).toList();
    }

    private static List<TravelKnowledgeSnippet> dedupeSnippets(List<TravelKnowledgeSnippet> rankedSnippets) {
        Map<String, TravelKnowledgeSnippet> unique = new LinkedHashMap<>();
        for (TravelKnowledgeSnippet snippet : rankedSnippets) {
            TravelKnowledgeSnippet enriched = enrichSnippet(snippet);
            unique.putIfAbsent(dedupeKey(enriched), enriched);
        }
        return List.copyOf(unique.values());
    }

    private static Map<String, Integer> topicAllocation(List<String> inferredTopics, int limit) {
        LinkedHashMap<String, Integer> allocation = new LinkedHashMap<>();
        if (limit <= 0) {
            return allocation;
        }

        List<String> orderedTopics = orderedTopics(inferredTopics);
        int remaining = limit;
        for (String topic : orderedTopics) {
            if (remaining <= 0) {
                break;
            }
            int target = Math.min(TOPIC_TARGETS.getOrDefault(topic, 1), remaining);
            if (target > 0) {
                allocation.put(topic, target);
                remaining -= target;
            }
        }

        while (remaining > 0 && !orderedTopics.isEmpty()) {
            for (String topic : orderedTopics) {
                allocation.put(topic, allocation.getOrDefault(topic, 0) + 1);
                remaining--;
                if (remaining == 0) {
                    break;
                }
            }
        }
        return allocation;
    }

    private static List<String> orderedTopics(List<String> inferredTopics) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (inferredTopics != null) {
            inferredTopics.stream()
                    .map(TravelKnowledgeRetrievalSupport::normalize)
                    .filter(topic -> !topic.isBlank())
                    .forEach(normalized::add);
        }
        if (normalized.isEmpty()) {
            normalized.add("scenic");
            normalized.add("food");
        }

        List<String> ordered = new ArrayList<>();
        for (String topic : TOPIC_PRIORITY) {
            if (normalized.contains(topic)) {
                ordered.add(topic);
            }
        }
        for (String topic : normalized) {
            if (!ordered.contains(topic)) {
                ordered.add(topic);
            }
        }
        return List.copyOf(ordered);
    }

    private static Set<String> termsOf(String raw) {
        Set<String> terms = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return terms;
        }
        String normalized = normalize(raw);
        if (!normalized.isBlank()) {
            terms.add(normalized);
        }
        for (String token : normalized.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]+")) {
            if (!token.isBlank()) {
                terms.add(token);
            }
        }
        return terms;
    }

    private static boolean containsAny(String value, List<String> candidates) {
        if (value == null || value.isBlank() || candidates == null || candidates.isEmpty()) {
            return false;
        }
        for (String candidate : candidates) {
            if (value.contains(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    private static String searchableText(TravelKnowledgeSnippet snippet) {
        return searchableText(snippet.title(), snippet.content(), snippet.tags()) + " " + normalize(snippet.schemaSubtype()) + " " + String.join(" ", normalizeValues(snippet.tripStyleTags()));
    }

    private static String searchableText(String title, String content, List<String> tags) {
        StringBuilder builder = new StringBuilder();
        append(builder, title);
        append(builder, content);
        if (tags != null) {
            tags.forEach(tag -> append(builder, tag));
        }
        return normalize(builder.toString());
    }

    private static void append(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(value.trim());
    }

    private static Filter.Expression buildFilterExpression(String normalizedDestination, Set<String> inferredTopics) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op destinationOp = normalizedDestination.isBlank() ? null : builder.eq("city", normalizedDestination);
        FilterExpressionBuilder.Op topicOp = inferredTopics == null || inferredTopics.isEmpty()
                ? null
                : builder.in("topic", inferredTopics.toArray());

        if (destinationOp != null && topicOp != null) {
            return builder.and(destinationOp, topicOp).build();
        }
        if (destinationOp != null) {
            return destinationOp.build();
        }
        if (topicOp != null) {
            return topicOp.build();
        }
        return null;
    }

    private static List<String> dedupeValues(List<String> values, String seed) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (seed != null && !seed.isBlank()) {
            unique.add(seed.trim());
        }
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    unique.add(value.trim());
                }
            }
        }
        return List.copyOf(unique);
    }

    private static List<String> dedupeValues(List<String> values) {
        return dedupeValues(values, null);
    }

    private static List<String> normalizeValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            String candidate = normalize(value);
            if (!candidate.isBlank() && !normalized.contains(candidate)) {
                normalized.add(candidate);
            }
        }
        return List.copyOf(normalized);
    }

    record RetrievalPlan(
            String normalizedDestination,
            String combinedQuery,
            List<String> inferredTopics,
            List<String> inferredTripStyles,
            Filter.Expression filterExpression
    ) {
    }

    private record ScoredSnippet(
            TravelKnowledgeSnippet snippet,
            int score,
            int order
    ) {
    }
}
