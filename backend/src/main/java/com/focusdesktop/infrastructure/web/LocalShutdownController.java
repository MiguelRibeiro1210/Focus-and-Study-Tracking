package com.focusdesktop.infrastructure.web;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal, bearer-authenticated shutdown hook used by the owning Tauri shell. */
@RestController
public class LocalShutdownController {
    private final AtomicBoolean shutdownRequested = new AtomicBoolean();
    private final ConfigurableApplicationContext applicationContext;

    public LocalShutdownController(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostMapping("/internal/shutdown")
    public ResponseEntity<Void> shutdown() {
        if (!shutdownRequested.compareAndSet(false, true)) {
            return ResponseEntity.noContent().build();
        }
        Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "focusdesktop-shutdown");
            thread.setDaemon(true);
            return thread;
        }).schedule(() -> {
            applicationContext.close();
            System.exit(0);
        }, 200, TimeUnit.MILLISECONDS);
        return ResponseEntity.noContent().build();
    }
}
