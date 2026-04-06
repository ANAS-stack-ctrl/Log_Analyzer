package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.dto.AutoIncidentDetectionResponse;
import com.caciopee.loganalyzer.service.AutoIncidentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auto-incidents")
public class AutoIncidentController {

    private final AutoIncidentService autoIncidentService;

    public AutoIncidentController(AutoIncidentService autoIncidentService) {
        this.autoIncidentService = autoIncidentService;
    }

    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    @PostMapping("/detect")
    public AutoIncidentDetectionResponse detect() {
        return autoIncidentService.detectAndCreate();
    }
}