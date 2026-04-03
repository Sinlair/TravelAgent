package com.xx2201.travel.agent.infrastructure.gateway.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenAiAvailability {

    private final String apiKey;

    public OpenAiAvailability(@Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isAvailable() {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        String normalized = apiKey.trim().toLowerCase();
        return !normalized.contains("placeholder")
                && !normalized.contains("dummy")
                && !normalized.contains("example");
    }
}
