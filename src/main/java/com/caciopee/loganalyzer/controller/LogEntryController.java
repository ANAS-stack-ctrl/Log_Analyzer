package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.dto.ErrorClusterResponse;
import com.caciopee.loganalyzer.dto.LogListItemResponse;
import com.caciopee.loganalyzer.dto.PagedResponse;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.service.LogEntryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/logs")
public class LogEntryController {

    private final LogEntryService logEntryService;

    public LogEntryController(LogEntryService logEntryService) {
        this.logEntryService = logEntryService;
    }

    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    @PostMapping
    public LogEntry addLog(@RequestBody LogEntry logEntry) {
        return logEntryService.saveLog(logEntry);
    }

    @PreAuthorize("hasAnyRole('VIEWER','ANALYST','MANAGER','ADMIN')")
    @GetMapping
    public PagedResponse<LogListItemResponse> getLogs(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String businessKey,
            @RequestParam(required = false) String fieldName,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String executionId,
            @RequestParam(required = false) Boolean error,
            @RequestParam(required = false) Long importId,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String triageStatus,
            @RequestParam(required = false) Boolean important,
            @RequestParam(required = false) Boolean onlyActive,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return logEntryService.getLogs(
                level,
                eventType,
                businessKey,
                fieldName,
                correlationId,
                executionId,
                error,
                importId,
                fileName,
                triageStatus,
                important,
                onlyActive,
                assignedTo,
                page,
                size
        );
    }

    @PreAuthorize("hasAnyRole('VIEWER','ANALYST','MANAGER','ADMIN')")
    @GetMapping("/clusters/errors")
    public List<ErrorClusterResponse> getErrorClusters(
            @RequestParam(defaultValue = "true") Boolean onlyActive,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return logEntryService.getErrorClusters(onlyActive, limit);
    }

    @PreAuthorize("hasAnyRole('VIEWER','ANALYST','MANAGER','ADMIN')")
    @GetMapping("/count")
    public Map<String, Long> countAllLogs() {
        return Map.of("total", logEntryService.countLogs());
    }

    @PreAuthorize("hasAnyRole('VIEWER','ANALYST','MANAGER','ADMIN')")
    @GetMapping("/count-by-level")
    public Map<String, Object> countLogsByLevel(@RequestParam String level) {
        Map<String, Object> response = new HashMap<>();
        response.put("level", level);
        response.put("count", logEntryService.countByLevel(level));
        return response;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping
    public String deleteAllLogs() {
        logEntryService.deleteAllLogs();
        return "All logs deleted successfully.";
    }
}