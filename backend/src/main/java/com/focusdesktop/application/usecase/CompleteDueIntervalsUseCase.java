package com.focusdesktop.application.usecase;

import com.focusdesktop.application.port.DomainEventOutbox;
import com.focusdesktop.domain.event.IntervalCompleted;
import com.focusdesktop.domain.event.IntervalStarted;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.IntervalState;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.TimeStamp;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import com.focusdesktop.domain.service.SessionIntervalScheduleService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

public class CompleteDueIntervalsUseCase {
    private final FocusSessionRepository sessions;
    private final DomainEventOutbox outbox;
    private final SessionIntervalScheduleService schedule;
    private final Clock clock;

    public CompleteDueIntervalsUseCase(FocusSessionRepository sessions, DomainEventOutbox outbox,
            SessionIntervalScheduleService schedule, Clock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public int execute() {
        TimeStamp asOf = new TimeStamp(clock.instant());
        var active = sessions.findActive();
        if (active.isEmpty()) {
            return 0;
        }

        FocusSession session = active.get();
        List<DueTransition> transitions = new ArrayList<>();
        while (schedule.dueAt(session, asOf).isPresent()) {
            TimeStamp dueAt = schedule.dueAt(session, asOf).orElseThrow();
            var completedType = session.currentInterval().type();
            var nextType = schedule.nextType(session);
            session.completeInterval(nextType, dueAt);
            transitions.add(new DueTransition(completedType, nextType, dueAt,
                    session.state(), session.currentInterval().remainingDuration(dueAt),
                    session.state() == SessionState.RUNNING
                            && session.currentInterval().state() == IntervalState.RUNNING));
        }

        if (transitions.isEmpty()) {
            return 0;
        }

        sessions.save(session);
        for (DueTransition transition : transitions) {
            outbox.append(new IntervalCompleted(session.id(), transition.completedType(),
                    transition.nextType(), transition.resultingState(), transition.remainingDuration(),
                    transition.occurredAt()));
            if (transition.nextStarted()) {
                outbox.append(new IntervalStarted(session.id(), transition.nextType(),
                        transition.resultingState(), transition.remainingDuration(), transition.occurredAt()));
            }
        }
        return transitions.size();
    }

    private record DueTransition(SessionType completedType,
                                 SessionType nextType,
                                 TimeStamp occurredAt,
                                 SessionState resultingState,
                                 Duration remainingDuration,
                                 boolean nextStarted) { }
}
