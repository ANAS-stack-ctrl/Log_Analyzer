package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.dto.LogEntryViewDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.service.LogQueryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/logs")
public class LogController {

    private final LogQueryService logQueryService;
    private final LogEntryRepository logEntryRepository;

    public LogController(LogQueryService logQueryService,
                         LogEntryRepository logEntryRepository) {
        this.logQueryService = logQueryService;
        this.logEntryRepository = logEntryRepository;
    }

    /**
     * Recherche flexible pour le frontend :
     * /logs?importId=1&error=true&eventType=RULE_NOT_FOUND&limit=100
     */
    @GetMapping
    public List<LogEntryViewDto> searchLogs(
            @RequestParam(required = false) Long importId,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) List<Long> importIds,
            @RequestParam(required = false) List<String> fileNames,
            @RequestParam(required = false) Boolean error,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String processName,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String uuid,
            @RequestParam(required = false, defaultValue = "100") Integer limit
    ) {
        List<Long> mergedImportIds = Optional.ofNullable(importIds).orElseGet(List::of);
        if (importId != null) {
            mergedImportIds = mergedImportIds.isEmpty() ? List.of(importId) : mergedImportIds;
        }

        List<String> mergedFileNames = Optional.ofNullable(fileNames).orElseGet(List::of);
        if (fileName != null && !fileName.isBlank()) {
            mergedFileNames = mergedFileNames.isEmpty() ? List.of(fileName) : mergedFileNames;
        }

        if (!mergedImportIds.isEmpty() || !mergedFileNames.isEmpty()) {
            return logQueryService.searchLogsMulti(
                    mergedImportIds,
                    mergedFileNames,
                    error,
                    eventType,
                    processName,
                    sessionId,
                    uuid,
                    limit
            );
        }

        return logQueryService.searchLogs(
                null,
                null,
                error,
                eventType,
                processName,
                sessionId,
                uuid,
                limit
        );
    }

    /**
     * Logs bruts d'un import
     */
    @GetMapping("/import/{importId}")
    public List<LogEntry> getLogsByImportId(@PathVariable Long importId) {
        return logEntryRepository.findByLogImportIdOrderByLogTimestampAscIdAsc(importId);
    }

    /**
     * Logs en erreur d'un import
     */
    @GetMapping("/import/{importId}/errors")
    public List<LogEntry> getErrorLogsByImportId(@PathVariable Long importId) {
        return logEntryRepository.findByLogImportIdAndIsErrorTrueOrderByLogTimestampAsc(importId);
    }

    /**
     * Logs d'une session
     */
    @GetMapping("/session/{sessionId}")
    public List<LogEntry> getLogsBySessionId(@PathVariable String sessionId) {
        return logEntryRepository.findBySessionIdOrderByLogTimestampAsc(sessionId);
    }

    /**
     * Logs d'un correlation id utilisateur
     */
    @GetMapping("/user/{userCorrelationId}")
    public List<LogEntry> getLogsByUserCorrelationId(@PathVariable String userCorrelationId) {
        return logEntryRepository.findByUserCorrelationIdOrderByLogTimestampAsc(userCorrelationId);
    }

    /**
     * Logs par niveau
     */
    @GetMapping("/level/{level}")
    public List<LogEntry> getLogsByLevel(@PathVariable String level) {
        return logEntryRepository.findByLevelIgnoreCaseOrderByLogTimestampAsc(level);
    }

    /**
     * Logs par event type
     */
    @GetMapping("/event/{eventType}")
    public List<LogEntry> getLogsByEventType(@PathVariable String eventType) {
        return logEntryRepository.findByEventTypeOrderByLogTimestampAsc(eventType);
    }
}