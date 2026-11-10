package com.focusdesktop.domain.event;

import com.focusdesktop.domain.model.TimeStamp;

public interface DomainEvent {
    TimeStamp occurredAt();
}
