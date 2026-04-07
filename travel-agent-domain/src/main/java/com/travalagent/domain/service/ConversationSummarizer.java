package com.travalagent.domain.service;

import com.travalagent.domain.model.entity.ConversationMessage;

import java.util.List;

public interface ConversationSummarizer {

    String summarize(String existingSummary, List<ConversationMessage> messages);
}
