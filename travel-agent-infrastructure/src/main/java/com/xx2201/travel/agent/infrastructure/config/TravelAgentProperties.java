package com.xx2201.travel.agent.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "travel.agent")
public class TravelAgentProperties {

    private int memoryWindow = 12;
    private int summaryThreshold = 6;
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
    private ToolProvider toolProvider = ToolProvider.MCP;
    private MemoryProvider memoryProvider = MemoryProvider.AUTO;

    public int getMemoryWindow() {
        return memoryWindow;
    }

    public void setMemoryWindow(int memoryWindow) {
        this.memoryWindow = memoryWindow;
    }

    public int getSummaryThreshold() {
        return summaryThreshold;
    }

    public void setSummaryThreshold(int summaryThreshold) {
        this.summaryThreshold = summaryThreshold;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public ToolProvider getToolProvider() {
        return toolProvider;
    }

    public void setToolProvider(ToolProvider toolProvider) {
        this.toolProvider = toolProvider;
    }

    public MemoryProvider getMemoryProvider() {
        return memoryProvider;
    }

    public void setMemoryProvider(MemoryProvider memoryProvider) {
        this.memoryProvider = memoryProvider;
    }

    public enum ToolProvider {
        LOCAL,
        MCP
    }

    public enum MemoryProvider {
        AUTO,
        SQLITE,
        MILVUS
    }
}