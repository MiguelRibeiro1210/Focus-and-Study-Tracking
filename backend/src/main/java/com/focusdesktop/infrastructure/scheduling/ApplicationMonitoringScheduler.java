package com.focusdesktop.infrastructure.scheduling;

import com.focusdesktop.application.usecase.DetectActiveApplicationUseCase;
import com.focusdesktop.application.usecase.ApplicationProtectionCoordinator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ApplicationMonitoringScheduler {
    private final DetectActiveApplicationUseCase detect;
    private final ApplicationProtectionCoordinator protection;
    public ApplicationMonitoringScheduler(DetectActiveApplicationUseCase detect, ApplicationProtectionCoordinator protection) {
        this.detect = detect; this.protection = protection;
    }

    @Bean
    public ThreadPoolTaskScheduler blockingTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("blocking-monitor-"); return scheduler;
    }

    // Keep existing timer/outbox schedules on their own scheduler rather than this OS-query worker.
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("application-scheduler-"); return scheduler;
    }

    @Bean
    public ThreadPoolTaskScheduler websiteTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("website-protection-"); return scheduler;
    }

    @Bean
    public ThreadPoolTaskScheduler protectionTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("protection-reconcile-"); return scheduler;
    }

    @Scheduled(fixedDelayString = "${focusdesktop.blocking.monitor-delay-ms:2000}", scheduler = "blockingTaskScheduler")
    public void monitor() {
        try {
            if (protection.allowsObservation()) detect.execute().ifPresent(protection::observe);
            else detect.idleForProtection();
        } catch (RuntimeException exception) { protection.reconcile(); }
    }

    @Scheduled(fixedDelay = 500, scheduler = "protectionTaskScheduler")
    public void reconcile() { protection.reconcile(); }
}
