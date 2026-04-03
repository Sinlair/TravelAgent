package com.xx2201.travel.agent.domain.service;

import com.xx2201.travel.agent.domain.model.entity.ConversationMessage;
import com.xx2201.travel.agent.domain.model.entity.TaskMemory;

import java.util.List;

public interface TaskMemoryExtractor {

    TaskMemory extract(TaskMemory existing, List<ConversationMessage> messages);
}
