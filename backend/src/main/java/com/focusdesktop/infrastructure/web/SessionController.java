package com.focusdesktop.infrastructure.web;

import com.focusdesktop.application.usecase.GetActiveSessionUseCase;
import com.focusdesktop.application.usecase.PauseSessionUseCase;
import com.focusdesktop.application.usecase.ResumeSessionUseCase;
import com.focusdesktop.application.usecase.StartSessionUseCase;
import com.focusdesktop.application.usecase.StopSessionUseCase;
import com.focusdesktop.application.usecase.commands.SessionIdCommand;
import com.focusdesktop.application.usecase.commands.StartSessionCommand;
import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.SessionType;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {
    private final StartSessionUseCase start;
    private final GetActiveSessionUseCase active;
    private final PauseSessionUseCase pause;
    private final ResumeSessionUseCase resume;
    private final StopSessionUseCase stop;

    public SessionController(StartSessionUseCase start, GetActiveSessionUseCase active,
            PauseSessionUseCase pause, ResumeSessionUseCase resume, StopSessionUseCase stop) {
        this.start = start;
        this.active = active;
        this.pause = pause;
        this.resume = resume;
        this.stop = stop;
    }

    @PostMapping
    public ResponseEntity<SessionResponse> start(@RequestBody StartSessionRequest request) {
        if (request == null || request.sessionType() == null) {
            throw new IllegalArgumentException("sessionType is required");
        }
        SessionResponse response = SessionResponse.from(start.execute(new StartSessionCommand(request.sessionType())));
        return ResponseEntity.created(URI.create("/api/v1/sessions/" + response.sessionId())).body(response);
    }

    @GetMapping("/active")
    public ResponseEntity<SessionResponse> active() {
        return active.execute().map(SessionResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
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

    private static SessionIdCommand command(UUID id) {
        return new SessionIdCommand(new SessionId(id));
    }

    public record StartSessionRequest(SessionType sessionType) { }
}
