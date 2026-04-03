package com.xx2201.travel.agent.app.controller;

import com.xx2201.travel.agent.app.dto.ChatRequest;
import com.xx2201.travel.agent.app.dto.ChatResponse;
import com.xx2201.travel.agent.app.dto.ConversationDetailResponse;
import com.xx2201.travel.agent.app.service.ConversationApplicationService;
import com.xx2201.travel.agent.app.stream.ConversationStreamHub;
import com.xx2201.travel.agent.domain.model.entity.ConversationSession;
import com.xx2201.travel.agent.domain.model.entity.TimelineEvent;
import com.xx2201.travel.agent.types.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationApplicationService conversationApplicationService;
    private final ConversationStreamHub conversationStreamHub;

    public ConversationController(
            ConversationApplicationService conversationApplicationService,
            ConversationStreamHub conversationStreamHub
    ) {
        this.conversationApplicationService = conversationApplicationService;
        this.conversationStreamHub = conversationStreamHub;
    }

    @PostMapping("/chat")
    public Mono<ApiResponse<ChatResponse>> chat(@Valid @RequestBody ChatRequest request) {
        return Mono.fromCallable(() -> ApiResponse.success(conversationApplicationService.chat(request)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping
    public ApiResponse<List<ConversationSession>> listConversations() {
        return ApiResponse.success(conversationApplicationService.listConversations());
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationDetailResponse> detail(@PathVariable String conversationId) {
        return ApiResponse.success(conversationApplicationService.conversationDetail(conversationId));
    }

    @DeleteMapping("/{conversationId}")
    public ApiResponse<Void> delete(@PathVariable String conversationId) {
        conversationApplicationService.deleteConversation(conversationId);
        conversationStreamHub.complete(conversationId);
        return ApiResponse.success(null);
    }

    @GetMapping(value = "/{conversationId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<TimelineEvent>> stream(@PathVariable String conversationId) {
        return conversationStreamHub.stream(conversationId)
                .map(event -> ServerSentEvent.<TimelineEvent>builder()
                        .event(event.stage().name())
                        .data(event)
                        .build());
    }
}
