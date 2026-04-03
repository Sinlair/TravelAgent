package com.xx2201.travel.agent.domain.repository;

import com.xx2201.travel.agent.domain.model.entity.ConversationMessage;
import com.xx2201.travel.agent.domain.model.entity.ConversationSession;
import com.xx2201.travel.agent.domain.model.entity.TaskMemory;
import com.xx2201.travel.agent.domain.model.entity.TimelineEvent;
import com.xx2201.travel.agent.domain.model.entity.TravelPlan;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository {

    Optional<ConversationSession> findConversation(String conversationId);

    List<ConversationSession> listConversations();

    void saveConversation(ConversationSession session);

    void saveMessage(ConversationMessage message);

    List<ConversationMessage> findMessages(String conversationId);

    List<ConversationMessage> findRecentMessages(String conversationId, int limit);

    Optional<TaskMemory> findTaskMemory(String conversationId);

    void saveTaskMemory(TaskMemory taskMemory);

    Optional<TravelPlan> findTravelPlan(String conversationId);

    void saveTravelPlan(TravelPlan travelPlan);

    void saveTimeline(TimelineEvent timelineEvent);

    List<TimelineEvent> findTimeline(String conversationId);

    void deleteConversation(String conversationId);
}

