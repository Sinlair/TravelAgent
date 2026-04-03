package com.xx2201.travel.agent.domain.service;

import com.xx2201.travel.agent.domain.model.entity.ConversationMessage;

import java.util.List;

public interface ConversationSummarizer {

    String summarize(String existingSummary, List<ConversationMessage> messages);
}
