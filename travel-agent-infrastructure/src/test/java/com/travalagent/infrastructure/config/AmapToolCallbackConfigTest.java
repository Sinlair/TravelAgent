package com.travalagent.infrastructure.config;

import com.travalagent.domain.event.TimelinePublisher;
import com.travalagent.domain.gateway.AmapGateway;
import com.travalagent.infrastructure.gateway.tool.AmapTravelTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AmapToolCallbackConfigTest {

    private final AmapToolCallbackConfig config = new AmapToolCallbackConfig();

    @Test
    void createsLocalToolCallbacksWhenConfigured() {
        TravelAgentProperties properties = new TravelAgentProperties();
        properties.setToolProvider(TravelAgentProperties.ToolProvider.LOCAL);

        ToolCallbackProvider provider = config.amapToolCallbackProvider(
                properties,
                new AmapTravelTools(mock(AmapGateway.class), mock(TimelinePublisher.class)),
                emptyProvider()
        );

        assertEquals(5, provider.getToolCallbacks().length);
    }

    @Test
    void filtersMcpToolCallbacksToAmapTools() {
        TravelAgentProperties properties = new TravelAgentProperties();
        properties.setToolProvider(TravelAgentProperties.ToolProvider.MCP);

        SyncMcpToolCallbackProvider mcpProvider = mock(SyncMcpToolCallbackProvider.class);
        when(mcpProvider.getToolCallbacks()).thenReturn(new ToolCallback[]{toolCallback("amap_weather"), toolCallback("calendar_create")});

        ToolCallbackProvider provider = config.amapToolCallbackProvider(
                properties,
                new AmapTravelTools(mock(AmapGateway.class), mock(TimelinePublisher.class)),
                providerOf(mcpProvider)
        );

        assertEquals(1, provider.getToolCallbacks().length);
        assertEquals("amap_weather", provider.getToolCallbacks()[0].getToolDefinition().name());
    }

    @Test
    void throwsWhenMcpModeIsEnabledWithoutMcpProvider() {
        TravelAgentProperties properties = new TravelAgentProperties();
        properties.setToolProvider(TravelAgentProperties.ToolProvider.MCP);

        assertThrows(
                IllegalStateException.class,
                () -> config.amapToolCallbackProvider(
                        properties,
                        new AmapTravelTools(mock(AmapGateway.class), mock(TimelinePublisher.class)),
                        emptyProvider()
                )
        );
    }

    private ToolCallback toolCallback(String name) {
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
                return "{}";
            }
        };
    }

    private ObjectProvider<SyncMcpToolCallbackProvider> emptyProvider() {
        return new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(SyncMcpToolCallbackProvider.class);
    }

    private ObjectProvider<SyncMcpToolCallbackProvider> providerOf(SyncMcpToolCallbackProvider provider) {
        org.springframework.beans.factory.support.StaticListableBeanFactory beanFactory = new org.springframework.beans.factory.support.StaticListableBeanFactory();
        beanFactory.addBean("syncMcpToolCallbackProvider", provider);
        return beanFactory.getBeanProvider(SyncMcpToolCallbackProvider.class);
    }
}
