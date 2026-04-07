package com.travalagent.app.stream;

import com.travalagent.domain.event.TimelinePublisher;
import com.travalagent.domain.model.entity.TimelineEvent;
import com.travalagent.domain.repository.ConversationRepository;
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
