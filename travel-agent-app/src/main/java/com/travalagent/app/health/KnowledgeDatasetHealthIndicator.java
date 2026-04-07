package com.travalagent.app.health;

import com.travalagent.infrastructure.repository.LocalTravelKnowledgeRepository;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeDatasetHealthIndicator implements HealthIndicator {

    private final LocalTravelKnowledgeRepository localTravelKnowledgeRepository;

    public KnowledgeDatasetHealthIndicator(LocalTravelKnowledgeRepository localTravelKnowledgeRepository) {
        this.localTravelKnowledgeRepository = localTravelKnowledgeRepository;
    }

    @Override
    public Health health() {
        int records = localTravelKnowledgeRepository.all().size();
        if (records > 0) {
            return Health.up()
                    .withDetail("records", records)
                    .build();
        }
        return Health.down()
                .withDetail("records", records)
                .withDetail("reason", "No local travel knowledge records loaded")
                .build();
    }
}
