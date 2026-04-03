package com.xx2201.travel.agent.app.health;

import com.xx2201.travel.agent.infrastructure.gateway.llm.OpenAiAvailability;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class OpenAiHealthIndicator implements HealthIndicator {

    private final OpenAiAvailability openAiAvailability;

    public OpenAiHealthIndicator(OpenAiAvailability openAiAvailability) {
        this.openAiAvailability = openAiAvailability;
    }

    @Override
    public Health health() {
        boolean available = openAiAvailability.isAvailable();
        if (available) {
            return Health.up()
                    .withDetail("configured", true)
                    .build();
        }
        return Health.up()
                .withDetail("configured", false)
                .withDetail("degraded", true)
                .withDetail("reason", "OpenAI API key is missing or placeholder-like")
                .build();
    }
}
