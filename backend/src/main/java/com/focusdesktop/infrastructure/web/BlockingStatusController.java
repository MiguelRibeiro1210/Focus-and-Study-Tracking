package com.focusdesktop.infrastructure.web;

import com.focusdesktop.application.usecase.DetectActiveApplicationUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BlockingStatusController {
    private final DetectActiveApplicationUseCase detect;
    public BlockingStatusController(DetectActiveApplicationUseCase detect) { this.detect = detect; }
    @GetMapping("/api/v1/blocking-status")
    public DetectActiveApplicationUseCase.Status status() { return detect.status(); }
}
