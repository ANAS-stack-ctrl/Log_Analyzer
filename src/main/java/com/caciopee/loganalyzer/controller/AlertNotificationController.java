package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.dto.AlertActionRequest;
import com.caciopee.loganalyzer.dto.AutoIncidentDetectionResponse;
import com.caciopee.loganalyzer.entity.AlertNotification;
import com.caciopee.loganalyzer.service.AlertEngineService;
import com.caciopee.loganalyzer.service.AlertNotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/alerts")
public class AlertNotificationController {

    private final AlertNotificationService alertNotificationService;
    private final AlertEngineService alertEngineService;

    public AlertNotificationController(AlertNotificationService alertNotificationService,
                                       AlertEngineService alertEngineService) {
        this.alertNotificationService = alertNotificationService;
        this.alertEngineService = alertEngineService;
    }

    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    @PostMapping("/run-checks")
    public AutoIncidentDetectionResponse runChecks() {
        return alertEngineService.runAlertChecks();
    }

    @PreAuthorize("hasAnyRole('VIEWER','ANALYST','MANAGER','ADMIN')")
    @GetMapping
    public List<AlertNotification> getAll(@RequestParam(required = false) String status) {
        return alertNotificationService.getByStatus(status);
    }

    @PreAuthorize("hasAnyRole('ANALYST','MANAGER','ADMIN')")
    @PostMapping("/{id}/ack")
    public AlertNotification acknowledge(@PathVariable Long id,
                                         @RequestBody AlertActionRequest request) {
        return alertNotificationService.acknowledge(id, request.getUsername());
    }

    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    @PostMapping("/{id}/resolve")
    public AlertNotification resolve(@PathVariable Long id,
                                     @RequestBody AlertActionRequest request) {
        return alertNotificationService.resolve(id, request.getUsername());
    }
}