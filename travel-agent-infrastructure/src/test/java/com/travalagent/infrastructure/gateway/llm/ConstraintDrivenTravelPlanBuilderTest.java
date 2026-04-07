package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.model.entity.TaskMemory;
import com.travalagent.domain.model.entity.TravelPlan;
import com.travalagent.domain.model.valobj.AgentExecutionContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstraintDrivenTravelPlanBuilderTest {

    private final ConstraintDrivenTravelPlanBuilder builder = new ConstraintDrivenTravelPlanBuilder();

    @Test
    void buildsStructuredPlanWithConstraintChecks() {
        AgentExecutionContext context = new AgentExecutionContext(
                "conversation-1",
                "Plan a 3-day trip from Shanghai to Hangzhou with a 3000 CNY budget. Focus on West Lake, Lingyin Temple, local food, and a relaxed pace.",
                List.of(),
                new TaskMemory(
                        "conversation-1",
                        "Shanghai",
                        "Hangzhou",
                        3,
                        "3000 CNY",
                        List.of("West Lake", "Lingyin Temple", "local food", "relaxed pace"),
                        null,
                        null,
                        Instant.now()
                ),
                null,
                List.of(),
                "planner"
        );

        TravelPlan plan = builder.build(context);
        String answer = builder.render(plan, context);

        assertEquals(3, plan.days().size());
        assertTrue(plan.estimatedTotalMax() >= plan.estimatedTotalMin());
        assertFalse(plan.checks().isEmpty());
        assertTrue(plan.days().stream().flatMap(day -> day.stops().stream()).anyMatch(stop -> stop.name().contains("Lingyin")));
        assertTrue(plan.days().stream().flatMap(day -> day.stops().stream()).anyMatch(stop -> stop.name().contains("West Lake") || stop.name().contains("Broken Bridge")));
        assertTrue(plan.checks().stream().anyMatch(check -> check.code().equals("budget")));
        assertTrue(answer.contains("Constraint Checks"));
        assertTrue(answer.contains("Budget Breakdown"));
    }

    @Test
    void extractsEnglishDestinationFromHangzhouTripWording() {
        AgentExecutionContext context = new AgentExecutionContext(
                "conversation-2",
                "Plan a 2 day Hangzhou trip from Shanghai with a 1800 CNY budget. I want West Lake and local food.",
                List.of(),
                new TaskMemory(
                        "conversation-2",
                        null,
                        null,
                        2,
                        "1800 CNY",
                        List.of("West Lake", "local food"),
                        null,
                        null,
                        Instant.now()
                ),
                null,
                List.of(),
                "planner"
        );

        TravelPlan plan = builder.build(context);

        assertTrue(plan.title().contains("Hangzhou"));
        assertFalse(plan.days().isEmpty());
    }
}
