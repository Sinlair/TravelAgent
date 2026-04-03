package com.xx2201.travel.agent.infrastructure.gateway.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xx2201.travel.agent.domain.model.valobj.PlaceSearchQuery;
import com.xx2201.travel.agent.domain.model.valobj.TransitRouteQuery;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AmapMcpGatewayTest {

    @Test
    void parsesToolResultsFromCallbacks() {
        ToolCallbackProvider provider = ToolCallbackProvider.from(
                tool("amap_geocode", """
                        {
                          "name":"West Lake",
                          "address":"Hangzhou West Lake",
                          "longitude":"120.15",
                          "latitude":"30.25",
                          "adCode":"330106"
                        }
                        """),
                tool("amap_input_tips", """
                        [
                          {
                            "id":"1",
                            "name":"West Lake Scenic Area",
                            "district":"Hangzhou",
                            "address":"Xihu District",
                            "adCode":"330106",
                            "location":"120.15,30.25",
                            "typeCode":"110000",
                            "type":"poi"
                          }
                        ]
                        """),
                tool("amap_transit_route", """
                        {
                          "structuredContent":{
                            "mode":"SUBWAY",
                            "summary":"Line 1",
                            "durationMinutes":22,
                            "distanceMeters":5600,
                            "walkingMinutes":8,
                            "cost":4,
                            "lineNames":["Line 1"],
                            "steps":[],
                            "polyline":["120.1,30.2","120.2,30.3"]
                          }
                        }
                        """)
        );

        AmapMcpGateway gateway = new AmapMcpGateway(new ObjectMapper(), provider);

        var geo = gateway.geocode("West Lake", "conversation-1");
        assertEquals("120.15", geo.longitude());

        var tips = gateway.inputTips(new PlaceSearchQuery("West Lake", "Hangzhou", null, null, true, "poi"), "conversation-1");
        assertEquals(1, tips.size());
        assertEquals("West Lake Scenic Area", tips.get(0).name());

        var route = gateway.transitRoute(new TransitRouteQuery("120.1", "30.2", "120.2", "30.3", "Hangzhou"), "conversation-1");
        assertEquals("SUBWAY", route.mode());
        assertEquals(2, route.polyline().size());
    }

    @Test
    void reusesCachedResultsWithinTheSameConversation() {
        AtomicInteger geocodeCalls = new AtomicInteger();
        ToolCallbackProvider provider = ToolCallbackProvider.from(
                tool("amap_geocode", """
                        {
                          "name":"West Lake",
                          "address":"Hangzhou West Lake",
                          "longitude":"120.15",
                          "latitude":"30.25",
                          "adCode":"330106"
                        }
                        """, geocodeCalls)
        );

        AmapMcpGateway gateway = new AmapMcpGateway(new ObjectMapper(), provider);

        gateway.geocode("West Lake", "conversation-1");
        gateway.geocode("West Lake", "conversation-1");
        gateway.geocode("West Lake", "conversation-2");

        assertEquals(2, geocodeCalls.get());
    }

    private ToolCallback tool(String name, String output) {
        return tool(name, output, null);
    }

    private ToolCallback tool(String name, String output, AtomicInteger counter) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(name)
                .description(name + " description")
                .inputSchema("{}")
                .build();

        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                if (counter != null) {
                    counter.incrementAndGet();
                }
                return output;
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                if (counter != null) {
                    counter.incrementAndGet();
                }
                return output;
            }
        };
    }
}
