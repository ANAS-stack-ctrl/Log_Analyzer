package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.dto.LogTriageActionRequest;
import com.caciopee.loganalyzer.dto.LogTriageHistoryResponse;
import com.caciopee.loganalyzer.dto.LogTriageStateResponse;
import com.caciopee.loganalyzer.service.LogTriageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/triage/logs")
public class LogTriageController {

    private final LogTriageService logTriageService;

    public LogTriageController(LogTriageService logTriageService) {
        this.logTriageService = logTriageService;
    }

    @PreAuthorize("hasAnyRole('VIEWER','ANALYST','MANAGER','ADMIN')")
    @GetMapping("/{logId}/state")
    public LogTriageStateResponse getState(@PathVariable Long logId) {
        return logTriageService.getOrCreateState(logId);
    }

    @PreAuthorize("hasAnyRole('VIEWER','ANALYST','MANAGER','ADMIN')")
    @GetMapping("/{logId}/history")
    public List<LogTriageHistoryResponse> getHistory(@PathVariable Long logId) {
        return logTriageService.getHistory(logId);
    }

    @PreAuthorize("hasAnyRole('ANALYST','MANAGER','ADMIN')")
    @PostMapping("/{logId}/start-progress")
    public LogTriageStateResponse startProgress(@PathVariable Long logId,
                                                @RequestBody LogTriageActionRequest request) {
        return logTriageService.startProgress(logId, request.getComment(), request.getActionBy());
    }

    @PreAuthorize("hasAnyRole('ANALYST','MANAGER','ADMIN')")
    @PostMapping("/{logId}/resolve")
    public LogTriageStateResponse resolve(@PathVariable Long logId,
                                          @RequestBody LogTriageActionRequest request) {
        return logTriageService.resolve(logId, request.getComment(), request.getActionBy());
    }

    @PreAuthorize("hasAnyRole('ANALYST','MANAGER','ADMIN')")
    @PostMapping("/{logId}/ignore")
    public LogTriageStateResponse ignore(@PathVariable Long logId,
                                         @RequestBody LogTriageActionRequest request) {
        return logTriageService.ignore(logId, request.getComment(), request.getActionBy());
    }

    @PreAuthorize("hasAnyRole('ANALYST','MANAGER','ADMIN')")
    @PostMapping("/{logId}/reopen")
    public LogTriageStateResponse reopen(@PathVariable Long logId,
                                         @RequestBody LogTriageActionRequest request) {
        return logTriageService.reopen(logId, request.getComment(), request.getActionBy());
    }

    @PreAuthorize("hasAnyRole('ANALYST','MANAGER','ADMIN')")
    @PostMapping("/{logId}/mark-important")
    public LogTriageStateResponse markImportant(@PathVariable Long logId,
                                                @RequestBody LogTriageActionRequest request) {
        return logTriageService.markImportant(logId, request.getActionBy());
    }

    @PreAuthorize("hasAnyRole('ANALYST','MANAGER','ADMIN')")
    @PostMapping("/{logId}/unmark-important")
    public LogTriageStateResponse unmarkImportant(@PathVariable Long logId,
                                                  @RequestBody LogTriageActionRequest request) {
        return logTriageService.unmarkImportant(logId, request.getActionBy());
    }

    @PreAuthorize("hasAnyRole('ANALYST','MANAGER','ADMIN')")
    @PostMapping("/{logId}/assign")
    public LogTriageStateResponse assign(@PathVariable Long logId,
                                         @RequestBody LogTriageActionRequest request) {
        return logTriageService.assign(logId, request.getAssignedTo(), request.getComment(), request.getActionBy());
    }

    @PreAuthorize("hasAnyRole('ANALYST','MANAGER','ADMIN')")
    @PostMapping("/{logId}/unassign")
    public LogTriageStateResponse unassign(@PathVariable Long logId,
                                           @RequestBody LogTriageActionRequest request) {
        return logTriageService.unassign(logId, request.getComment(), request.getActionBy());
    }

    @PreAuthorize("hasAnyRole('ANALYST','MANAGER','ADMIN')")
    @PostMapping("/{logId}/comment")
    public LogTriageStateResponse comment(@PathVariable Long logId,
                                          @RequestBody LogTriageActionRequest request) {
        return logTriageService.addComment(logId, request.getComment(), request.getActionBy());
    }
}