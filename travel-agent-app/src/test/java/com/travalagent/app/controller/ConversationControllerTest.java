package com.travalagent.app.controller;

import com.travalagent.app.dto.ChatResponse;
import com.travalagent.app.dto.FeedbackDatasetRecord;
import com.travalagent.app.dto.FeedbackLoopSummaryResponse;
import com.travalagent.app.service.ConversationApplicationService;
import com.travalagent.app.stream.ConversationStreamHub;
import com.travalagent.domain.model.entity.ConversationFeedback;
import com.travalagent.domain.model.entity.TaskMemory;
import com.travalagent.domain.model.valobj.AgentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.mockito.Mockito.mock;

class ConversationControllerTest {

    private final StubConversationApplicationService conversationApplicationService = new StubConversationApplicationService();
    private final ConversationStreamHub conversationStreamHub = mock(ConversationStreamHub.class);
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        this.webTestClient = WebTestClient.bindToController(new ConversationController(conversationApplicationService, conversationStreamHub))
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void chatReturnsWrappedSuccessResponse() {
        conversationApplicationService.chatResponse = new ChatResponse(
                "conversation-1",
                AgentType.WEATHER,
                "Sunny today",
                TaskMemory.empty("conversation-1"),
                null,
                List.of()
        );

        webTestClient.post()
                .uri("/api/conversations/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "message": "What is the weather in Hangzhou?"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo("0000")
                .jsonPath("$.data.conversationId").isEqualTo("conversation-1")
                .jsonPath("$.data.agentType").isEqualTo("WEATHER");
    }

    @Test
    void chatAcceptsImageOnlyRequests() {
        conversationApplicationService.chatResponse = new ChatResponse(
                "conversation-2",
                AgentType.TRAVEL_PLANNER,
                "I extracted the trip details from the uploaded image.",
                TaskMemory.empty("conversation-2"),
                null,
                List.of()
        );

        webTestClient.post()
                .uri("/api/conversations/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "attachments": [
                            {
                              "name": "hotel.png",
                              "mediaType": "image/png",
                              "dataUrl": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+yWZ0AAAAASUVORK5CYII="
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo("0000")
                .jsonPath("$.data.conversationId").isEqualTo("conversation-2")
                .jsonPath("$.data.agentType").isEqualTo("TRAVEL_PLANNER");
    }

    @Test
    void streamReturnsEventStream() {
        org.mockito.Mockito.when(conversationStreamHub.stream("conversation-1")).thenReturn(Flux.empty());

        webTestClient.get()
                .uri("/api/conversations/conversation-1/stream")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }

    @Test
    void feedbackReturnsWrappedSuccessResponse() {
        conversationApplicationService.feedbackResponse = new ConversationFeedback(
                "conversation-1",
                "ACCEPTED",
                "used_as_is",
                "Good enough to use directly.",
                AgentType.TRAVEL_PLANNER,
                "Hangzhou",
                2,
                "1800 CNY",
                true,
                java.util.Map.of("knowledgeHintCount", 5),
                java.time.Instant.parse("2026-04-07T00:00:00Z"),
                java.time.Instant.parse("2026-04-07T00:00:00Z")
        );

        webTestClient.post()
                .uri("/api/conversations/conversation-1/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "label": "ACCEPTED",
                          "reasonCode": "used_as_is",
                          "note": "Good enough to use directly."
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo("0000")
                .jsonPath("$.data.conversationId").isEqualTo("conversation-1")
                .jsonPath("$.data.label").isEqualTo("ACCEPTED");
    }

    @Test
    void exportFeedbackDatasetReturnsWrappedSuccessResponse() {
        conversationApplicationService.exportFeedbackDatasetResponse = List.of();

        webTestClient.get()
                .uri("/api/conversations/feedback/export?limit=50")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo("0000")
                .jsonPath("$.data").isArray();
    }

    @Test
    void feedbackLoopSummaryReturnsWrappedSuccessResponse() {
        conversationApplicationService.feedbackLoopSummaryResponse = new FeedbackLoopSummaryResponse(
                java.time.Instant.parse("2026-04-07T01:00:00Z"),
                50,
                12,
                5,
                4,
                3,
                41.67,
                75.0,
                10,
                83.33,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        webTestClient.get()
                .uri("/api/conversations/feedback/summary?limit=50")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo("0000")
                .jsonPath("$.data.sampleCount").isEqualTo(12)
                .jsonPath("$.data.usableRatePct").isEqualTo(75.0);
    }

    private static class StubConversationApplicationService extends ConversationApplicationService {

        private ChatResponse chatResponse;
        private ConversationFeedback feedbackResponse;
        private List<FeedbackDatasetRecord> exportFeedbackDatasetResponse = List.of();
        private FeedbackLoopSummaryResponse feedbackLoopSummaryResponse;

        StubConversationApplicationService() {
            super(null, null, null);
        }

        @Override
        public ChatResponse chat(com.travalagent.app.dto.ChatRequest request) {
            return chatResponse;
        }

        @Override
        public ConversationFeedback saveFeedback(String conversationId, com.travalagent.app.dto.ConversationFeedbackRequest request) {
            return feedbackResponse;
        }

        @Override
        public List<FeedbackDatasetRecord> exportFeedbackDataset(int limit) {
            return exportFeedbackDatasetResponse;
        }

        @Override
        public FeedbackLoopSummaryResponse feedbackLoopSummary(int limit) {
            return feedbackLoopSummaryResponse;
        }
    }
}
