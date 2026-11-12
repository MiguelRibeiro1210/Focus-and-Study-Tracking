package com.focusdesktop.infrastructure.config;

import com.focusdesktop.application.port.DomainEventOutbox;
import com.focusdesktop.application.port.SessionSettingsProvider;
import com.focusdesktop.application.usecase.GetActiveSessionUseCase;
import com.focusdesktop.application.usecase.CompleteDueIntervalsUseCase;
import com.focusdesktop.application.usecase.PauseSessionUseCase;
import com.focusdesktop.application.usecase.ResumeSessionUseCase;
import com.focusdesktop.application.usecase.SkipBreakUseCase;
import com.focusdesktop.application.usecase.StartNextIntervalUseCase;
import com.focusdesktop.application.usecase.StartSessionUseCase;
import com.focusdesktop.application.usecase.StopSessionUseCase;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import com.focusdesktop.domain.service.SessionIntervalScheduleService;
import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ApplicationConfiguration {
    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    SessionSettingsProvider sessionSettingsProvider() {
        return Optional::empty;
    }

    @Bean
    StartSessionUseCase startSessionUseCase(FocusSessionRepository sessions,
            SessionSettingsProvider settingsProvider, DomainEventOutbox outbox, Clock clock) {
        return new StartSessionUseCase(sessions, settingsProvider, outbox, clock);
    }

    @Bean
    PauseSessionUseCase pauseSessionUseCase(FocusSessionRepository sessions,
            DomainEventOutbox outbox, Clock clock, CompleteDueIntervalsUseCase completeDueIntervals) {
        return new PauseSessionUseCase(sessions, outbox, clock, completeDueIntervals);
    }

    @Bean
    ResumeSessionUseCase resumeSessionUseCase(FocusSessionRepository sessions,
            DomainEventOutbox outbox, Clock clock) {
        return new ResumeSessionUseCase(sessions, outbox, clock);
    }

    @Bean
    StopSessionUseCase stopSessionUseCase(FocusSessionRepository sessions,
            DomainEventOutbox outbox, Clock clock, CompleteDueIntervalsUseCase completeDueIntervals) {
        return new StopSessionUseCase(sessions, outbox, clock, completeDueIntervals);
    }

    @Bean
    GetActiveSessionUseCase getActiveSessionUseCase(FocusSessionRepository sessions, Clock clock) {
        return new GetActiveSessionUseCase(sessions, clock);
    }

    @Bean
    SessionIntervalScheduleService sessionIntervalScheduleService() {
        return new SessionIntervalScheduleService();
    }

    @Bean
    StartNextIntervalUseCase startNextIntervalUseCase(FocusSessionRepository sessions,
            DomainEventOutbox outbox, Clock clock) {
        return new StartNextIntervalUseCase(sessions, outbox, clock);
    }

    @Bean
    SkipBreakUseCase skipBreakUseCase(FocusSessionRepository sessions, DomainEventOutbox outbox, Clock clock) {
        return new SkipBreakUseCase(sessions, outbox, clock);
    }

    @Bean
    CompleteDueIntervalsUseCase completeDueIntervalsUseCase(FocusSessionRepository sessions,
            DomainEventOutbox outbox, SessionIntervalScheduleService schedule, Clock clock) {
        return new CompleteDueIntervalsUseCase(sessions, outbox, schedule, clock);
    }
}
