package com.xx2201.travel.agent.amap.mcp.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = AmapMcpServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "travel.agent.amap.mock-when-missing-key=true",
                "travel.agent.amap.mock-on-error=true"
        }
)
class AmapMcpServerIntegrationTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void initializesAndListsToolsOverMcp() throws IOException, InterruptedException {
        HttpResponse<String> initialize = post(null, """
                {
                  "jsonrpc": "2.0",
                  "id": "init-1",
                  "method": "initialize",
                  "params": {
                    "protocolVersion": "2025-03-26",
                    "capabilities": {},
                    "clientInfo": {
                      "name": "test-client",
                      "version": "1.0.0"
                    }
                  }
                }
                """);

        assertEquals(200, initialize.statusCode());
        assertTrue(initialize.body().contains("travel-agent-amap"));
        String sessionId = initialize.headers().firstValue("Mcp-Session-Id").orElse(null);
        assertNotNull(sessionId);

        post(sessionId, """
                {
                  "jsonrpc": "2.0",
                  "method": "notifications/initialized"
                }
                """);

        HttpResponse<String> tools = post(sessionId, """
                {
                  "jsonrpc": "2.0",
                  "id": "tools-1",
                  "method": "tools/list",
                  "params": {}
                }
                """);

        assertEquals(200, tools.statusCode());
        assertTrue(tools.body().contains("amap_weather"));
        assertTrue(tools.body().contains("amap_geocode"));
        assertTrue(tools.body().contains("amap_transit_route"));
    }

    @Test
    void callsWeatherToolWithMockFallback() throws IOException, InterruptedException {
        HttpResponse<String> initialize = post(null, """
                {
                  "jsonrpc": "2.0",
                  "id": "init-2",
                  "method": "initialize",
                  "params": {
                    "protocolVersion": "2025-03-26",
                    "capabilities": {},
                    "clientInfo": {
                      "name": "test-client",
                      "version": "1.0.0"
                    }
                  }
                }
                """);
        String sessionId = initialize.headers().firstValue("Mcp-Session-Id").orElse(null);
        assertNotNull(sessionId);

        post(sessionId, """
                {
                  "jsonrpc": "2.0",
                  "method": "notifications/initialized"
                }
                """);

        HttpResponse<String> weather = post(sessionId, """
                {
                  "jsonrpc": "2.0",
                  "id": "call-1",
                  "method": "tools/call",
                  "params": {
                    "name": "amap_weather",
                    "arguments": {
                      "city": "330100"
                    }
                  }
                }
                """);

        assertEquals(200, weather.statusCode());
        assertTrue(weather.body().contains("\"isError\":false"));
        assertTrue(weather.body().contains("330100"));
    }

    private HttpResponse<String> post(String sessionId, String body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream, application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
