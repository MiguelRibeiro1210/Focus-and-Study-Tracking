package com.focusdesktop.application.usecase;

import com.focusdesktop.application.port.DomainEventOutbox;
import com.focusdesktop.domain.event.DomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordDistractionEventUseCase {
    private final DomainEventOutbox outbox;
    public RecordDistractionEventUseCase(DomainEventOutbox outbox) { this.outbox = outbox; }
    @Transactional
    public void execute(DomainEvent event) { outbox.append(event); }
}
