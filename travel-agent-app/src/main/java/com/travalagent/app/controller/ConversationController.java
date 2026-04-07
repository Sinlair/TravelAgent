package com.travalagent.app.controller;

import com.travalagent.app.dto.ChatRequest;
import com.travalagent.app.dto.ChatResponse;
import com.travalagent.app.dto.ConversationDetailResponse;
import com.travalagent.app.dto.ConversationFeedbackRequest;
import com.travalagent.app.dto.FeedbackDatasetRecord;
import com.travalagent.app.dto.FeedbackLoopSummaryResponse;
import com.travalagent.app.service.ConversationApplicationService;
import com.travalagent.app.stream.ConversationStreamHub;
import com.travalagent.domain.model.entity.ConversationFeedback;
import com.travalagent.domain.model.entity.ConversationSession;
import com.travalagent.domain.model.entity.TimelineEvent;
import com.travalagent.types.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/feedback/export")
    public ApiResponse<List<FeedbackDatasetRecord>> exportFeedbackDataset(
            @RequestParam(defaultValue = "200") int limit
    ) {
        return ApiResponse.success(conversationApplicationService.exportFeedbackDataset(limit));
    }

    @GetMapping("/feedback/summary")
    public ApiResponse<FeedbackLoopSummaryResponse> feedbackLoopSummary(
            @RequestParam(defaultValue = "200") int limit
    ) {
        return ApiResponse.success(conversationApplicationService.feedbackLoopSummary(limit));
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationDetailResponse> detail(@PathVariable String conversationId) {
        return ApiResponse.success(conversationApplicationService.conversationDetail(conversationId));
    }

    @PostMapping("/{conversationId}/feedback")
    public ApiResponse<ConversationFeedback> feedback(
            @PathVariable String conversationId,
            @Valid @RequestBody ConversationFeedbackRequest request
    ) {
        return ApiResponse.success(conversationApplicationService.saveFeedback(conversationId, request));
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
