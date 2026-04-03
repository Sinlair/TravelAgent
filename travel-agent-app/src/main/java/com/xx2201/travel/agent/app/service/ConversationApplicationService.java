package com.xx2201.travel.agent.app.service;

import com.xx2201.travel.agent.app.dto.ChatRequest;
import com.xx2201.travel.agent.app.dto.ChatResponse;
import com.xx2201.travel.agent.app.dto.ConversationDetailResponse;
import com.xx2201.travel.agent.domain.model.entity.ConversationSession;
import com.xx2201.travel.agent.domain.model.entity.TaskMemory;
import com.xx2201.travel.agent.domain.repository.ConversationRepository;
import com.xx2201.travel.agent.infrastructure.gateway.tool.AmapMcpGateway;
import io.micrometer.observation.annotation.Observed;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Observed(name = "travel.agent.conversation.service")
public class ConversationApplicationService {

    private final ConversationWorkflow conversationWorkflow;
    private final ConversationRepository conversationRepository;
    private final AmapMcpGateway amapMcpGateway;

    public ConversationApplicationService(
            ConversationWorkflow conversationWorkflow,
            ConversationRepository conversationRepository,
            AmapMcpGateway amapMcpGateway
    ) {
        this.conversationWorkflow = conversationWorkflow;
        this.conversationRepository = conversationRepository;
        this.amapMcpGateway = amapMcpGateway;
    }

    public ChatResponse chat(ChatRequest request) {
        return conversationWorkflow.execute(request);
    }

    @Observed(name = "travel.agent.list-conversations")
    public List<ConversationSession> listConversations() {
        return conversationRepository.listConversations();
    }

    @Observed(name = "travel.agent.conversation-detail")
    public ConversationDetailResponse conversationDetail(String conversationId) {
        var session = conversationRepository.findConversation(conversationId).orElseThrow();
        return new ConversationDetailResponse(
                session,
                conversationRepository.findMessages(conversationId),
                conversationRepository.findTimeline(conversationId),
                conversationRepository.findTaskMemory(conversationId).orElse(TaskMemory.empty(conversationId)),
                conversationRepository.findTravelPlan(conversationId).orElse(null)
        );
    }

    @Observed(name = "travel.agent.delete-conversation")
    public void deleteConversation(String conversationId) {
        conversationRepository.deleteConversation(conversationId);
        amapMcpGateway.clearConversationCache(conversationId);
    }
}
