package com.focusdesktop.infrastructure.scheduling;

import com.focusdesktop.application.usecase.DetectActiveApplicationUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ApplicationMonitoringScheduler {
    private final DetectActiveApplicationUseCase detect;
    public ApplicationMonitoringScheduler(DetectActiveApplicationUseCase detect) { this.detect = detect; }

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

    @Scheduled(fixedDelayString = "${focusdesktop.blocking.monitor-delay-ms:2000}", scheduler = "blockingTaskScheduler")
    public void monitor() { detect.execute(); }
}
