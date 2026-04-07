package com.travalagent.domain.event;

import com.travalagent.domain.model.entity.TimelineEvent;

public interface TimelinePublisher {

    void publish(TimelineEvent event);
}
