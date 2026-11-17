package com.focusdesktop.infrastructure.web;

import com.focusdesktop.application.dto.AnalyticsSummary;
import com.focusdesktop.application.usecase.GetAnalyticsSummaryUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    private final GetAnalyticsSummaryUseCase summary;

    public AnalyticsController(GetAnalyticsSummaryUseCase summary) {
        this.summary = summary;
    }

    @GetMapping("/summary")
    public AnalyticsSummary summary(@RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to) {
        return summary.execute(from, to);
    }
}
