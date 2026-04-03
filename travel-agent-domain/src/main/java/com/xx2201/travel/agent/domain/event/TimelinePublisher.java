package com.xx2201.travel.agent.domain.event;

import com.xx2201.travel.agent.domain.model.entity.TimelineEvent;

public interface TimelinePublisher {

    void publish(TimelineEvent event);
}
