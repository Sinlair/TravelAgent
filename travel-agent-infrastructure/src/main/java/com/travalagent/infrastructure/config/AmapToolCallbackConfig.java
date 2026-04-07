package com.travalagent.infrastructure.config;

import com.travalagent.infrastructure.gateway.tool.AmapTravelTools;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration(proxyBeanMethods = false)
public class AmapToolCallbackConfig {

    @Bean(name = "amapToolCallbackProvider")
    ToolCallbackProvider amapToolCallbackProvider(
            TravelAgentProperties properties,
            AmapTravelTools amapTravelTools,
            ObjectProvider<SyncMcpToolCallbackProvider> mcpToolCallbackProvider
    ) {
        if (properties.getToolProvider() == TravelAgentProperties.ToolProvider.LOCAL) {
            return MethodToolCallbackProvider.builder()
                    .toolObjects(amapTravelTools)
                    .build();
        }

        SyncMcpToolCallbackProvider provider = mcpToolCallbackProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException("travel.agent.tool-provider=MCP but no SyncMcpToolCallbackProvider bean is available");
        }

        ToolCallback[] callbacks = Arrays.stream(provider.getToolCallbacks())
                .filter(toolCallback -> toolCallback.getToolDefinition().name().startsWith("amap_"))
                .toArray(ToolCallback[]::new);

        if (callbacks.length == 0) {
            throw new IllegalStateException("No Amap MCP tool callbacks were discovered from the configured MCP clients");
        }

        return ToolCallbackProvider.from(callbacks);
    }
}