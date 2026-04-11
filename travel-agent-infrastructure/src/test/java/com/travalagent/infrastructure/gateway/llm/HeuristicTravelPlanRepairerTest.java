package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.model.entity.TaskMemory;
import com.travalagent.domain.model.entity.TravelBudgetItem;
import com.travalagent.domain.model.entity.TravelConstraintCheck;
import com.travalagent.domain.model.entity.TravelCostBreakdown;
import com.travalagent.domain.model.entity.TravelHotelRecommendation;
import com.travalagent.domain.model.entity.TravelPlan;
import com.travalagent.domain.model.entity.TravelPlanDay;
import com.travalagent.domain.model.entity.TravelPlanSlot;
import com.travalagent.domain.model.entity.TravelPlanStop;
import com.travalagent.domain.model.entity.TravelTransitLeg;
import com.travalagent.domain.model.valobj.AgentExecutionContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HeuristicTravelPlanRepairerTest {

    private final HeuristicTravelPlanRepairer repairer = new HeuristicTravelPlanRepairer();

    @Test
    void repairTrimsStopsAndLowersHotelBudget() {
        TravelPlan plan = samplePlan();
        TravelPlanValidationResult validationResult = new TravelPlanValidationResult(
                plan,
                List.of("budget", "transit-load", "dedupe"),
                false,
                1,
                2
        );
        AgentExecutionContext context = new AgentExecutionContext(
                "conversation-1",
                "Give me a cheaper and lighter Hangzhou plan.",
                List.of(),
                new TaskMemory("conversation-1", "Shanghai", "Hangzhou", 2, "600 CNY", List.of("relaxed pace"), null, null, Instant.now()),
                null,
                List.of(),
                "planner"
        );

        TravelPlan repaired = repairer.repair(plan, validationResult, context);

        assertTrue(repaired.days().get(0).stops().size() < plan.days().get(0).stops().size());
        assertTrue(repaired.budget().stream().filter(item -> item.category().equals("Hotel")).findFirst().orElseThrow().maxAmount()
                < plan.budget().stream().filter(item -> item.category().equals("Hotel")).findFirst().orElseThrow().maxAmount());
        assertTrue(repaired.days().stream().allMatch(day -> day.stops().stream().noneMatch(stop -> stop.routeFromPrevious() != null)));
    }

    private TravelPlan samplePlan() {
        TravelPlanStop morning = new TravelPlanStop(
                TravelPlanSlot.MORNING,
                "West Lake",
                "West Lake",
                "Hangzhou West Lake",
                "120.1551",
                "30.2741",
                "09:00",
                "11:00",
                120,
                80,
                180,
                "08:00",
                "17:30",
                "Main stop",
                new TravelCostBreakdown(60, 40, 20, 10, "morning"),
                null,
                transitLeg("Hotel", "West Lake", 80, 20)
        );
        TravelPlanStop evening = new TravelPlanStop(
                TravelPlanSlot.EVENING,
                "West Lake",
                "West Lake",
                "Hangzhou West Lake",
                "120.1551",
                "30.2741",
                "18:00",
                "20:00",
                120,
                90,
                160,
                "08:00",
                "19:00",
                "Duplicate stop",
                new TravelCostBreakdown(50, 30, 30, 20, "evening"),
                null,
                transitLeg("Mall", "West Lake", 90, 30)
        );

        return new TravelPlan(
                "conversation-1",
                "Hangzhou draft",
                "Draft plan",
                "West Lake",
                "Central access",
                List.of(new TravelHotelRecommendation("Lake Hotel", "West Lake", "Hangzhou", 520, 680, "Primary option", "120.1", "30.2", "amap", null)),
                600,
                0,
                0,
                List.of("West Lake"),
                List.of(
                        new TravelBudgetItem("Hotel", 520, 680, "draft hotel range"),
                        new TravelBudgetItem("Intercity transport", 100, 180, "rail"),
                        new TravelBudgetItem("Local transit", 50, 90, "metro"),
                        new TravelBudgetItem("Food", 120, 180, "meals"),
                        new TravelBudgetItem("Attractions and buffer", 160, 240, "tickets")
                ),
                List.of(new TravelConstraintCheck("budget", null, "warn")),
                List.of(new TravelPlanDay(1, "Lake day", "09:00", "20:00", 250, 240, 400, List.of(morning, evening), transitLeg("West Lake", "Hotel", 80, 30))),
                Instant.now()
        );
    }

    private TravelTransitLeg transitLeg(String from, String to, int minutes, int cost) {
        return new TravelTransitLeg(from, to, "TRANSIT", from + " to " + to, minutes, 5000, 10, cost, List.of("Metro"), List.of(), List.of(), "test");
    }
}