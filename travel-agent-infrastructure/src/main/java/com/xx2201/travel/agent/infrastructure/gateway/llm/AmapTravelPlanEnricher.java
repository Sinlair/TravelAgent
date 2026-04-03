package com.xx2201.travel.agent.infrastructure.gateway.llm;

import com.xx2201.travel.agent.domain.event.TimelinePublisher;
import com.xx2201.travel.agent.domain.model.entity.TimelineEvent;
import com.xx2201.travel.agent.domain.model.entity.TravelCostBreakdown;
import com.xx2201.travel.agent.domain.model.entity.TravelHotelRecommendation;
import com.xx2201.travel.agent.domain.model.entity.TravelPlan;
import com.xx2201.travel.agent.domain.model.entity.TravelPlanDay;
import com.xx2201.travel.agent.domain.model.entity.TravelPlanStop;
import com.xx2201.travel.agent.domain.model.entity.TravelPoiMatch;
import com.xx2201.travel.agent.domain.model.entity.TravelTransitLeg;
import com.xx2201.travel.agent.domain.model.entity.TravelTransitStep;
import com.xx2201.travel.agent.domain.model.valobj.AgentExecutionContext;
import com.xx2201.travel.agent.domain.model.valobj.ExecutionStage;
import com.xx2201.travel.agent.domain.model.valobj.GeoLocation;
import com.xx2201.travel.agent.domain.model.valobj.PlaceSearchQuery;
import com.xx2201.travel.agent.domain.model.valobj.PlaceSuggestion;
import com.xx2201.travel.agent.domain.model.valobj.TransitRoutePlan;
import com.xx2201.travel.agent.domain.model.valobj.TransitRouteQuery;
import com.xx2201.travel.agent.infrastructure.gateway.tool.AmapMcpGateway;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class AmapTravelPlanEnricher {

    private final AmapMcpGateway amapMcpGateway;
    private final TimelinePublisher timelinePublisher;

    public AmapTravelPlanEnricher(AmapMcpGateway amapMcpGateway, TimelinePublisher timelinePublisher) {
        this.amapMcpGateway = amapMcpGateway;
        this.timelinePublisher = timelinePublisher;
    }

    public TravelPlan enrich(TravelPlan plan, AgentExecutionContext context) {
        if (plan == null) {
            return null;
        }
        String city = context.taskMemory().destination();
        if (city == null || city.isBlank()) {
            return plan;
        }

        boolean preferChinese = preferChinese(context.userMessage(), city);
        List<TravelPlanDay> enrichedDays = new ArrayList<>();
        for (TravelPlanDay day : plan.days()) {
            List<TravelPlanStop> enrichedStops = new ArrayList<>();
            for (TravelPlanStop stop : day.stops()) {
                enrichedStops.add(enrichStop(stop, city, preferChinese, context.conversationId()));
            }
            enrichedDays.add(new TravelPlanDay(
                    day.dayNumber(),
                    day.theme(),
                    day.startTime(),
                    day.endTime(),
                    day.totalTransitMinutes(),
                    day.totalActivityMinutes(),
                    day.estimatedCost(),
                    enrichedStops,
                    day.returnToHotel()
            ));
        }

        List<String> enrichedHighlights = new ArrayList<>();
        for (String highlight : plan.highlights()) {
            ResolvedPoi resolved = resolvePoi(canonicalKeyword(highlight), null, city, context.conversationId());
            enrichedHighlights.add(resolved == null ? highlight : chooseName(highlight, resolved.suggestion()));
        }

        List<TravelHotelRecommendation> hotels = recommendHotels(plan, enrichedDays, city, preferChinese, context.conversationId());
        List<TravelPlanDay> routedDays = enrichRoutes(enrichedDays, hotels, plan.hotelArea(), city, preferChinese, context.conversationId());

        return new TravelPlan(
                plan.conversationId(),
                plan.title(),
                plan.summary(),
                plan.hotelArea(),
                plan.hotelAreaReason(),
                hotels,
                plan.totalBudget(),
                plan.estimatedTotalMin(),
                plan.estimatedTotalMax(),
                new ArrayList<>(new LinkedHashSet<>(enrichedHighlights)),
                plan.budget(),
                plan.checks(),
                routedDays,
                Instant.now()
        );
    }

    // poi resolution

    private TravelPlanStop enrichStop(TravelPlanStop stop, String city, boolean preferChinese, String conversationId) {
        String keyword = canonicalKeyword(stop.name());
        if (keyword == null || keyword.isBlank()) {
            return stop;
        }
        ResolvedPoi resolved = resolvePoi(keyword, stop.area(), city, conversationId);
        if (resolved == null) {
            return stop;
        }
        PlaceSuggestion suggestion = resolved.suggestion();
        String[] coordinate = splitLocation(suggestion.location());
        return new TravelPlanStop(
                stop.slot(),
                chooseName(stop.name(), suggestion),
                chooseArea(stop.area(), suggestion),
                composeAddress(city, suggestion),
                coordinate[0],
                coordinate[1],
                stop.startTime(),
                stop.endTime(),
                stop.durationMinutes(),
                stop.transitMinutesFromPrevious(),
                stop.estimatedCost(),
                stop.openTime(),
                stop.closeTime(),
                appendVerification(stop.rationale(), suggestion, preferChinese),
                stop.costBreakdown(),
                new TravelPoiMatch(
                        keyword,
                        chooseName(stop.name(), suggestion),
                        chooseArea(stop.area(), suggestion),
                        composeAddress(city, suggestion),
                        suggestion.adCode(),
                        coordinate[0],
                        coordinate[1],
                        resolved.score(),
                        resolved.candidateNames(),
                        "MCP.amap_input_tips"
                ),
                stop.routeFromPrevious()
        );
    }

    private ResolvedPoi resolvePoi(String keyword, String currentArea, String city, String conversationId) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        timelinePublisher.publish(TimelineEvent.of(
                conversationId,
                ExecutionStage.CALL_TOOL,
                "Resolve POI candidates with Amap",
                Map.of("keyword", keyword, "city", city)
        ));
        List<PlaceSuggestion> suggestions = amapMcpGateway.inputTips(new PlaceSearchQuery(keyword, city, null, null, true, "poi"), conversationId);
        List<PlaceSuggestion> actualCandidates = suggestions.stream()
                .filter(Objects::nonNull)
                .filter(suggestion -> suggestion.name() != null && !suggestion.name().isBlank())
                .filter(suggestion -> suggestion.address() == null || !suggestion.address().contains("No Amap result"))
                .toList();
        if (actualCandidates.isEmpty()) {
            return null;
        }
        List<String> candidateNames = actualCandidates.stream()
                .map(PlaceSuggestion::name)
                .distinct()
                .limit(5)
                .toList();
        return actualCandidates.stream()
                .map(suggestion -> new ResolvedPoi(suggestion, scoreCandidate(keyword, currentArea, city, suggestion), candidateNames))
                .max(Comparator.comparingDouble(ResolvedPoi::score))
                .orElse(null);
    }

    private double scoreCandidate(String keyword, String currentArea, String city, PlaceSuggestion suggestion) {
        String query = normalize(keyword);
        String name = normalize(suggestion.name());
        String district = normalize(suggestion.district());
        String address = normalize(suggestion.address());
        double score = 0;
        if (name.equals(query)) {
            score += 80;
        } else if (name.contains(query) || query.contains(name)) {
            score += 60;
        }
        if (!currentAreaIsBlank(currentArea)) {
            String area = normalize(currentArea);
            if (district.contains(area) || address.contains(area) || area.contains(district)) {
                score += 18;
            }
        }
        if (city != null && !city.isBlank()) {
            String normalizedCity = normalize(city);
            if (district.contains(normalizedCity) || address.contains(normalizedCity)) {
                score += 10;
            }
        }
        if (suggestion.location() != null && !suggestion.location().isBlank()) {
            score += 6;
        }
        if (suggestion.type() != null && !suggestion.type().isBlank()) {
            score += 3;
        }
        return score;
    }

    // hotels

    private List<TravelHotelRecommendation> recommendHotels(TravelPlan plan, List<TravelPlanDay> days, String city, boolean preferChinese, String conversationId) {
        GeoLocation hotelAreaGeo = resolveHotelArea(plan.hotelArea(), city, conversationId);
        String location = hotelAreaGeo.longitude() == null || hotelAreaGeo.longitude().isBlank()
                ? null
                : hotelAreaGeo.longitude() + "," + hotelAreaGeo.latitude();

        timelinePublisher.publish(TimelineEvent.of(
                conversationId,
                ExecutionStage.CALL_TOOL,
                "Recommend hotels with Amap",
                Map.of("city", city, "area", plan.hotelArea())
        ));

        List<PlaceSuggestion> collected = new ArrayList<>();
        for (String keyword : hotelKeywords(plan.hotelArea(), city, preferChinese)) {
            List<PlaceSuggestion> suggestions = amapMcpGateway.inputTips(new PlaceSearchQuery(keyword, city, "住宿服务", location, true, "poi"), conversationId);
            for (PlaceSuggestion suggestion : suggestions) {
                if (suggestion == null || suggestion.name() == null || suggestion.name().isBlank()) {
                    continue;
                }
                if (suggestion.address() != null && suggestion.address().contains("No Amap result")) {
                    continue;
                }
                collected.add(suggestion);
            }
            if (collected.size() >= 5) {
                break;
            }
        }

        Map<String, PlaceSuggestion> unique = new LinkedHashMap<>();
        for (PlaceSuggestion suggestion : collected) {
            String key = (suggestion.id() == null || suggestion.id().isBlank())
                    ? suggestion.name() + "|" + suggestion.address()
                    : suggestion.id();
            unique.putIfAbsent(key, suggestion);
        }

        int nights = Math.max(days.size() - 1, 1);
        int hotelMin = plan.budget().stream().filter(item -> "Hotel".equals(item.category())).findFirst().map(item -> item.minAmount() / nights).orElse(380);
        int hotelMax = plan.budget().stream().filter(item -> "Hotel".equals(item.category())).findFirst().map(item -> item.maxAmount() / nights).orElse(680);

        List<TravelHotelRecommendation> results = unique.values().stream()
                .limit(3)
                .map(suggestion -> {
                    String[] coordinate = splitLocation(suggestion.location());
                    return new TravelHotelRecommendation(
                            suggestion.name(),
                            chooseArea(plan.hotelArea(), suggestion),
                            composeAddress(city, suggestion),
                            hotelMin,
                            hotelMax,
                            hotelReasonForSuggestion(suggestion, preferChinese, resultsIndex(unique.values().stream().toList(), suggestion)),
                            coordinate[0],
                            coordinate[1],
                            "MCP.amap_input_tips"
                    );
                })
                .toList();

        if (!results.isEmpty()) {
            return results;
        }

        return List.of(new TravelHotelRecommendation(
                preferChinese ? plan.hotelArea() + "优先酒店位" : plan.hotelArea() + " hotel base",
                plan.hotelArea(),
                hotelAreaGeo.address(),
                hotelMin,
                hotelMax,
                preferChinese ? "高德酒店候选不足时，先按推荐住宿区保留落脚点。" : "Fallback hotel base when Amap cannot return a concrete hotel candidate.",
                hotelAreaGeo.longitude(),
                hotelAreaGeo.latitude(),
                "MCP.amap_geocode"
        ));
    }

    private GeoLocation resolveHotelArea(String hotelArea, String city, String conversationId) {
        timelinePublisher.publish(TimelineEvent.of(
                conversationId,
                ExecutionStage.CALL_TOOL,
                "Resolve hotel district center with Amap",
                Map.of("hotelArea", hotelArea, "city", city)
        ));
        return amapMcpGateway.geocode(city + hotelArea, conversationId);
    }

    private List<String> hotelKeywords(String hotelArea, String city, boolean preferChinese) {
        String canonicalArea = canonicalKeyword(hotelArea);
        if (preferChinese) {
            return List.of(canonicalArea + " 酒店", city + canonicalArea + " 酒店", canonicalArea + " 地铁站 酒店");
        }
        return List.of(canonicalArea + " hotel", city + " " + canonicalArea + " hotel", canonicalArea + " metro hotel");
    }

    private int resultsIndex(List<PlaceSuggestion> values, PlaceSuggestion suggestion) {
        return Math.max(0, values.indexOf(suggestion));
    }

    private String hotelReasonForSuggestion(PlaceSuggestion suggestion, boolean preferChinese, int index) {
        if (preferChinese) {
            return switch (index) {
                case 0 -> "优先推荐，通常更适合作为本次行程的主住点。";
                case 1 -> "作为备选，适合在主酒店满房或价格波动时替换。";
                default -> "作为第二备选，适合拉开价格和位置的选择空间。";
            };
        }
        return switch (index) {
            case 0 -> "Primary pick for this itinerary.";
            case 1 -> "Good backup when the primary hotel is full or spikes in price.";
            default -> "Secondary backup to widen the price and location options.";
        };
    }

    // routing

    private List<TravelPlanDay> enrichRoutes(List<TravelPlanDay> days, List<TravelHotelRecommendation> hotels, String hotelArea, String city, boolean preferChinese, String conversationId) {
        List<TravelPlanDay> results = new ArrayList<>();
        LocationRef hotelBase = hotelBase(hotels, hotelArea, city, conversationId);
        for (TravelPlanDay day : days) {
            List<TravelPlanStop> routedStops = new ArrayList<>();
            LocationRef previous = hotelBase;
            int totalTransit = 0;
            int totalCost = 0;

            for (TravelPlanStop stop : day.stops()) {
                TravelTransitLeg route = routeBetween(previous, stop, city, preferChinese, conversationId);
                TravelCostBreakdown breakdown = mergeTransitCost(stop.costBreakdown(), route == null ? 0 : route.estimatedCost());
                int routeMinutes = route == null || route.durationMinutes() == null ? stop.transitMinutesFromPrevious() : route.durationMinutes();
                int routeCost = route == null || route.estimatedCost() == null ? 0 : route.estimatedCost();
                totalTransit += routeMinutes;
                totalCost += safe(stop.estimatedCost()) + routeCost;
                routedStops.add(new TravelPlanStop(
                        stop.slot(),
                        stop.name(),
                        stop.area(),
                        stop.address(),
                        stop.longitude(),
                        stop.latitude(),
                        stop.startTime(),
                        stop.endTime(),
                        stop.durationMinutes(),
                        routeMinutes,
                        stop.estimatedCost(),
                        stop.openTime(),
                        stop.closeTime(),
                        stop.rationale(),
                        breakdown,
                        stop.poiMatch(),
                        route
                ));
                previous = toLocation(stop);
            }

            TravelTransitLeg returnToHotel = previous == null ? null : routeBetween(previous, hotelBase, city, preferChinese, conversationId);
            if (returnToHotel != null) {
                totalTransit += safe(returnToHotel.durationMinutes());
                totalCost += safe(returnToHotel.estimatedCost());
            }

            results.add(new TravelPlanDay(
                    day.dayNumber(),
                    day.theme(),
                    day.startTime(),
                    day.endTime(),
                    totalTransit,
                    day.totalActivityMinutes(),
                    totalCost == 0 ? day.estimatedCost() : totalCost,
                    routedStops,
                    returnToHotel
            ));
        }
        return results;
    }

    private LocationRef hotelBase(List<TravelHotelRecommendation> hotels, String hotelArea, String city, String conversationId) {
        if (!hotels.isEmpty()) {
            TravelHotelRecommendation hotel = hotels.get(0);
            return new LocationRef(hotel.name(), hotel.area(), hotel.longitude(), hotel.latitude());
        }
        GeoLocation geo = amapMcpGateway.geocode(city + hotelArea, conversationId);
        return new LocationRef(hotelArea, hotelArea, geo.longitude(), geo.latitude());
    }

    private TravelTransitLeg routeBetween(LocationRef from, TravelPlanStop to, String city, boolean preferChinese, String conversationId) {
        return routeBetween(from, toLocation(to), city, preferChinese, conversationId);
    }

    private TravelTransitLeg routeBetween(LocationRef from, LocationRef to, String city, boolean preferChinese, String conversationId) {
        if (from == null || to == null || blank(from.longitude()) || blank(from.latitude()) || blank(to.longitude()) || blank(to.latitude())) {
            return fallbackLeg(from, to, preferChinese);
        }
        timelinePublisher.publish(TimelineEvent.of(
                conversationId,
                ExecutionStage.CALL_TOOL,
                "Resolve transit route with Amap",
                Map.of("from", from.name(), "to", to.name(), "city", city)
        ));
        TransitRoutePlan route = amapMcpGateway.transitRoute(new TransitRouteQuery(from.longitude(), from.latitude(), to.longitude(), to.latitude(), city), conversationId);
        return new TravelTransitLeg(
                from.name(),
                to.name(),
                route.mode(),
                routeSummary(route, preferChinese),
                route.durationMinutes(),
                route.distanceMeters(),
                route.walkingMinutes(),
                route.cost(),
                route.lineNames(),
                route.steps().stream()
                        .map(step -> new TravelTransitStep(
                                step.mode(),
                                step.title(),
                                step.instruction(),
                                step.lineName(),
                                step.fromName(),
                                step.toName(),
                                step.durationMinutes(),
                                step.distanceMeters(),
                                step.stopCount(),
                                step.polyline()
                        ))
                        .toList(),
                route.polyline(),
                "MCP.amap_transit_route"
        );
    }

    private TravelTransitLeg fallbackLeg(LocationRef from, LocationRef to, boolean preferChinese) {
        String fromName = from == null ? (preferChinese ? "出发点" : "start") : from.name();
        String toName = to == null ? (preferChinese ? "下一站" : "next stop") : to.name();
        String summary = preferChinese ? "按片区估算，建议优先地铁或短距离打车。"
                : "Estimated from district distance. Prefer metro or a short taxi.";
        return new TravelTransitLeg(
                fromName,
                toName,
                "SUBWAY",
                summary,
                25,
                4500,
                8,
                4,
                List.of(),
                List.of(new TravelTransitStep("SUBWAY", summary, summary, "", fromName, toName, 25, 4500, 0, List.of())),
                List.of(),
                "RULE.fallback"
        );
    }

    private TravelCostBreakdown mergeTransitCost(TravelCostBreakdown current, int transitCost) {
        if (current == null) {
            return new TravelCostBreakdown(0, 0, transitCost, 0, "");
        }
        return new TravelCostBreakdown(
                current.ticketCost(),
                current.foodCost(),
                transitCost,
                current.otherCost(),
                current.note()
        );
    }

    private LocationRef toLocation(TravelPlanStop stop) {
        if (stop == null) {
            return null;
        }
        return new LocationRef(stop.name(), stop.area(), stop.longitude(), stop.latitude());
    }

    // helpers

    private boolean preferChinese(String userMessage, String city) {
        return (userMessage != null && userMessage.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF))
                || (city != null && city.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF));
    }

    private String canonicalKeyword(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value) {
            case "西湖", "West Lake" -> "西湖";
            case "断桥残雪", "Broken Bridge" -> "断桥";
            case "灵隐寺", "Lingyin Temple" -> "灵隐寺";
            case "龙井村", "Longjing Village" -> "龙井村";
            case "河坊街", "Hefang Street" -> "河坊街";
            case "武林夜市", "Wulin Night Market" -> "武林夜市";
            case "知味观味庄", "Zhiweiguan Weizhuang" -> "知味观味庄";
            case "湖滨步行街", "Hubin Pedestrian Street" -> "湖滨步行街";
            case "西湖湖滨", "West Lake Waterfront" -> "西湖";
            case "吴山河坊街", "Old Town" -> "河坊街";
            case "灵隐片区", "Historic Core" -> "灵隐寺";
            case "龙井茶村", "Longjing" -> "龙井村";
            case "市中心", "City Center" -> "武林广场";
            default -> value;
        };
    }

    private String chooseName(String currentName, PlaceSuggestion suggestion) {
        String resolved = suggestion.name();
        return resolved == null || resolved.isBlank() ? currentName : resolved;
    }

    private String chooseArea(String currentArea, PlaceSuggestion suggestion) {
        String resolved = suggestion.district();
        return resolved == null || resolved.isBlank() ? currentArea : resolved;
    }

    private String appendVerification(String rationale, PlaceSuggestion suggestion, boolean preferChinese) {
        if (suggestion.district() == null || suggestion.district().isBlank()) {
            return rationale;
        }
        return preferChinese
                ? rationale + " 已按高德 POI 候选映射到 " + suggestion.district() + "。"
                : rationale + " Verified against Amap POI candidates in " + suggestion.district() + ".";
    }

    private String composeAddress(String city, PlaceSuggestion suggestion) {
        String district = suggestion.district() == null ? "" : suggestion.district();
        String address = suggestion.address() == null ? "" : suggestion.address();
        String combined = (district + address).trim();
        if (combined.isBlank()) {
            return city;
        }
        return combined.startsWith(city) ? combined : city + combined;
    }

    private String[] splitLocation(String location) {
        if (location == null || location.isBlank() || !location.contains(",")) {
            return new String[]{null, null};
        }
        String[] parts = location.split(",");
        if (parts.length < 2) {
            return new String[]{null, null};
        }
        return new String[]{parts[0], parts[1]};
    }

    private String routeSummary(TransitRoutePlan route, boolean preferChinese) {
        String lineText = route.lineNames().isEmpty() ? route.mode() : String.join(" / ", route.lineNames());
        if (preferChinese) {
            return lineText + "，约 " + safe(route.durationMinutes()) + " 分钟，步行 " + safe(route.walkingMinutes()) + " 分钟，约 " + safe(route.cost()) + " 元";
        }
        return lineText + ", about " + safe(route.durationMinutes()) + " min, walk " + safe(route.walkingMinutes()) + " min, around " + safe(route.cost()) + " CNY";
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s·()（）-]", "");
    }

    private boolean currentAreaIsBlank(String currentArea) {
        return currentArea == null || currentArea.isBlank();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record ResolvedPoi(PlaceSuggestion suggestion, double score, List<String> candidateNames) {
    }

    private record LocationRef(String name, String area, String longitude, String latitude) {
    }
}
