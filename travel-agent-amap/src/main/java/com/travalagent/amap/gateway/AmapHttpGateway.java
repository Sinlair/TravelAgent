package com.travalagent.amap.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travalagent.amap.config.AmapProperties;
import com.travalagent.domain.gateway.AmapGateway;
import com.travalagent.domain.model.valobj.GeoLocation;
import com.travalagent.domain.model.valobj.PlaceSearchQuery;
import com.travalagent.domain.model.valobj.PlaceSuggestion;
import com.travalagent.domain.model.valobj.TransitRoutePlan;
import com.travalagent.domain.model.valobj.TransitRouteQuery;
import com.travalagent.domain.model.valobj.TransitRouteStep;
import com.travalagent.domain.model.valobj.WeatherSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AmapHttpGateway implements AmapGateway {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AmapProperties properties;
    private final Object throttleMonitor = new Object();
    private long lastHttpCallAt = 0L;

    public AmapHttpGateway(RestClient.Builder restClientBuilder, ObjectMapper objectMapper, AmapProperties properties) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public WeatherSnapshot weather(String city) {
        JsonNode root = get("/v3/weather/weatherInfo", Map.of("city", city, "extensions", "base"));
        JsonNode live = first(root.path("lives"));
        if (live == null) {
            return mockWeather(city);
        }
        return new WeatherSnapshot(
                text(live, "city", city),
                text(live, "province", ""),
                text(live, "reporttime", Instant.now().toString()),
                text(live, "weather", "Sunny"),
                text(live, "temperature", "25"),
                text(live, "winddirection", "SE"),
                text(live, "windpower", "3")
        );
    }

    @Override
    public GeoLocation geocode(String address) {
        JsonNode root = get("/v3/geocode/geo", Map.of("address", address));
        JsonNode item = first(root.path("geocodes"));
        if (item == null) {
            return mockGeo(address);
        }
        String location = text(item, "location", "116.397128,39.916527");
        String[] parts = location.split(",");
        return new GeoLocation(
                address,
                text(item, "formatted_address", address),
                parts.length > 0 ? parts[0] : "116.397128",
                parts.length > 1 ? parts[1] : "39.916527",
                text(item, "adcode", "")
        );
    }

    @Override
    public GeoLocation reverseGeocode(String longitude, String latitude) {
        JsonNode root = get("/v3/geocode/regeo", Map.of("location", longitude + "," + latitude));
        JsonNode regeocode = root.path("regeocode");
        if (regeocode.isMissingNode() || regeocode.isEmpty()) {
            return new GeoLocation(
                    "Coordinate point",
                    "No Amap result matched the provided coordinate",
                    longitude,
                    latitude,
                    ""
            );
        }
        JsonNode addressComponent = regeocode.path("addressComponent");
        return new GeoLocation(
                text(addressComponent, "township", "Coordinate point"),
                text(regeocode, "formatted_address", longitude + "," + latitude),
                longitude,
                latitude,
                text(addressComponent, "adcode", "")
        );
    }

    @Override
    public List<PlaceSuggestion> inputTips(PlaceSearchQuery query) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("keywords", query.keyword());
        if (query.city() != null && !query.city().isBlank()) {
            params.put("city", query.city());
        }
        if (query.type() != null && !query.type().isBlank()) {
            params.put("type", query.type());
        }
        if (query.location() != null && !query.location().isBlank()) {
            params.put("location", query.location());
        }
        if (query.cityLimit()) {
            params.put("citylimit", "true");
        }
        if (query.dataType() != null && !query.dataType().isBlank()) {
            params.put("datatype", query.dataType());
        }

        JsonNode root = get("/v3/assistant/inputtips", params);
        JsonNode tips = root.path("tips");
        if (!tips.isArray() || tips.isEmpty()) {
            return List.of(
                    new PlaceSuggestion(
                            "",
                            query.keyword(),
                            query.city() == null ? "" : query.city(),
                            "No Amap result matched the provided keyword",
                            "",
                            "116.397128,39.916527",
                            "",
                            ""
                    )
            );
        }

        List<PlaceSuggestion> suggestions = new ArrayList<>();
        for (JsonNode tip : tips) {
            suggestions.add(new PlaceSuggestion(
                    text(tip, "id", ""),
                    text(tip, "name", query.keyword()),
                    text(tip, "district", query.city() == null ? "" : query.city()),
                    text(tip, "address", ""),
                    text(tip, "adcode", ""),
                    text(tip, "location", ""),
                    text(tip, "typecode", ""),
                    text(tip, "type", "")
            ));
        }
        return suggestions;
    }

    @Override
    public TransitRoutePlan transitRoute(TransitRouteQuery query) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("origin", query.originLongitude() + "," + query.originLatitude());
        params.put("destination", query.destinationLongitude() + "," + query.destinationLatitude());
        if (query.city() != null && !query.city().isBlank()) {
            params.put("city", query.city());
            params.put("cityd", query.city());
        }
        params.put("strategy", "0");
        params.put("nightflag", "0");

        JsonNode root = get("/v3/direction/transit/integrated", params);
        JsonNode transit = first(root.path("route").path("transits"));
        if (transit == null) {
            return fallbackTransit(query);
        }

        List<TransitRouteStep> steps = new ArrayList<>();
        List<String> lineNames = new ArrayList<>();
        Set<String> polyline = new LinkedHashSet<>();

        JsonNode segments = transit.path("segments");
        if (segments.isArray()) {
            for (JsonNode segment : segments) {
                appendWalkingSteps(segment.path("walking"), steps, polyline);
                appendBusSteps(segment.path("bus").path("buslines"), steps, lineNames, polyline);
                appendRailwaySteps(segment.path("railway"), steps, lineNames, polyline);
            }
        }

        int walkingMinutes = steps.stream()
                .filter(step -> "WALK".equals(step.mode()))
                .mapToInt(step -> safeInt(step.durationMinutes()))
                .sum();
        int distanceMeters = parseInteger(text(transit, "distance", "0"));
        int durationMinutes = secondsToMinutes(text(transit, "duration", "0"));
        int cost = parseCost(text(transit, "cost", "0"));
        String mode = lineNames.stream().anyMatch(line -> line.contains("地铁")) ? "SUBWAY"
                : lineNames.isEmpty() ? "WALK" : "BUS";
        String summary = lineNames.isEmpty()
                ? "Walk or take a short taxi between the two points."
                : "Use " + String.join(" / ", lineNames) + " for this leg.";

        return new TransitRoutePlan(
                mode,
                summary,
                durationMinutes,
                distanceMeters,
                walkingMinutes,
                cost,
                lineNames,
                steps,
                new ArrayList<>(polyline)
        );
    }

    private void appendWalkingSteps(JsonNode walking, List<TransitRouteStep> steps, Set<String> polyline) {
        if (walking == null || walking.isMissingNode() || walking.isEmpty()) {
            return;
        }

        JsonNode walkSteps = walking.path("steps");
        if (walkSteps.isArray() && !walkSteps.isEmpty()) {
            for (JsonNode item : walkSteps) {
                List<String> stepPolyline = toPolylineList(text(item, "polyline", ""));
                polyline.addAll(stepPolyline);
                steps.add(new TransitRouteStep(
                        "WALK",
                        text(item, "instruction", "Walk"),
                        text(item, "instruction", "Walk"),
                        "",
                        "",
                        "",
                        secondsToMinutes(text(item, "duration", text(walking, "duration", "0"))),
                        parseInteger(text(item, "distance", "0")),
                        0,
                        stepPolyline
                ));
            }
            return;
        }

        List<String> stepPolyline = toPolylineList(text(walking, "polyline", ""));
        polyline.addAll(stepPolyline);
        steps.add(new TransitRouteStep(
                "WALK",
                "Walk",
                "Walk to the next transfer point.",
                "",
                "",
                "",
                secondsToMinutes(text(walking, "duration", "0")),
                parseInteger(text(walking, "distance", "0")),
                0,
                stepPolyline
        ));
    }

    private void appendBusSteps(JsonNode buslines, List<TransitRouteStep> steps, List<String> lineNames, Set<String> polyline) {
        if (!buslines.isArray()) {
            return;
        }
        for (JsonNode line : buslines) {
            String lineName = text(line, "name", "");
            List<String> stepPolyline = toPolylineList(text(line, "polyline", ""));
            polyline.addAll(stepPolyline);
            lineNames.add(lineName);
            steps.add(new TransitRouteStep(
                    lineName.contains("地铁") ? "SUBWAY" : "BUS",
                    lineName,
                    lineName,
                    lineName,
                    text(line.path("departure_stop"), "name", ""),
                    text(line.path("arrival_stop"), "name", ""),
                    secondsToMinutes(text(line, "duration", "0")),
                    parseInteger(text(line, "distance", "0")),
                    parseInteger(text(line, "via_num", "0")),
                    stepPolyline
            ));
        }
    }

    private void appendRailwaySteps(JsonNode railway, List<TransitRouteStep> steps, List<String> lineNames, Set<String> polyline) {
        if (railway == null || railway.isMissingNode() || railway.isEmpty()) {
            return;
        }
        String lineName = text(railway, "name", "Rail");
        List<String> stepPolyline = toPolylineList(text(railway, "polyline", ""));
        polyline.addAll(stepPolyline);
        lineNames.add(lineName);
        steps.add(new TransitRouteStep(
                "RAIL",
                lineName,
                lineName,
                lineName,
                text(railway.path("departure_stop"), "name", ""),
                text(railway.path("arrival_stop"), "name", ""),
                secondsToMinutes(text(railway, "time", "0")),
                parseInteger(text(railway, "distance", "0")),
                0,
                stepPolyline
        ));
    }

    private TransitRoutePlan fallbackTransit(TransitRouteQuery query) {
        int distanceMeters = estimateDistanceMeters(
                parseDouble(query.originLongitude()),
                parseDouble(query.originLatitude()),
                parseDouble(query.destinationLongitude()),
                parseDouble(query.destinationLatitude())
        );
        int durationMinutes = distanceMeters <= 1500 ? Math.max(10, distanceMeters / 80)
                : distanceMeters <= 8000 ? Math.max(18, distanceMeters / 280)
                : Math.max(25, distanceMeters / 380);
        int cost = distanceMeters <= 1500 ? 0 : distanceMeters <= 8000 ? 4 : Math.max(18, distanceMeters / 700);
        String mode = distanceMeters <= 1500 ? "WALK" : distanceMeters <= 8000 ? "SUBWAY" : "TAXI";
        String summary = switch (mode) {
            case "WALK" -> "Walk directly between the two points.";
            case "SUBWAY" -> "Take metro or a short taxi hop between the two points.";
            default -> "A taxi is the most stable fallback for this leg.";
        };
        List<String> polyline = List.of(
                query.originLongitude() + "," + query.originLatitude(),
                query.destinationLongitude() + "," + query.destinationLatitude()
        );
        return new TransitRoutePlan(
                mode,
                summary,
                durationMinutes,
                distanceMeters,
                "WALK".equals(mode) ? durationMinutes : 8,
                cost,
                List.of(),
                List.of(new TransitRouteStep(mode, summary, summary, "", "", "", durationMinutes, distanceMeters, 0, polyline)),
                polyline
        );
    }

    private List<String> toPolylineList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> points = new ArrayList<>();
        for (String point : raw.split(";")) {
            if (!point.isBlank()) {
                points.add(point.trim());
            }
        }
        return points;
    }

    private int estimateDistanceMeters(double originLongitude, double originLatitude, double destinationLongitude, double destinationLatitude) {
        if (Double.isNaN(originLongitude) || Double.isNaN(originLatitude) || Double.isNaN(destinationLongitude) || Double.isNaN(destinationLatitude)) {
            return 4500;
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

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception exception) {
            return Double.NaN;
        }
    }

    private int secondsToMinutes(String value) {
        int seconds = parseInteger(value);
        return seconds <= 0 ? 0 : Math.max(1, (int) Math.round(seconds / 60.0));
    }

    private int parseCost(String value) {
        try {
            return (int) Math.round(Double.parseDouble(value));
        } catch (Exception exception) {
            return 0;
        }
    }

    private int parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception exception) {
            return 0;
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private JsonNode get(String path, Map<String, String> params) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            if (properties.isMockWhenMissingKey()) {
                return objectMapper.createObjectNode();
            }
            throw new IllegalStateException("TRAVEL_AGENT_AMAP_API_KEY is required");
        }
        try {
            Map<String, String> actual = new LinkedHashMap<>(params);
            actual.put("key", properties.getApiKey());
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getBaseUrl() + path);
            actual.forEach(builder::queryParam);
            throttleBeforeCall();
            String body = restClient.get()
                    .uri(builder.build().encode().toUri())
                    .retrieve()
                    .body(String.class);
            JsonNode root = body == null ? objectMapper.createObjectNode() : objectMapper.readTree(body);
            if (!"1".equals(root.path("status").asText("1"))) {
                String info = root.path("info").asText("Unknown Amap error");
                String infocode = root.path("infocode").asText("");
                throw new IllegalStateException("Amap request failed: " + info + (infocode.isBlank() ? "" : " (" + infocode + ")"));
            }
            return root;
        } catch (Exception exception) {
            if (properties.isMockOnError()) {
                return objectMapper.createObjectNode();
            }
            throw new IllegalStateException("Amap request failed for path " + path + ": " + exception.getMessage(), exception);
        }
    }

    private JsonNode first(JsonNode arrayNode) {
        return arrayNode.isArray() && !arrayNode.isEmpty() ? arrayNode.get(0) : null;
    }

    private GeoLocation mockGeo(String address) {
        return new GeoLocation(
                address,
                "No Amap result matched the provided address, fallback to Tiananmen coordinates",
                "116.397128",
                "39.916527",
                "110101"
        );
    }

    private WeatherSnapshot mockWeather(String city) {
        return new WeatherSnapshot(city, city, Instant.now().toString(), "Sunny", "25", "SE", "3");
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() || child.asText().isBlank() ? fallback : child.asText();
    }

    private void throttleBeforeCall() {
        double requestsPerSecond = properties.getRequestsPerSecond();
        if (requestsPerSecond <= 0) {
            return;
        }
        long minIntervalMillis = Math.max(334L, (long) Math.ceil(1000.0 / requestsPerSecond));
        synchronized (throttleMonitor) {
            long now = System.currentTimeMillis();
            long waitMillis = (lastHttpCallAt + minIntervalMillis) - now;
            if (waitMillis > 0) {
                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while throttling Amap HTTP requests", exception);
                }
            }
            lastHttpCallAt = System.currentTimeMillis();
        }
    }
}
