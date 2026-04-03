package com.xx2201.travel.agent.app.stream;

import com.xx2201.travel.agent.domain.model.entity.TimelineEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConversationStreamHub {

    private final Map<String, Sinks.Many<TimelineEvent>> sinks = new ConcurrentHashMap<>();

    public void publish(TimelineEvent event) {
        sinks.computeIfAbsent(event.conversationId(), ignored -> Sinks.many().multicast().onBackpressureBuffer())
                .tryEmitNext(event);
    }

    public Flux<TimelineEvent> stream(String conversationId) {
        return sinks.computeIfAbsent(conversationId, ignored -> Sinks.many().multicast().onBackpressureBuffer())
                .asFlux();
    }

    public void complete(String conversationId) {
        Sinks.Many<TimelineEvent> sink = sinks.remove(conversationId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }
}
