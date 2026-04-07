package com.travalagent.amap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "travel.agent.amap")
public class AmapProperties {

    private String apiKey = "";
    private String baseUrl = "https://restapi.amap.com";
    private boolean mockWhenMissingKey = true;
    private boolean mockOnError = true;
    private double requestsPerSecond = 3.0;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isMockWhenMissingKey() {
        return mockWhenMissingKey;
    }

    public void setMockWhenMissingKey(boolean mockWhenMissingKey) {
        this.mockWhenMissingKey = mockWhenMissingKey;
    }

    public boolean isMockOnError() {
        return mockOnError;
    }

    public void setMockOnError(boolean mockOnError) {
        this.mockOnError = mockOnError;
    }

    public double getRequestsPerSecond() {
        return requestsPerSecond;
    }

    public void setRequestsPerSecond(double requestsPerSecond) {
        this.requestsPerSecond = requestsPerSecond;
    }
}
