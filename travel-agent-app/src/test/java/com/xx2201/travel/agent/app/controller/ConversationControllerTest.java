package com.xx2201.travel.agent.app.controller;

import com.xx2201.travel.agent.app.dto.ChatResponse;
import com.xx2201.travel.agent.app.service.ConversationApplicationService;
import com.xx2201.travel.agent.app.stream.ConversationStreamHub;
import com.xx2201.travel.agent.domain.model.entity.TaskMemory;
import com.xx2201.travel.agent.domain.model.valobj.AgentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationControllerTest {

    private final ConversationApplicationService conversationApplicationService = mock(ConversationApplicationService.class);
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
        when(conversationApplicationService.chat(any())).thenReturn(new ChatResponse(
                "conversation-1",
                AgentType.WEATHER,
                "Sunny today",
                TaskMemory.empty("conversation-1"),
                null,
                List.of()
        ));

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
    void streamReturnsEventStream() {
        when(conversationStreamHub.stream("conversation-1")).thenReturn(Flux.empty());

        webTestClient.get()
                .uri("/api/conversations/conversation-1/stream")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }
}
