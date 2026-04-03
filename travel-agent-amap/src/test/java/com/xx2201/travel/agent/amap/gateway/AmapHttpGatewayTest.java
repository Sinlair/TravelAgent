package com.xx2201.travel.agent.amap.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xx2201.travel.agent.amap.config.AmapProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AmapHttpGatewayTest {

    @Test
    void returnsMockWeatherWhenKeyIsMissingAndFallbackIsEnabled() {
        AmapProperties properties = new AmapProperties();
        properties.setApiKey("");
        properties.setMockWhenMissingKey(true);
        properties.setMockOnError(true);

        AmapHttpGateway gateway = new AmapHttpGateway(RestClient.builder(), new ObjectMapper(), properties);

        assertEquals("Hangzhou", gateway.weather("Hangzhou").city());
    }

    @Test
    void throwsWhenKeyIsMissingAndFallbackIsDisabled() {
        AmapProperties properties = new AmapProperties();
        properties.setApiKey("");
        properties.setMockWhenMissingKey(false);
        properties.setMockOnError(false);

        AmapHttpGateway gateway = new AmapHttpGateway(RestClient.builder(), new ObjectMapper(), properties);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> gateway.weather("Hangzhou"));
        assertEquals("TRAVEL_AGENT_AMAP_API_KEY is required", exception.getMessage());
    }
}
