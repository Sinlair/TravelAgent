package com.travalagent.amap.mcp.server;

import com.travalagent.domain.gateway.AmapGateway;
import com.travalagent.domain.model.valobj.GeoLocation;
import com.travalagent.domain.model.valobj.PlaceSearchQuery;
import com.travalagent.domain.model.valobj.PlaceSuggestion;
import com.travalagent.domain.model.valobj.TransitRoutePlan;
import com.travalagent.domain.model.valobj.TransitRouteQuery;
import com.travalagent.domain.model.valobj.WeatherSnapshot;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AmapMcpTools {

    private final AmapGateway amapGateway;

    public AmapMcpTools(AmapGateway amapGateway) {
        this.amapGateway = amapGateway;
    }

    @Tool(name = "amap_weather", description = "Query current weather for a Chinese city or district")
    public WeatherSnapshot weather(
            @ToolParam(description = "Chinese city or district name, such as Hangzhou or Chaoyang") String city
    ) {
        return amapGateway.weather(requireText(city, "city"));
    }

    @Tool(name = "amap_geocode", description = "Convert a Chinese address or place name into coordinates")
    public GeoLocation geocode(
            @ToolParam(description = "Chinese address or scenic spot name") String address
    ) {
        return amapGateway.geocode(requireText(address, "address"));
    }

    @Tool(name = "amap_reverse_geocode", description = "Convert coordinates into a Chinese address")
    public GeoLocation reverseGeocode(
            @ToolParam(description = "Longitude") String longitude,
            @ToolParam(description = "Latitude") String latitude
    ) {
        return amapGateway.reverseGeocode(requireText(longitude, "longitude"), requireText(latitude, "latitude"));
    }

    @Tool(name = "amap_input_tips", description = "Search place suggestions for attractions, districts, or transport hubs")
    public List<PlaceSuggestion> inputTips(
            @ToolParam(description = "Keywords such as West Lake, Forbidden City, Pudong Airport") String keyword,
            @ToolParam(description = "Optional city name to narrow the suggestions", required = false) String city,
            @ToolParam(description = "Optional Amap type filter such as 住宿服务", required = false) String type,
            @ToolParam(description = "Optional longitude,latitude to bias nearby search", required = false) String location,
            @ToolParam(description = "Whether to enforce city limit", required = false) Boolean cityLimit,
            @ToolParam(description = "Optional data type such as poi", required = false) String dataType
    ) {
        return amapGateway.inputTips(new PlaceSearchQuery(
                requireText(keyword, "keyword"),
                blankToNull(city),
                blankToNull(type),
                blankToNull(location),
                cityLimit != null && cityLimit,
                blankToDefault(dataType, "poi")
        ));
    }

    @Tool(name = "amap_transit_route", description = "Plan local transit between two coordinates in a Chinese city")
    public TransitRoutePlan transitRoute(
            @ToolParam(description = "Origin longitude") String originLongitude,
            @ToolParam(description = "Origin latitude") String originLatitude,
            @ToolParam(description = "Destination longitude") String destinationLongitude,
            @ToolParam(description = "Destination latitude") String destinationLatitude,
            @ToolParam(description = "City name for local transit planning") String city
    ) {
        return amapGateway.transitRoute(new TransitRouteQuery(
                requireText(originLongitude, "originLongitude"),
                requireText(originLatitude, "originLatitude"),
                requireText(destinationLongitude, "destinationLongitude"),
                requireText(destinationLatitude, "destinationLatitude"),
                requireText(city, "city")
        ));
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private String blankToDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.strip();
    }
}
