package com.xx2201.travel.agent.app.stream;

import com.xx2201.travel.agent.domain.event.TimelinePublisher;
import com.xx2201.travel.agent.domain.model.entity.TimelineEvent;
import com.xx2201.travel.agent.domain.repository.ConversationRepository;
import org.springframework.stereotype.Component;

@Component
public class ReactiveTimelinePublisher implements TimelinePublisher {

    private final ConversationRepository conversationRepository;
    private final ConversationStreamHub conversationStreamHub;

    public ReactiveTimelinePublisher(
            ConversationRepository conversationRepository,
            ConversationStreamHub conversationStreamHub
    ) {
        this.conversationRepository = conversationRepository;
        this.conversationStreamHub = conversationStreamHub;
    }

    @Override
    public void publish(TimelineEvent event) {
        conversationRepository.saveTimeline(event);
        conversationStreamHub.publish(event);
    }
}
