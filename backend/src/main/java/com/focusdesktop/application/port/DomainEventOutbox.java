package com.focusdesktop.application.port;

import com.focusdesktop.domain.event.DomainEvent;

public interface DomainEventOutbox {
    void append(DomainEvent event);
}
