package com.focusdesktop.infrastructure.web;

import com.focusdesktop.application.dto.DistractionLogPage;
import com.focusdesktop.application.usecase.QueryDistractionLogUseCase;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
public class DistractionController {
    private final QueryDistractionLogUseCase query;
    public DistractionController(QueryDistractionLogUseCase query) { this.query = query; }
    @GetMapping("/api/v1/distractions")
    public DistractionLogPage list(@RequestParam(required = false) UUID sessionId,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return query.execute(sessionId, from, to, page, size);
    }
}
