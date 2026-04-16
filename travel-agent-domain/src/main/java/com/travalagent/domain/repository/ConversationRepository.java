package com.travalagent.domain.repository;

import com.travalagent.domain.model.entity.ConversationMessage;
import com.travalagent.domain.model.entity.ConversationFeedback;
import com.travalagent.domain.model.entity.ConversationImageContext;
import com.travalagent.domain.model.entity.ConversationSession;
import com.travalagent.domain.model.entity.TaskMemory;
import com.travalagent.domain.model.entity.TimelineEvent;
import com.travalagent.domain.model.entity.TravelPlan;
import com.travalagent.domain.model.entity.TravelPlanVersionSnapshot;

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

    Optional<ConversationFeedback> findFeedback(String conversationId);

    List<ConversationFeedback> listFeedback(int limit);

    void saveFeedback(ConversationFeedback feedback);

    Optional<ConversationImageContext> findPendingImageContext(String conversationId);

    void savePendingImageContext(ConversationImageContext imageContext);

    void deletePendingImageContext(String conversationId);

    Optional<TravelPlan> findTravelPlan(String conversationId);

    void saveTravelPlan(TravelPlan travelPlan);

    List<TravelPlanVersionSnapshot> listTravelPlanVersions(String conversationId, int limit);

    void saveTravelPlanVersion(TravelPlanVersionSnapshot versionSnapshot);

    void saveTimeline(TimelineEvent timelineEvent);

    List<TimelineEvent> findTimeline(String conversationId);

    void deleteConversation(String conversationId);
}

