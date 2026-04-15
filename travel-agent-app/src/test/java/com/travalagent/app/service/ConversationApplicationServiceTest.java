package com.travalagent.app.service;

import com.travalagent.app.dto.FeedbackLoopSummaryResponse;
import com.travalagent.domain.model.entity.ConversationFeedback;
import com.travalagent.domain.repository.ConversationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationApplicationServiceTest {

    private final ConversationWorkflow conversationWorkflow = mock(ConversationWorkflow.class);
    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final ConversationApplicationService service = new ConversationApplicationService(
            conversationWorkflow,
            conversationRepository,
            null
    );

    @Test
    void feedbackLoopSummaryAggregatesFailureSignals() {
        when(conversationRepository.listFeedback(200)).thenReturn(List.of(
                new ConversationFeedback(
                        "conversation-1",
                        "ACCEPTED",
                        "message-1",
                        "OVERALL",
                        "2026-04-07T00:00:00Z",
                        List.of("used_as_is"),
                        "used_as_is",
                        null,
                        com.travalagent.domain.model.valobj.AgentType.TRAVEL_PLANNER,
                        "Hangzhou",
                        2,
                        "1800 CNY",
                        true,
                        Map.of(
                                "validationFailCount", 0,
                                "validationWarnCount", 1,
                                "constraintRelaxed", false,
                                "knowledgeHintCount", 4
                        ),
                        Instant.parse("2026-04-07T00:00:00Z"),
                        Instant.parse("2026-04-07T00:00:00Z")
                ),
                new ConversationFeedback(
                        "conversation-2",
                        "PARTIAL",
                        "message-2",
                        "PLAN",
                        "2026-04-07T00:00:00Z",
                        List.of("edited_before_use"),
                        "edited_before_use",
                        null,
                        com.travalagent.domain.model.valobj.AgentType.TRAVEL_PLANNER,
                        "Hangzhou",
                        2,
                        "1800 CNY",
                        true,
                        Map.of(
                                "validationFailCount", 1,
                                "validationWarnCount", 2,
                                "constraintRelaxed", true,
                                "knowledgeHintCount", 1
                        ),
                        Instant.parse("2026-04-07T00:00:00Z"),
                        Instant.parse("2026-04-07T00:00:00Z")
                ),
                new ConversationFeedback(
                        "conversation-3",
                        "REJECTED",
                        "message-3",
                        "OVERALL",
                        null,
                        List.of("not_useful"),
                        "not_useful",
                        null,
                        com.travalagent.domain.model.valobj.AgentType.TRAVEL_PLANNER,
                        "Suzhou",
                        3,
                        "2500 CNY",
                        false,
                        Map.of(
                                "validationFailCount", 2,
                                "validationWarnCount", 3,
                                "constraintRelaxed", true,
                                "knowledgeHintCount", 0
                        ),
                        Instant.parse("2026-04-07T00:00:00Z"),
                        Instant.parse("2026-04-07T00:00:00Z")
                )
        ));

        FeedbackLoopSummaryResponse summary = service.feedbackLoopSummary(0);

        assertThat(summary.limitApplied()).isEqualTo(200);
        assertThat(summary.sampleCount()).isEqualTo(3);
        assertThat(summary.acceptedCount()).isEqualTo(1);
        assertThat(summary.partialCount()).isEqualTo(1);
        assertThat(summary.rejectedCount()).isEqualTo(1);
        assertThat(summary.acceptedRatePct()).isEqualTo(33.33);
        assertThat(summary.usableRatePct()).isEqualTo(66.67);
        assertThat(summary.structuredPlanCoveragePct()).isEqualTo(66.67);
        assertThat(summary.topDestinations())
                .anyMatch(item -> item.key().equals("Hangzhou") && item.totalCount() == 2 && item.usableRatePct() == 100.0);
        assertThat(summary.keyFindings())
                .anyMatch(item -> item.type().equals("VALIDATION_FAIL") && item.key().equals("validationFailCount>0"));
        assertThat(summary.keyFindings())
                .anyMatch(item -> item.type().equals("TRAVEL_PLAN_COVERAGE") && item.key().equals("no_structured_plan"));
        assertThat(summary.keyFindings())
                .anyMatch(item -> item.type().equals("REASON_CODE") && item.key().equals("not_useful"));
    }
}
