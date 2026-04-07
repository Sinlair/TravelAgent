package com.travalagent.app.health;

import com.travalagent.infrastructure.config.TravelKnowledgeVectorStoreProperties;
import com.travalagent.infrastructure.repository.TravelKnowledgeVectorStoreRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeVectorHealthIndicator implements HealthIndicator {

    private final TravelKnowledgeVectorStoreProperties properties;
    private final ObjectProvider<TravelKnowledgeVectorStoreRepository> repositoryProvider;

    public KnowledgeVectorHealthIndicator(
            TravelKnowledgeVectorStoreProperties properties,
            ObjectProvider<TravelKnowledgeVectorStoreRepository> repositoryProvider
    ) {
        this.properties = properties;
        this.repositoryProvider = repositoryProvider;
    }

    @Override
    public Health health() {
        boolean enabled = properties.isEnabled();
        boolean repositoryAvailable = repositoryProvider.getIfAvailable() != null;
        if (!enabled) {
            return Health.up()
                    .withDetail("enabled", false)
                    .withDetail("collection", properties.getCollectionName())
                    .build();
        }
        if (repositoryAvailable) {
            return Health.up()
                    .withDetail("enabled", true)
                    .withDetail("collection", properties.getCollectionName())
                    .withDetail("database", properties.getDatabaseName())
                    .build();
        }
        return Health.down()
                .withDetail("enabled", true)
                .withDetail("collection", properties.getCollectionName())
                .withDetail("reason", "Knowledge vector store bean is unavailable")
                .build();
    }
}
