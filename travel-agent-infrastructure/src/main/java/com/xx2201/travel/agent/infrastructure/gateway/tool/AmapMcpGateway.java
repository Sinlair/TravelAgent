package com.xx2201.travel.agent.infrastructure.gateway.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xx2201.travel.agent.domain.model.valobj.GeoLocation;
import com.xx2201.travel.agent.domain.model.valobj.PlaceSearchQuery;
import com.xx2201.travel.agent.domain.model.valobj.PlaceSuggestion;
import com.xx2201.travel.agent.domain.model.valobj.TransitRoutePlan;
import com.xx2201.travel.agent.domain.model.valobj.TransitRouteQuery;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AmapMcpGateway {

    private static final TypeReference<List<PlaceSuggestion>> PLACE_SUGGESTION_LIST = new TypeReference<>() {
    };
    private static final long MIN_TOOL_CALL_INTERVAL_MS = 380L;

    private final ObjectMapper objectMapper;
    private final Map<String, ToolCallback> callbacks;
    private final Map<String, Map<String, JsonNode>> conversationCache = new ConcurrentHashMap<>();
    private final Object throttleMonitor = new Object();
    private long lastToolCallAt = 0L;

    public AmapMcpGateway(
            ObjectMapper objectMapper,
            @Qualifier("amapToolCallbackProvider") ToolCallbackProvider toolCallbackProvider
    ) {
        this.objectMapper = objectMapper;
        this.callbacks = List.of(toolCallbackProvider.getToolCallbacks()).stream()
                .collect(Collectors.toMap(callback -> callback.getToolDefinition().name(), Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    public GeoLocation geocode(String address, String conversationId) {
        return convertValue(call("amap_geocode", Map.of("address", address), conversationId), GeoLocation.class);
    }

    public GeoLocation reverseGeocode(String longitude, String latitude, String conversationId) {
        return convertValue(call("amap_reverse_geocode", Map.of(
                "longitude", longitude,
                "latitude", latitude
        ), conversationId), GeoLocation.class);
    }

    public List<PlaceSuggestion> inputTips(PlaceSearchQuery query, String conversationId) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("keyword", query.keyword());
        if (query.city() != null && !query.city().isBlank()) {
            arguments.put("city", query.city());
        }
        if (query.type() != null && !query.type().isBlank()) {
            arguments.put("type", query.type());
        }
        if (query.location() != null && !query.location().isBlank()) {
            arguments.put("location", query.location());
        }
        if (query.cityLimit()) {
            arguments.put("cityLimit", true);
        }
        if (query.dataType() != null && !query.dataType().isBlank()) {
            arguments.put("dataType", query.dataType());
        }
        JsonNode node = call("amap_input_tips", arguments, conversationId);
        if (!node.isArray()) {
            return List.of();
        }
        return objectMapper.convertValue(node, PLACE_SUGGESTION_LIST);
    }

    public TransitRoutePlan transitRoute(TransitRouteQuery query, String conversationId) {
        return convertValue(call("amap_transit_route", Map.of(
                "originLongitude", query.originLongitude(),
                "originLatitude", query.originLatitude(),
                "destinationLongitude", query.destinationLongitude(),
                "destinationLatitude", query.destinationLatitude(),
                "city", query.city()
        ), conversationId), TransitRoutePlan.class);
    }

    public void clearConversationCache(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        conversationCache.remove(conversationId);
    }

    private JsonNode call(String toolName, Map<String, Object> arguments, String conversationId) {
        ToolCallback callback = callbacks.get(toolName);
        if (callback == null) {
            throw new IllegalStateException("Amap MCP tool not found: " + toolName);
        }
        if (conversationId != null && !conversationId.isBlank()) {
            String cacheKey = cacheKey(toolName, arguments);
            Map<String, JsonNode> toolCache = conversationCache.computeIfAbsent(conversationId, ignored -> new ConcurrentHashMap<>());
            JsonNode cached = toolCache.get(cacheKey);
            if (cached != null) {
                return cached.deepCopy();
            }
            throttleBeforeCall();
            String raw = callback.call(writeJson(arguments), new ToolContext(Map.of("conversationId", conversationId)));
            JsonNode result = unwrap(raw);
            toolCache.put(cacheKey, result.deepCopy());
            return result;
        }
        throttleBeforeCall();
        String raw = callback.call(writeJson(arguments), new ToolContext(Map.of("conversationId", conversationId)));
        return unwrap(raw);
    }

    private JsonNode unwrap(String raw) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.isTextual() && looksLikeJson(node.asText())) {
                node = objectMapper.readTree(node.asText());
            }
            if (node.isArray() && !node.isEmpty()) {
                JsonNode first = node.get(0);
                if (first != null && first.has("text") && looksLikeJson(first.path("text").asText())) {
                    return objectMapper.readTree(first.path("text").asText());
                }
            }
            if (node.has("structuredContent")) {
                return node.path("structuredContent");
            }
            if (node.has("result")) {
                return node.path("result");
            }
            if (node.has("content") && node.path("content").isArray() && !node.path("content").isEmpty()) {
                JsonNode first = node.path("content").get(0);
                if (first != null && first.has("text") && looksLikeJson(first.path("text").asText())) {
                    return objectMapper.readTree(first.path("text").asText());
                }
            }
            return node;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse MCP tool result", exception);
        }
    }

    private boolean looksLikeJson(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize MCP tool input", exception);
        }
    }

    private <T> T convertValue(JsonNode node, Class<T> targetType) {
        return objectMapper.convertValue(node, targetType);
    }

    private String cacheKey(String toolName, Map<String, Object> arguments) {
        Map<String, Object> sortedArguments = new TreeMap<>(arguments == null ? Collections.emptyMap() : arguments);
        return toolName + ":" + writeJson(sortedArguments);
    }

    private void throttleBeforeCall() {
        synchronized (throttleMonitor) {
            long now = System.currentTimeMillis();
            long waitMillis = (lastToolCallAt + MIN_TOOL_CALL_INTERVAL_MS) - now;
            if (waitMillis > 0) {
                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while throttling Amap MCP requests", exception);
                }
            }
            lastToolCallAt = System.currentTimeMillis();
        }
    }
}
