package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.model.entity.TaskMemory;
import com.travalagent.domain.model.valobj.AgentRouteDecision;
import com.travalagent.domain.model.valobj.AgentType;
import com.travalagent.domain.model.valobj.RoutingContext;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class OpenAiAgentRouterTest {

    @Test
    void heuristicPlannerRouteUsesTaskMemoryForScopedFollowUps() {
        OpenAiAgentRouter router = new OpenAiAgentRouter(mock(ChatClient.Builder.class), new OpenAiAvailability(""));

        AgentRouteDecision decision = router.route(new RoutingContext(
                "conversation-1",
                "Refresh only Day 1 and keep the rest stable.",
                List.of(),
                new TaskMemory(
                        "conversation-1",
                        "Shanghai",
                        "Hangzhou",
                        "2026-05-02",
                        "2026-05-03",
                        2,
                        "couple",
                        "2500 CNY",
                        List.of("local food"),
                        null,
                        null,
                        Instant.now()
                ),
                null,
                List.of()
        ));

        assertEquals(AgentType.TRAVEL_PLANNER, decision.agentType());
        assertFalse(decision.clarificationRequired());
    }
}
