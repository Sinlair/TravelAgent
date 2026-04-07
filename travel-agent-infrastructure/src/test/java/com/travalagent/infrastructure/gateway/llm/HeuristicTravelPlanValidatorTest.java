package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.model.entity.TaskMemory;
import com.travalagent.domain.model.entity.TravelBudgetItem;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeuristicTravelPlanValidatorTest {

    private final HeuristicTravelPlanValidator validator = new HeuristicTravelPlanValidator();

    @Test
    void validateFlagsBudgetHoursTransitPaceAndDuplicates() {
        TravelPlan plan = samplePlan();
        AgentExecutionContext context = new AgentExecutionContext(
                "conversation-1",
                "Plan a relaxed Hangzhou trip with a strict 600 CNY budget.",
                List.of(),
                new TaskMemory(
                        "conversation-1",
                        "Shanghai",
                        "Hangzhou",
                        2,
                        "600 CNY",
                        List.of("relaxed pace"),
                        null,
                        null,
                        Instant.now()
                ),
                null,
                List.of(),
                "planner"
        );

        TravelPlanValidationResult result = validator.validate(plan, context);

        assertFalse(result.accepted());
        assertTrue(result.repairCodes().contains("budget"));
        assertTrue(result.repairCodes().contains("opening-hours"));
        assertTrue(result.repairCodes().contains("transit-load"));
        assertTrue(result.repairCodes().contains("pace"));
        assertTrue(result.repairCodes().contains("dedupe"));
        assertTrue(result.normalizedPlan().checks().stream().anyMatch(check -> check.code().equals("dedupe") && check.status().name().equals("FAIL")));
    }

    private TravelPlan samplePlan() {
        TravelPlanStop dayOneStop = new TravelPlanStop(
                TravelPlanSlot.MORNING,
                "West Lake",
                "West Lake",
                "Hangzhou West Lake",
                "120.1551",
                "30.2741",
                "09:00",
                "18:30",
                510,
                120,
                280,
                "09:00",
                "17:30",
                "Long scenic walk",
                new TravelCostBreakdown(120, 80, 50, 30, "ticket and lunch"),
                null,
                transitLeg("Hotel", "West Lake", 120, 50)
        );
        TravelPlanStop dayTwoStop = new TravelPlanStop(
                TravelPlanSlot.AFTERNOON,
                "West Lake",
                "West Lake",
                "Hangzhou West Lake",
                "120.1551",
                "30.2741",
                "13:00",
                "15:00",
                120,
                30,
                120,
                "09:00",
                "17:30",
                "Repeat lake visit",
                new TravelCostBreakdown(40, 30, 20, 30, "coffee and small ticket"),
                null,
                transitLeg("Hotel", "West Lake", 30, 20)
        );

        return new TravelPlan(
                "conversation-1",
                "Hangzhou draft",
                "Draft plan",
                "West Lake",
                "Central access",
                List.of(new TravelHotelRecommendation("Lake Hotel", "West Lake", "Hangzhou", 520, 680, "Primary option", "120.1", "30.2", "amap")),
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
                List.of(),
                List.of(
                        new TravelPlanDay(1, "Lake day", "09:00", "18:30", 240, 510, 330, List.of(dayOneStop), transitLeg("West Lake", "Hotel", 120, 60)),
                        new TravelPlanDay(2, "Repeat day", "13:00", "15:00", 60, 120, 140, List.of(dayTwoStop), transitLeg("West Lake", "Hotel", 30, 20))
                ),
                Instant.now()
        );
    }

    private TravelTransitLeg transitLeg(String from, String to, int minutes, int cost) {
        return new TravelTransitLeg(from, to, "TRANSIT", from + " to " + to, minutes, 5000, 10, cost, List.of("Metro"), List.of(), List.of(), "test");
    }
}