package com.focusdesktop.infrastructure.web;

import com.focusdesktop.application.port.ApplicationCatalog;
import com.focusdesktop.application.usecase.ListApplicationsUseCase;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationCatalogController {
    private final ListApplicationsUseCase applications;
    public ApplicationCatalogController(ListApplicationsUseCase applications) { this.applications = applications; }
    @GetMapping public ApplicationCatalog.Scan list(@RequestParam(defaultValue = "false") boolean refresh) { return applications.execute(refresh); }
}
