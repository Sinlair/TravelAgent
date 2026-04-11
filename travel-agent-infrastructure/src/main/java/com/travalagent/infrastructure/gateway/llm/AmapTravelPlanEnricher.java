package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.event.TimelinePublisher;
import com.travalagent.domain.model.entity.TimelineEvent;
import com.travalagent.domain.model.entity.TravelCostBreakdown;
import com.travalagent.domain.model.entity.TravelHotelRecommendation;
import com.travalagent.domain.model.entity.TravelPlan;
import com.travalagent.domain.model.entity.TravelPlanDay;
import com.travalagent.domain.model.entity.TravelPlanStop;
import com.travalagent.domain.model.entity.TravelPoiMatch;
import com.travalagent.domain.model.entity.TravelTransitLeg;
import com.travalagent.domain.model.entity.TravelTransitStep;
import com.travalagent.domain.model.valobj.AgentExecutionContext;
import com.travalagent.domain.model.valobj.ExecutionStage;
import com.travalagent.domain.model.valobj.GeoLocation;
import com.travalagent.domain.model.valobj.PlaceSearchQuery;
import com.travalagent.domain.model.valobj.PlaceSuggestion;
import com.travalagent.domain.model.valobj.TransitRoutePlan;
import com.travalagent.domain.model.valobj.TransitRouteQuery;
import com.travalagent.infrastructure.gateway.tool.AmapMcpGateway;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        String hotelAreaKeyword = canonicalKeyword(plan.hotelArea());

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

        List<ScoredHotelCandidate> rankedCandidates = unique.values().stream()
                .map(suggestion -> new ScoredHotelCandidate(
                        suggestion,
                        scoreHotelCandidate(hotelAreaKeyword, city, hotelAreaGeo, suggestion),
                        distanceMeters(hotelAreaGeo.longitude(), hotelAreaGeo.latitude(), splitLocation(suggestion.location())[0], splitLocation(suggestion.location())[1])
                ))
                .sorted(Comparator.comparingDouble(ScoredHotelCandidate::score).reversed()
                        .thenComparingInt(candidate -> candidate.distanceMeters() == Integer.MAX_VALUE ? Integer.MAX_VALUE : candidate.distanceMeters()))
                .toList();

        if (!rankedCandidates.isEmpty()
                && rankedCandidates.get(0).distanceMeters() > 8000
                && !blank(hotelAreaGeo.longitude())
                && !blank(hotelAreaGeo.latitude())) {
            return List.of(fallbackHotelBase(plan, hotelAreaGeo, hotelMin, hotelMax, preferChinese));
        }

        List<PlaceSuggestion> rankedSuggestions = rankedCandidates.stream()
                .map(ScoredHotelCandidate::suggestion)
                .toList();

        List<TravelHotelRecommendation> results = rankedSuggestions.stream()
                .limit(3)
                .map(suggestion -> {
                    String[] coordinate = splitLocation(suggestion.location());
                    String name = suggestion.name();
                    return new TravelHotelRecommendation(
                            name,
                            chooseArea(plan.hotelArea(), suggestion),
                            composeAddress(city, suggestion),
                            hotelMin,
                            hotelMax,
                            hotelReasonForSuggestion(suggestion, preferChinese, resultsIndex(rankedSuggestions, suggestion)),
                            coordinate[0],
                            coordinate[1],
                            "MCP.amap_input_tips",
                            generateBookingUrl(name, city, preferChinese)
                    );
                })
                .toList();

        if (!results.isEmpty()) {
            return results;
        }

        return List.of(fallbackHotelBase(plan, hotelAreaGeo, hotelMin, hotelMax, preferChinese)); /*
                preferChinese ? plan.hotelArea() + "优先酒店位" : plan.hotelArea() + " hotel base",
                plan.hotelArea(),
                hotelAreaGeo.address(),
                hotelMin,
                hotelMax,
                preferChinese ? "高德酒店候选不足时，先按推荐住宿区保留落脚点。" : "Fallback hotel base when Amap cannot return a concrete hotel candidate.",
                hotelAreaGeo.longitude(),
                hotelAreaGeo.latitude(),
                "MCP.amap_geocode"
        )); */
    }

    private GeoLocation resolveHotelArea(String hotelArea, String city, String conversationId) {
        timelinePublisher.publish(TimelineEvent.of(
                conversationId,
                ExecutionStage.CALL_TOOL,
                "Resolve hotel district center with Amap",
                Map.of("hotelArea", hotelArea, "city", city)
        ));
        GeoLocation mapped = mappedHotelArea(city, hotelArea);
        if (mapped != null) {
            return mapped;
        }
        List<String> candidates = new ArrayList<>();
        if (!blank(city) && !blank(hotelArea)) {
            candidates.add(city + " " + hotelArea);
        }
        String canonical = canonicalKeyword(hotelArea);
        String stableHotelArea = switch (hotelArea) {
            case "West Lake Waterfront" -> "\u897f\u6e56";
            case "Historic Core" -> "\u7075\u9690\u5bfa";
            case "Old Town" -> "\u6cb3\u574a\u8857";
            case "Longjing" -> "\u9f99\u4e95\u6751";
            case "City Center" -> "\u6b66\u6797\u5e7f\u573a";
            default -> hotelArea;
        };
        if (!blank(city) && !blank(canonical) && !canonical.equals(hotelArea)) {
            candidates.add(city + " " + canonical);
        }
        if (!blank(city) && !blank(stableHotelArea) && !stableHotelArea.equals(hotelArea) && !stableHotelArea.equals(canonical)) {
            candidates.add(city + " " + stableHotelArea);
        }
        if (!blank(canonical)) {
            candidates.add(canonical);
        }
        if (!blank(stableHotelArea) && !stableHotelArea.equals(canonical)) {
            candidates.add(stableHotelArea);
        }
        if (!blank(hotelArea)) {
            candidates.add(hotelArea);
        }

        IllegalStateException last = null;
        for (String candidate : candidates.stream().filter(value -> !blank(value)).distinct().toList()) {
            try {
                return amapMcpGateway.geocode(candidate, conversationId);
            } catch (IllegalStateException exception) {
                last = exception;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new IllegalStateException("Unable to resolve hotel area with Amap");
    }

    private GeoLocation mappedHotelArea(String city, String hotelArea) {
        if (blank(city) || blank(hotelArea)) {
            return null;
        }
        String normalizedCity = normalize(city);
        String normalizedArea = normalize(hotelArea);
        if (!(normalizedCity.contains("hangzhou") || normalizedCity.contains("\u676d\u5dde"))) {
            return null;
        }
        if (normalizedArea.contains("westlake") || normalizedArea.contains("\u897f\u6e56")) {
            return new GeoLocation("West Lake hotel area", "\u676d\u5dde\u5e02\u897f\u6e56\u533a", "120.130396", "30.259242", "330106");
        }
        if (normalizedArea.contains("historiccore") || normalizedArea.contains("lingyin") || normalizedArea.contains("\u7075\u9690")) {
            return new GeoLocation("Historic core hotel area", "\u676d\u5dde\u5e02\u897f\u6e56\u533a\u7075\u9690\u8def", "120.104114", "30.242742", "330106");
        }
        if (normalizedArea.contains("oldtown") || normalizedArea.contains("hefang") || normalizedArea.contains("\u6cb3\u574a")) {
            return new GeoLocation("Old town hotel area", "\u676d\u5dde\u5e02\u4e0a\u57ce\u533a\u6cb3\u574a\u8857", "120.171465", "30.245775", "330102");
        }
        if (normalizedArea.contains("longjing") || normalizedArea.contains("\u9f99\u4e95")) {
            return new GeoLocation("Longjing hotel area", "\u676d\u5dde\u5e02\u897f\u6e56\u533a\u9f99\u4e95\u6751", "120.111906", "30.225563", "330106");
        }
        if (normalizedArea.contains("citycenter") || normalizedArea.contains("wulin") || normalizedArea.contains("\u6b66\u6797")) {
            return new GeoLocation("City center hotel area", "\u676d\u5dde\u5e02\u62f1\u5885\u533a\u6b66\u6797\u5e7f\u573a", "120.169607", "30.274728", "330105");
        }
        if (normalizedArea.contains("transithub") || normalizedArea.contains("\u67a2\u7ebd")) {
            return new GeoLocation("Transit hub hotel area", "\u676d\u5dde\u5e02\u62f1\u5885\u533a\u6b66\u6797\u5e7f\u573a", "120.169607", "30.274728", "330105");
        }
        if (normalizedArea.contains("hoteldistrict")) {
            return new GeoLocation("Hotel district", "\u676d\u5dde\u5e02\u62f1\u5885\u533a\u6b66\u6797\u5e7f\u573a", "120.169607", "30.274728", "330105");
        }
        return null;
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
        GeoLocation geo = resolveHotelArea(hotelArea, city, conversationId);
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
        TravelTransitLeg rawLeg = new TravelTransitLeg(
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
        TravelTransitLeg fallback = fallbackLeg(from, to, preferChinese);
        return shouldPreferFallback(route, fallback, from, to) ? fallback : rawLeg;
    }

    private TravelTransitLeg fallbackLegLegacy(LocationRef from, LocationRef to, boolean preferChinese) {
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

    private TravelTransitLeg fallbackLeg(LocationRef from, LocationRef to, boolean preferChinese) {
        String fromName = from == null ? "start" : from.name();
        String toName = to == null ? "next stop" : to.name();
        int directDistance = estimateDistanceMeters(from, to);
        int durationMinutes = directDistance <= 1500 ? Math.max(10, directDistance / 80)
                : directDistance <= 8000 ? Math.max(18, directDistance / 280)
                : Math.max(25, directDistance / 380);
        int walkingMinutes = directDistance <= 1500 ? durationMinutes : Math.min(10, Math.max(4, directDistance / 800));
        int cost = directDistance <= 1500 ? 0 : directDistance <= 8000 ? 4 : Math.max(18, directDistance / 700);
        String mode = directDistance <= 1500 ? "WALK" : directDistance <= 8000 ? "SUBWAY" : "TAXI";
        String summary = preferChinese
                ? "估算为更可执行的市内交通方案，优先地铁或短程打车。"
                : "Estimated with a more practical in-city fallback. Prefer metro or a short taxi hop.";
        return new TravelTransitLeg(
                fromName,
                toName,
                mode,
                summary,
                durationMinutes,
                directDistance,
                walkingMinutes,
                cost,
                List.of(),
                List.of(new TravelTransitStep(mode, summary, summary, "", fromName, toName, durationMinutes, directDistance, 0, List.of())),
                List.of(),
                "RULE.fallback"
        );
    }

    private TravelHotelRecommendation fallbackHotelBase(TravelPlan plan, GeoLocation hotelAreaGeo, int hotelMin, int hotelMax, boolean preferChinese) {
        if (plan != null) {
            FallbackHotelExample example = fallbackHotelExample(plan.hotelArea(), preferChinese);
            String hotelName = example == null ? fallbackHotelName(plan.hotelArea(), preferChinese) : example.name();
            String address = example == null ? hotelAreaGeo.address() : example.address();
            String longitude = example == null || blank(example.longitude()) ? hotelAreaGeo.longitude() : example.longitude();
            String latitude = example == null || blank(example.latitude()) ? hotelAreaGeo.latitude() : example.latitude();
            String rationale = preferChinese
                    ? "当前没有拿到稳定的高德酒店候选，先给你一个推荐片区内更明确的参考住宿点，后续可以再替换成你自己想订的酒店。"
                    : "A stable Amap hotel candidate was not available, so this keeps a clearer reference stay inside the recommended district until you swap in your final booking.";
            return new TravelHotelRecommendation(
                    hotelName,
                    plan.hotelArea(),
                    address,
                    hotelMin,
                    hotelMax,
                    rationale,
                    longitude,
                    latitude,
                    "RULE.fallback",
                    generateBookingUrl(hotelName, null, preferChinese)
            );
        }
        String hotelName = preferChinese
                ? switch (plan.hotelArea()) {
                    case "西湖湖滨" -> "西湖湖滨精选酒店";
                    case "灵隐片区" -> "灵隐片区安静酒店";
                    case "吴山河坊街" -> "河坊街步行友好酒店";
                    case "龙井茶村" -> "龙井茶村慢节奏民宿";
                    case "市中心" -> "市中心通勤便利酒店";
                    default -> plan.hotelArea() + " 精选酒店";
                }
                : switch (plan.hotelArea()) {
                    case "West Lake Waterfront" -> "West Lake Selected Hotel";
                    case "Historic Core" -> "Historic Core Quiet Hotel";
                    case "Old Town" -> "Old Town Walkable Hotel";
                    case "Longjing" -> "Longjing Slow-Paced Stay";
                    case "City Center" -> "City Center Transit Hotel";
                    default -> plan.hotelArea() + " selected hotel";
                };
        String rationale = preferChinese
                ? "高德酒店候选不够稳定时，先保留一个位于推荐住宿区内、便于继续执行路线的酒店落脚点。"
                : "When Amap hotel candidates are unstable, keep a dependable base inside the recommended stay area so the itinerary remains executable.";
        return new TravelHotelRecommendation(
                hotelName,
                plan.hotelArea(),
                hotelAreaGeo.address(),
                hotelMin,
                hotelMax,
                rationale,
                hotelAreaGeo.longitude(),
                hotelAreaGeo.latitude(),
                "MCP.amap_geocode",
                generateBookingUrl(hotelName, null, preferChinese)
        );
    }

    private String fallbackHotelName(String hotelArea, boolean preferChinese) {
        if (preferChinese) {
            return switch (hotelArea) {
                case "西湖湖滨" -> "西湖湖滨参考住宿点";
                case "灵隐片区" -> "灵隐片区参考住宿点";
                case "吴山河坊街" -> "河坊街参考住宿点";
                case "龙井茶村" -> "龙井茶村参考住宿点";
                case "市中心" -> "市中心参考住宿点";
                default -> hotelArea + "参考住宿点";
            };
        }
        return switch (hotelArea) {
            case "West Lake Waterfront" -> "West Lake reference stay";
            case "Historic Core" -> "Historic Core reference stay";
            case "Old Town" -> "Old Town reference stay";
            case "Longjing" -> "Longjing reference stay";
            case "City Center" -> "City Center reference stay";
            default -> hotelArea + " reference stay";
        };
    }

    private FallbackHotelExample fallbackHotelExample(String hotelArea, boolean preferChinese) {
        String normalizedArea = normalize(hotelArea);
        if (normalizedArea.contains("westlake") || normalizedArea.contains("西湖")) {
            return preferChinese
                    ? new FallbackHotelExample(
                    "浣纱路参考住宿点",
                    "杭州市上城区浣纱路17号",
                    "120.164930",
                    "30.255220"
            )
                    : new FallbackHotelExample(
                    "Hangzhou Sunny Huansha Hotel",
                    "17 Huansha Road, Shangcheng District, Hangzhou",
                    "120.164930",
                    "30.255220"
            );
        }
        if (normalizedArea.contains("oldtown") || normalizedArea.contains("hefang") || normalizedArea.contains("河坊")) {
            return preferChinese
                    ? new FallbackHotelExample(
                    "建国南路参考住宿点",
                    "杭州市上城区建国南路280号",
                    "120.177420",
                    "30.242960"
            )
                    : new FallbackHotelExample(
                    "Redstar Culture Hotel",
                    "280 South Jianguo Road, Shangcheng District, Hangzhou",
                    "120.177420",
                    "30.242960"
            );
        }
        if (normalizedArea.contains("citycenter") || normalizedArea.contains("wulin") || normalizedArea.contains("市中心") || normalizedArea.contains("武林")) {
            return preferChinese
                    ? new FallbackHotelExample(
                    "庆春路参考住宿点",
                    "杭州市上城区庆春路65号",
                    "120.177860",
                    "30.261740"
            )
                    : new FallbackHotelExample(
                    "Enjoyor Hotel",
                    "65 Qingchun Road, Hangzhou",
                    "120.177860",
                    "30.261740"
            );
        }
        return null;
    }

    private double scoreHotelCandidate(String hotelAreaKeyword, String city, GeoLocation hotelAreaGeo, PlaceSuggestion suggestion) {
        String keyword = normalize(hotelAreaKeyword);
        String name = normalize(suggestion.name());
        String district = normalize(suggestion.district());
        String address = normalize(suggestion.address());
        double score = 0;

        if (!keyword.isBlank() && (name.contains(keyword) || district.contains(keyword) || address.contains(keyword))) {
            score += 35;
        }
        if (!blank(city)) {
            String normalizedCity = normalize(city);
            if (district.contains(normalizedCity) || address.contains(normalizedCity)) {
                score += 8;
            }
        }
        if (!blank(suggestion.location())) {
            score += 15;
        }

        int distance = distanceMeters(hotelAreaGeo.longitude(), hotelAreaGeo.latitude(), splitLocation(suggestion.location())[0], splitLocation(suggestion.location())[1]);
        if (distance != Integer.MAX_VALUE) {
            score += Math.max(0, 60 - (distance / 200.0));
        }
        return score;
    }

    private boolean shouldPreferFallback(TransitRoutePlan route, TravelTransitLeg fallback, LocationRef from, LocationRef to) {
        if (route == null || fallback == null) {
            return true;
        }
        int rawDuration = safe(route.durationMinutes());
        int fallbackDuration = safe(fallback.durationMinutes());
        int directDistance = estimateDistanceMeters(from, to);
        int routeDistance = safe(route.distanceMeters()) > 0 ? safe(route.distanceMeters()) : directDistance;

        if (directDistance <= 2000 && rawDuration > 35) {
            return true;
        }
        if (directDistance <= 5000 && rawDuration > 55) {
            return true;
        }
        if (routeDistance <= 12000 && rawDuration > fallbackDuration + 25) {
            return true;
        }
        return safe(route.walkingMinutes()) > 45 && routeDistance <= 5000;
    }

    private int estimateDistanceMeters(LocationRef from, LocationRef to) {
        if (from == null || to == null) {
            return 4500;
        }
        int distance = distanceMeters(from.longitude(), from.latitude(), to.longitude(), to.latitude());
        return distance == Integer.MAX_VALUE ? 4500 : distance;
    }

    private int distanceMeters(String fromLongitude, String fromLatitude, String toLongitude, String toLatitude) {
        if (blank(fromLongitude) || blank(fromLatitude) || blank(toLongitude) || blank(toLatitude)) {
            return Integer.MAX_VALUE;
        }
        double originLongitude;
        double originLatitude;
        double destinationLongitude;
        double destinationLatitude;
        try {
            originLongitude = Double.parseDouble(fromLongitude);
            originLatitude = Double.parseDouble(fromLatitude);
            destinationLongitude = Double.parseDouble(toLongitude);
            destinationLatitude = Double.parseDouble(toLatitude);
        } catch (Exception exception) {
            return Integer.MAX_VALUE;
        }

        double radians = Math.PI / 180;
        double latitudeDistance = (destinationLatitude - originLatitude) * radians;
        double longitudeDistance = (destinationLongitude - originLongitude) * radians;
        double a = Math.sin(latitudeDistance / 2) * Math.sin(latitudeDistance / 2)
                + Math.cos(originLatitude * radians) * Math.cos(destinationLatitude * radians)
                * Math.sin(longitudeDistance / 2) * Math.sin(longitudeDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(6371000 * c);
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
            return lineText + "，全程约 " + safe(route.durationMinutes()) + " 分钟，步行 " + safe(route.walkingMinutes()) + " 分钟，预计 " + safe(route.cost()) + " 元";
        }
        return lineText + ", around " + safe(route.durationMinutes()) + " min total, " + safe(route.walkingMinutes()) + " min walking, about " + safe(route.cost()) + " CNY";
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

    private String generateBookingUrl(String hotelName, String city, boolean preferChinese) {
        if (hotelName == null || hotelName.isBlank()) {
            return null;
        }
        try {
            String encodedName = URLEncoder.encode(hotelName, StandardCharsets.UTF_8);
            if (preferChinese) {
                // Ctrip search
                return "https://hotels.ctrip.com/hotels/list?keyword=" + encodedName + "&cityname=" + URLEncoder.encode(city, StandardCharsets.UTF_8);
            } else {
                // Booking.com search
                return "https://www.booking.com/searchresults.html?ss=" + encodedName;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private record ScoredHotelCandidate(PlaceSuggestion suggestion, double score, int distanceMeters) {
    }

    private record FallbackHotelExample(String name, String address, String longitude, String latitude) {
    }
}
