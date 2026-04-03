package com.xx2201.travel.agent.app.bootstrap;

import com.xx2201.travel.agent.infrastructure.repository.TravelKnowledgeSeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "travel.agent.knowledge.seed", name = "enabled", havingValue = "true")
public class TravelKnowledgeSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TravelKnowledgeSeedRunner.class);

    private final TravelKnowledgeSeedService travelKnowledgeSeedService;
    private final ConfigurableApplicationContext applicationContext;
    private final Environment environment;

    public TravelKnowledgeSeedRunner(
            TravelKnowledgeSeedService travelKnowledgeSeedService,
            ConfigurableApplicationContext applicationContext,
            Environment environment
    ) {
        this.travelKnowledgeSeedService = travelKnowledgeSeedService;
        this.applicationContext = applicationContext;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        int seeded = travelKnowledgeSeedService.seedDefaultKnowledge();
        log.info("Seeded {} travel knowledge snippets into the vector store.", seeded);
        if (environment.getProperty("travel.agent.knowledge.seed.verify.enabled", Boolean.class, false)) {
            List<String> sampleCities = parseSampleCities(environment.getProperty("travel.agent.knowledge.seed.verify.sample-cities"));
            var reports = travelKnowledgeSeedService.verifySeededKnowledge(sampleCities);
            if (reports.isEmpty()) {
                log.warn("Travel knowledge seed verification did not produce any sample retrieval report.");
            }
            for (var report : reports) {
                log.info(
                        "Seed verification for city={} source={} inferredTopics={} matched={}",
                        report.destination(),
                        report.retrievalSource(),
                        report.inferredTopics(),
                        report.selections().size()
                );
                for (var selection : report.selections()) {
                    log.info(
                            "  - [{} / {} / q={}] {} ({})",
                            selection.topic(),
                            selection.schemaSubtype(),
                            selection.qualityScore(),
                            selection.title(),
                            selection.source()
                    );
                }
            }
        }
        applicationContext.close();
    }

    private List<String> parseSampleCities(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
