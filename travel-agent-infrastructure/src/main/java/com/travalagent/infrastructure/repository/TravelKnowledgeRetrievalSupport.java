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
        
        // 增强元数据推断
        List<String> season = snippet.season() == null || snippet.season().isEmpty()
                ? inferSeason(snippet.topic(), snippet.title(), snippet.content(), snippet.tags())
                : snippet.season();
        String budgetLevel = snippet.budgetLevel() == null || snippet.budgetLevel().isBlank()
                ? inferBudgetLevel(snippet.topic(), snippet.title(), snippet.content(), snippet.tags())
                : snippet.budgetLevel();
        String duration = snippet.duration() == null || snippet.duration().isBlank()
                ? inferDuration(snippet.topic(), snippet.title(), snippet.content(), snippet.tags())
                : snippet.duration();
        String bestTime = snippet.bestTime() == null || snippet.bestTime().isBlank()
                ? inferBestTime(snippet.topic(), snippet.title(), snippet.content(), snippet.tags())
                : snippet.bestTime();
        String crowdLevel = snippet.crowdLevel() == null || snippet.crowdLevel().isBlank()
                ? inferCrowdLevel(snippet.topic(), snippet.title(), snippet.content(), snippet.tags())
                : snippet.crowdLevel();
        String location = snippet.location() == null || snippet.location().isBlank()
                ? extractLocation(snippet.title(), snippet.content(), snippet.tags())
                : snippet.location();
        String area = snippet.area() == null || snippet.area().isBlank()
                ? extractArea(snippet.title(), snippet.content(), snippet.tags())
                : snippet.area();
        Double rating = snippet.rating();
        String priceRange = snippet.priceRange() == null || snippet.priceRange().isBlank()
                ? extractPriceRange(snippet.title(), snippet.content(), snippet.tags())
                : snippet.priceRange();
        List<String> facilities = snippet.facilities() == null || snippet.facilities().isEmpty()
                ? extractFacilities(snippet.topic(), snippet.title(), snippet.content(), snippet.tags())
                : snippet.facilities();
        List<String> nearbyPOIs = snippet.nearbyPOIs() == null || snippet.nearbyPOIs().isEmpty()
                ? extractNearbyPOIs(snippet.title(), snippet.content(), snippet.tags())
                : snippet.nearbyPOIs();
        
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
                tripStyleTags,
                season,
                budgetLevel,
                duration,
                bestTime,
                crowdLevel,
                location,
                area,
                rating,
                priceRange,
                facilities,
                nearbyPOIs
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

    // ==================== 增强元数据推断方法 ====================

    /**
     * 推断适用季节
     */
    private static List<String> inferSeason(String topic, String title, String content, List<String> tags) {
        String searchable = searchableText(title, content, tags);
        Set<String> seasons = new LinkedHashSet<>();
        
        if (containsAny(searchable, List.of("spring", "cherry blossom", "樱花", "春季", "春天"))) {
            seasons.add("春");
        }
        if (containsAny(searchable, List.of("summer", "beach", "surf", "夏日", "夏季", "夏天", "海滨"))) {
            seasons.add("夏");
        }
        if (containsAny(searchable, List.of("autumn", "fall", "红叶", "秋季", "秋天", "枫叶"))) {
            seasons.add("秋");
        }
        if (containsAny(searchable, List.of("winter", "ski", "snow", "冬季", "冬天", "滑雪", "雪"))) {
            seasons.add("冬");
        }
        
        // 如果没有特定季节，默认为全年
        if (seasons.isEmpty()) {
            if (containsAny(searchable, List.of("year-round", "all season", "四季", "全年"))) {
                seasons.addAll(List.of("春", "夏", "秋", "冬"));
            } else {
                // 默认全年适宜
                seasons.addAll(List.of("春", "夏", "秋", "冬"));
            }
        }
        
        return List.copyOf(seasons);
    }

    /**
     * 推断预算等级
     */
    private static String inferBudgetLevel(String topic, String title, String content, List<String> tags) {
        String searchable = searchableText(title, content, tags);
        
        if (containsAny(searchable, List.of("free", "免费", "免票", "无门票"))) {
            return "free";
        }
        if (containsAny(searchable, List.of("budget", "cheap", "affordable", "便宜", "实惠", "经济", "穷游"))) {
            return "budget";
        }
        if (containsAny(searchable, List.of("luxury", "premium", "高端", "豪华", "五星级"))) {
            return "luxury";
        }
        if (containsAny(searchable, List.of("expensive", "pricey", "昂贵", "高端"))) {
            return "premium";
        }
        
        // 默认为中等预算
        return "moderate";
    }

    /**
     * 推断建议时长
     */
    private static String inferDuration(String topic, String title, String content, List<String> tags) {
        String searchable = searchableText(title, content, tags);
        
        if (containsAny(searchable, List.of("1-2 hours", "1-2小时", "一小时", "两小时"))) {
            return "1-2小时";
        }
        if (containsAny(searchable, List.of("half day", "半天", "3-4小时", "4小时"))) {
            return "半天";
        }
        if (containsAny(searchable, List.of("full day", "全天", "一整", "8小时", "一天"))) {
            return "全天";
        }
        if (containsAny(searchable, List.of("2 days", "两天", "过夜", "多日"))) {
            return "2天+";
        }
        
        // 根据主题默认时长
        return switch (normalize(topic)) {
            case "scenic" -> "半天";
            case "food" -> "1-2小时";
            case "hotel" -> "全天";
            case "activity" -> "半天";
            default -> "半天";
        };
    }

    /**
     * 推断最佳时间
     */
    private static String inferBestTime(String topic, String title, String content, List<String> tags) {
        String searchable = searchableText(title, content, tags);
        
        if (containsAny(searchable, List.of("morning", "early", "早晨", "早上", "上午"))) {
            return "早晨";
        }
        if (containsAny(searchable, List.of("afternoon", "下午"))) {
            return "下午";
        }
        if (containsAny(searchable, List.of("evening", "sunset", "傍晚", "黄昏", "日落"))) {
            return "傍晚";
        }
        if (containsAny(searchable, List.of("night", "evening", "夜晚", "晚上", "夜景"))) {
            return "夜晚";
        }
        
        // 根据主题默认时间
        return switch (normalize(topic)) {
            case "scenic" -> "早晨";
            case "food" -> "下午";
            case "nightlife" -> "夜晚";
            default -> "下午";
        };
    }

    /**
     * 推断拥挤度
     */
    private static String inferCrowdLevel(String topic, String title, String content, List<String> tags) {
        String searchable = searchableText(title, content, tags);
        
        if (containsAny(searchable, List.of("crowded", "busy", "popular", "拥挤", "热门", "人多", "排队"))) {
            return "高";
        }
        if (containsAny(searchable, List.of("quiet", "peaceful", "hidden", "安静", "小众", "人少", "清静"))) {
            return "低";
        }
        
        // 根据主题默认拥挤度
        return switch (normalize(topic)) {
            case "scenic" -> "高";
            case "food" -> "中";
            case "hotel" -> "中";
            case "nightlife" -> "高";
            default -> "中";
        };
    }

    /**
     * 提取具体位置
     */
    private static String extractLocation(String title, String content, List<String> tags) {
        String searchable = searchableText(title, content, tags);
        
        // 查找地址模式
        if (searchable.contains("address") || searchable.contains("地址")) {
            String[] lines = searchable.split("\\n");
            for (String line : lines) {
                if (line.contains("地址") || line.contains("address")) {
                    return line.trim();
                }
            }
        }
        
        return null;
    }

    /**
     * 提取所在区域
     */
    private static String extractArea(String title, String content, List<String> tags) {
        String searchable = searchableText(title, content, tags);
        
        // 查找区域关键词
        List<String> areaKeywords = List.of(
            "district", "area", "zone", "quarter", "district",
            "区", "区域", "街区", "商圈", "景区"
        );
        
        for (String keyword : areaKeywords) {
            int index = searchable.indexOf(keyword);
            if (index > 0) {
                // 提取关键词前面的内容作为区域名
                int start = Math.max(0, index - 10);
                String area = searchable.substring(start, index).trim();
                if (!area.isBlank() && area.length() > 1) {
                    return area;
                }
            }
        }
        
        return null;
    }

    /**
     * 提取价格范围
     */
    private static String extractPriceRange(String title, String content, List<String> tags) {
        String searchable = searchableText(title, content, tags);
        
        // 匹配价格模式：¥100-200, ￥50-100, 100-200元
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "[¥￥]?(\\d+)\\s*[-~至到]\\s*[¥￥]?(\\d+)\\s*元?"
        );
        java.util.regex.Matcher matcher = pattern.matcher(searchable);
        
        if (matcher.find()) {
            String min = matcher.group(1);
            String max = matcher.group(2);
            return "¥" + min + "-" + max;
        }
        
        // 匹配单一价格
        pattern = java.util.regex.Pattern.compile("[¥￥]?(\\d+)\\s*元?");
        matcher = pattern.matcher(searchable);
        if (matcher.find()) {
            return "¥" + matcher.group(1);
        }
        
        return null;
    }

    /**
     * 提取设施标签
     */
    private static List<String> extractFacilities(String topic, String title, String content, List<String> tags) {
        String searchable = searchableText(title, content, tags);
        Set<String> facilities = new LinkedHashSet<>();
        
        // 常见设施关键词
        Map<String, List<String>> facilityMap = Map.of(
            "WiFi", List.of("wifi", "无线网络", "网络"),
            "停车场", List.of("parking", "停车场", "停车"),
            "早餐", List.of("breakfast", "早餐"),
            "泳池", List.of("pool", "游泳池", "泳池"),
            "健身房", List.of("gym", "fitness", "健身房"),
            "SPA", List.of("spa", "水疗", "按摩"),
            "餐厅", List.of("restaurant", "餐厅"),
            "电梯", List.of("elevator", "电梯"),
            "无障碍", List.of("wheelchair", "accessible", "无障碍")
        );
        
        for (Map.Entry<String, List<String>> entry : facilityMap.entrySet()) {
            if (containsAny(searchable, entry.getValue())) {
                facilities.add(entry.getKey());
            }
        }
        
        return List.copyOf(facilities);
    }

    /**
     * 提取周边兴趣点
     */
    private static List<String> extractNearbyPOIs(String title, String content, List<String> tags) {
        String searchable = searchableText(title, content, tags);
        Set<String> pois = new LinkedHashSet<>();
        
        // 查找"附近"、"周边"、"nearby"、"close to"等关键词
        List<String> nearbyPatterns = List.of(
            "near", "nearby", "close to", "around", "附近", "周边", "旁边", "步行"
        );
        
        for (String pattern : nearbyPatterns) {
            int index = searchable.indexOf(pattern);
            if (index > 0) {
                // 提取后面的内容作为周边POI
                int end = Math.min(searchable.length(), index + 50);
                String nearby = searchable.substring(index, end).trim();
                if (!nearby.isBlank()) {
                    pois.add(nearby);
                }
            }
        }
        
        return pois.isEmpty() ? List.of() : List.copyOf(pois);
    }
}
