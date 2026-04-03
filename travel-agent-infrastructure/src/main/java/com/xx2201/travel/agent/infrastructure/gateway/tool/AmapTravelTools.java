package com.xx2201.travel.agent.infrastructure.gateway.tool;

import com.xx2201.travel.agent.domain.event.TimelinePublisher;
import com.xx2201.travel.agent.domain.gateway.AmapGateway;
import com.xx2201.travel.agent.domain.model.entity.TimelineEvent;
import com.xx2201.travel.agent.domain.model.valobj.ExecutionStage;
import com.xx2201.travel.agent.domain.model.valobj.GeoLocation;
import com.xx2201.travel.agent.domain.model.valobj.PlaceSearchQuery;
import com.xx2201.travel.agent.domain.model.valobj.PlaceSuggestion;
import com.xx2201.travel.agent.domain.model.valobj.TransitRoutePlan;
import com.xx2201.travel.agent.domain.model.valobj.TransitRouteQuery;
import com.xx2201.travel.agent.domain.model.valobj.WeatherSnapshot;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AmapTravelTools {

    private final AmapGateway amapGateway;
    private final TimelinePublisher timelinePublisher;

    public AmapTravelTools(AmapGateway amapGateway, TimelinePublisher timelinePublisher) {
        this.amapGateway = amapGateway;
        this.timelinePublisher = timelinePublisher;
    }

    @Tool(name = "amap_weather", description = "Query current weather for a Chinese city or district")
    public WeatherSnapshot weather(
            @ToolParam(description = "Chinese city or district name, such as Hangzhou or Chaoyang") String city,
            ToolContext toolContext
    ) {
        publish(toolContext, "Call Amap weather tool", Map.of("city", city));
        return amapGateway.weather(city);
    }

    @Tool(name = "amap_geocode", description = "Convert a Chinese address or place name into coordinates")
    public GeoLocation geocode(
            @ToolParam(description = "Chinese address or scenic spot name") String address,
            ToolContext toolContext
    ) {
        publish(toolContext, "Call Amap geocode tool", Map.of("address", address));
        return amapGateway.geocode(address);
    }

    @Tool(name = "amap_reverse_geocode", description = "Convert coordinates into a Chinese address")
    public GeoLocation reverseGeocode(
            @ToolParam(description = "Longitude") String longitude,
            @ToolParam(description = "Latitude") String latitude,
            ToolContext toolContext
    ) {
        publish(toolContext, "Call Amap reverse geocode tool", Map.of("longitude", longitude, "latitude", latitude));
        return amapGateway.reverseGeocode(longitude, latitude);
    }

    @Tool(name = "amap_input_tips", description = "Search place suggestions for attractions, districts, or transport hubs")
    public List<PlaceSuggestion> inputTips(
            @ToolParam(description = "Keywords such as West Lake, Forbidden City, Pudong Airport") String keyword,
            @ToolParam(description = "Optional city name to narrow the suggestions", required = false) String city,
            @ToolParam(description = "Optional Amap type filter such as 住宿服务", required = false) String type,
            @ToolParam(description = "Optional longitude,latitude to bias nearby search", required = false) String location,
            @ToolParam(description = "Whether to enforce city limit", required = false) Boolean cityLimit,
            @ToolParam(description = "Optional data type such as poi", required = false) String dataType,
            ToolContext toolContext
    ) {
        publish(toolContext, "Call Amap input tips tool", Map.of("keyword", keyword, "city", city == null ? "" : city));
        return amapGateway.inputTips(new PlaceSearchQuery(
                keyword,
                city,
                type,
                location,
                cityLimit != null && cityLimit,
                dataType == null || dataType.isBlank() ? "poi" : dataType
        ));
    }

    @Tool(name = "amap_transit_route", description = "Plan local transit between two coordinates in a Chinese city")
    public TransitRoutePlan transitRoute(
            @ToolParam(description = "Origin longitude") String originLongitude,
            @ToolParam(description = "Origin latitude") String originLatitude,
            @ToolParam(description = "Destination longitude") String destinationLongitude,
            @ToolParam(description = "Destination latitude") String destinationLatitude,
            @ToolParam(description = "City name for local transit planning") String city,
            ToolContext toolContext
    ) {
        publish(toolContext, "Call Amap transit route tool", Map.of(
                "originLongitude", originLongitude,
                "originLatitude", originLatitude,
                "destinationLongitude", destinationLongitude,
                "destinationLatitude", destinationLatitude,
                "city", city
        ));
        return amapGateway.transitRoute(new TransitRouteQuery(
                originLongitude,
                originLatitude,
                destinationLongitude,
                destinationLatitude,
                city
        ));
    }

    private void publish(ToolContext toolContext, String message, Map<String, Object> details) {
        Object conversationId = toolContext.getContext().get("conversationId");
        if (conversationId == null) {
            return;
        }
        timelinePublisher.publish(TimelineEvent.of(
                conversationId.toString(),
                ExecutionStage.CALL_TOOL,
                message,
                details
        ));
    }
}
