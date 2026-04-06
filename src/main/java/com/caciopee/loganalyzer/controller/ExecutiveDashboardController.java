package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.dto.ExecutiveDashboardResponse;
import com.caciopee.loganalyzer.service.ExecutiveDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/executive")
public class ExecutiveDashboardController {

    private final ExecutiveDashboardService executiveDashboardService;

    public ExecutiveDashboardController(ExecutiveDashboardService executiveDashboardService) {
        this.executiveDashboardService = executiveDashboardService;
    }

    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    @GetMapping("/dashboard")
    public ExecutiveDashboardResponse getExecutiveDashboard() {
        return executiveDashboardService.buildExecutiveDashboard();
    }
}