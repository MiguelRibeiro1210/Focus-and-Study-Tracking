package com.focusdesktop.infrastructure.scheduling;

import com.focusdesktop.application.usecase.CompleteDueIntervalsUseCase;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionIntervalScheduler {
    private static final Logger LOGGER = Logger.getLogger(SessionIntervalScheduler.class.getName());
    private final CompleteDueIntervalsUseCase completeDueIntervals;

    public SessionIntervalScheduler(CompleteDueIntervalsUseCase completeDueIntervals) {
        this.completeDueIntervals = completeDueIntervals;
    }

    @Scheduled(fixedDelayString = "${focusdesktop.session.interval-check-delay-ms:5000}")
    public void completeDueIntervals() {
        try {
            completeDueIntervals.execute();
        } catch (OptimisticLockingFailureException exception) {
            LOGGER.log(Level.FINE, "A concurrent session update won the interval completion race", exception);
        }
    }
}
