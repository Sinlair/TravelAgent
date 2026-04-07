package com.travalagent.domain.service;

import com.travalagent.domain.model.entity.ConversationMessage;
import com.travalagent.domain.model.entity.TaskMemory;

import java.util.List;

public interface TaskMemoryExtractor {

    TaskMemory extract(TaskMemory existing, List<ConversationMessage> messages);
}
