package com.travalagent.app.health;

import com.travalagent.infrastructure.config.TravelAgentProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class ToolProviderHealthIndicator implements HealthIndicator {

    private final TravelAgentProperties properties;
    private final boolean mcpEnabled;
    private final boolean mcpInitialized;
    private final String mcpBaseUrl;
    private final String amapApiKey;

    public ToolProviderHealthIndicator(
            TravelAgentProperties properties,
            @Value("${spring.ai.mcp.client.enabled:true}") boolean mcpEnabled,
            @Value("${spring.ai.mcp.client.initialized:true}") boolean mcpInitialized,
            @Value("${spring.ai.mcp.client.streamable-http.connections.amap.url:http://localhost:8090}") String mcpBaseUrl,
            @Value("${travel.agent.amap.api-key:}") String amapApiKey
    ) {
        this.properties = properties;
        this.mcpEnabled = mcpEnabled;
        this.mcpInitialized = mcpInitialized;
        this.mcpBaseUrl = mcpBaseUrl;
        this.amapApiKey = amapApiKey;
    }

    @Override
    public Health health() {
        if (properties.getToolProvider() == TravelAgentProperties.ToolProvider.LOCAL) {
            return Health.up()
                    .withDetail("provider", "LOCAL")
                    .withDetail("amapApiKeyConfigured", isConfigured(amapApiKey))
                    .build();
        }
        if (mcpEnabled && mcpInitialized) {
            return Health.up()
                    .withDetail("provider", "MCP")
                    .withDetail("mcpBaseUrl", mcpBaseUrl)
                    .build();
        }
        return Health.down()
                .withDetail("provider", properties.getToolProvider().name())
                .withDetail("mcpEnabled", mcpEnabled)
                .withDetail("mcpInitialized", mcpInitialized)
                .withDetail("mcpBaseUrl", mcpBaseUrl)
                .build();
    }

    private boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }
}
