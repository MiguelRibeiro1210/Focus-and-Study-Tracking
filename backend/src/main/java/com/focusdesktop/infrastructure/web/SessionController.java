package com.focusdesktop.infrastructure.web;

import com.focusdesktop.application.usecase.GetActiveSessionUseCase;
import com.focusdesktop.application.usecase.GetSessionHistoryUseCase;
import com.focusdesktop.application.usecase.ClearSessionHistoryUseCase;
import com.focusdesktop.application.usecase.CleanupExpiredSessionHistoryUseCase;
import com.focusdesktop.application.usecase.SkipBreakUseCase;
import com.focusdesktop.application.usecase.StartNextIntervalUseCase;
import com.focusdesktop.application.usecase.PauseSessionUseCase;
import com.focusdesktop.application.usecase.ResumeSessionUseCase;
import com.focusdesktop.application.usecase.StartSessionUseCase;
import com.focusdesktop.application.usecase.StopSessionUseCase;
import com.focusdesktop.application.usecase.commands.SessionIdCommand;
import com.focusdesktop.application.usecase.commands.StartSessionCommand;
import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.SessionType;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {
    private final StartSessionUseCase start;
    private final GetActiveSessionUseCase active;
    private final PauseSessionUseCase pause;
    private final ResumeSessionUseCase resume;
    private final StopSessionUseCase stop;
    private final StartNextIntervalUseCase startNextInterval;
    private final SkipBreakUseCase skipBreak;
    private final GetSessionHistoryUseCase history;
    private final ClearSessionHistoryUseCase clearHistory;
    private final CleanupExpiredSessionHistoryUseCase cleanupExpiredHistory;

    public SessionController(StartSessionUseCase start, GetActiveSessionUseCase active,
            PauseSessionUseCase pause, ResumeSessionUseCase resume, StopSessionUseCase stop,
            StartNextIntervalUseCase startNextInterval, SkipBreakUseCase skipBreak,
            GetSessionHistoryUseCase history, ClearSessionHistoryUseCase clearHistory,
            CleanupExpiredSessionHistoryUseCase cleanupExpiredHistory) {
        this.start = start;
        this.active = active;
        this.pause = pause;
        this.resume = resume;
        this.stop = stop;
        this.startNextInterval = startNextInterval;
        this.skipBreak = skipBreak;
        this.history = history;
        this.clearHistory = clearHistory;
        this.cleanupExpiredHistory = cleanupExpiredHistory;
    }

    @PostMapping
    public ResponseEntity<SessionResponse> start(@RequestBody StartSessionRequest request) {
        if (request == null || request.sessionType() == null || request.projectName() == null) {
            throw new IllegalArgumentException("sessionType and projectName are required");
        }
        SessionResponse response = SessionResponse.from(start.execute(
                new StartSessionCommand(request.projectName(), request.sessionType(), request.timerOverride() == null
                        ? null : new StartSessionCommand.TimerOverride(request.timerOverride().workSeconds(),
                        request.timerOverride().shortBreakSeconds(), request.timerOverride().longBreakSeconds()))));
        return ResponseEntity.created(URI.create("/api/v1/sessions/" + response.sessionId())).body(response);
    }

    @GetMapping("/active")
    public ResponseEntity<SessionResponse> active() {
        return active.execute().map(SessionResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping
    public com.focusdesktop.application.dto.SessionHistoryPage history(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return history.execute(from, to, status, page, size);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory() {
        clearHistory.execute();
        return ResponseEntity.noContent().build();
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/history/expired")
    public CleanupExpiredHistoryResponse cleanupExpiredHistory() {
        return new CleanupExpiredHistoryResponse(cleanupExpiredHistory.execute());
    }

    @PostMapping("/{id}/pause")
    public SessionResponse pause(@PathVariable UUID id) {
        return SessionResponse.from(pause.execute(command(id)));
    }

    @PostMapping("/{id}/resume")
    public SessionResponse resume(@PathVariable UUID id) {
        return SessionResponse.from(resume.execute(command(id)));
    }

    @PostMapping("/{id}/stop")
    public SessionResponse stop(@PathVariable UUID id) {
        return SessionResponse.from(stop.execute(command(id)));
    }

    @PostMapping("/{id}/intervals/start")
    public SessionResponse startNextInterval(@PathVariable UUID id) {
        return SessionResponse.from(startNextInterval.execute(command(id)));
    }

    @PostMapping("/{id}/skip-break")
    public SessionResponse skipBreak(@PathVariable UUID id) {
        return SessionResponse.from(skipBreak.execute(command(id)));
    }

    private static SessionIdCommand command(UUID id) {
        return new SessionIdCommand(new SessionId(id));
    }

    public record StartSessionRequest(SessionType sessionType, String projectName, TimerOverrideRequest timerOverride) { }
    public record TimerOverrideRequest(Long workSeconds, Long shortBreakSeconds, Long longBreakSeconds) { }
    public record CleanupExpiredHistoryResponse(int deletedSessions) { }
}
