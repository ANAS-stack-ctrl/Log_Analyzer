package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.dto.*;
import com.caciopee.loganalyzer.service.IncidentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PreAuthorize("hasAnyRole('ANALYST','MANAGER','ADMIN')")
    @PostMapping
    public IncidentResponse create(@RequestBody IncidentCreateRequest request) {
        return incidentService.createIncident(request);
    }

    @PreAuthorize("hasAnyRole('VIEWER','ANALYST','MANAGER','ADMIN')")
    @GetMapping
    public List<IncidentResponse> getAll() {
        return incidentService.getAllIncidents();
    }

    @PreAuthorize("hasAnyRole('VIEWER','ANALYST','MANAGER','ADMIN')")
    @GetMapping("/{id}")
    public IncidentResponse getOne(@PathVariable Long id) {
        return incidentService.getIncident(id);
    }

    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    @PutMapping("/{id}")
    public IncidentResponse update(@PathVariable Long id,
                                   @RequestBody IncidentUpdateRequest request) {
        return incidentService.updateIncident(id, request);
    }

    @PreAuthorize("hasAnyRole('ANALYST','MANAGER','ADMIN')")
    @PostMapping("/{id}/logs/{logId}")
    public IncidentResponse linkLog(@PathVariable Long id,
                                    @PathVariable Long logId,
                                    @RequestParam String updatedBy) {
        return incidentService.linkLog(id, logId, updatedBy);
    }

    @PreAuthorize("hasAnyRole('ANALYST','MANAGER','ADMIN')")
    @PostMapping("/{id}/comments")
    public IncidentCommentResponse comment(@PathVariable Long id,
                                           @RequestBody IncidentCommentRequest request) {
        return incidentService.addComment(id, request);
    }

    @PreAuthorize("hasAnyRole('VIEWER','ANALYST','MANAGER','ADMIN')")
    @GetMapping("/{id}/comments")
    public List<IncidentCommentResponse> comments(@PathVariable Long id) {
        return incidentService.getComments(id);
    }
}