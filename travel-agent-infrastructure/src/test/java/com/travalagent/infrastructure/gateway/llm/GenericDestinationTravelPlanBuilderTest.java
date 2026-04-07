package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.model.entity.TaskMemory;
import com.travalagent.domain.model.entity.TravelPlan;
import com.travalagent.domain.model.valobj.AgentExecutionContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericDestinationTravelPlanBuilderTest {

    private final GenericDestinationTravelPlanBuilder builder = new GenericDestinationTravelPlanBuilder();

    @Test
    void prioritizesExplicitlyNamedPoi() {
        AgentExecutionContext context = new AgentExecutionContext(
                "conversation-1",
                "帮我规划 3 天厦门行程，从深圳出发，预算 3000，想去鼓浪屿，整体轻松一点。",
                List.of(),
                new TaskMemory(
                        "conversation-1",
                        "深圳",
                        "厦门",
                        3,
                        "3000 元",
                        List.of("relaxed pace"),
                        null,
                        null,
                        Instant.now()
                ),
                null,
                List.of(),
                "planner"
        );

        TravelPlan plan = builder.build(context);

        assertTrue(plan.days().stream()
                .flatMap(day -> day.stops().stream())
                .anyMatch(stop -> stop.name().contains("鼓浪屿")));
    }
}
