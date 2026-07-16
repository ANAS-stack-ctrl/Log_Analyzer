package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.dto.LogEntryViewDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.service.LogQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import com.caciopee.loganalyzer.dto.LogGroupDto;
import com.caciopee.loganalyzer.service.LogGroupingService;

import java.time.LocalDateTime;


import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/logs")
public class LogController {

    private final LogQueryService logQueryService;
    private final LogEntryRepository logEntryRepository;
    private final LogGroupingService logGroupingService;


    public LogController(LogQueryService logQueryService,
                         LogGroupingService logGroupingService,
                         LogEntryRepository logEntryRepository) {
        this.logQueryService = logQueryService;
        this.logEntryRepository = logEntryRepository;
        this.logGroupingService = logGroupingService;
    }

    @GetMapping
    public List<LogEntryViewDto> searchLogs(
            @RequestParam(required = false) Long importId,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) List<Long> importIds,
            @RequestParam(required = false) List<String> fileNames,
            @RequestParam(required = false) Boolean error,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String processName,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String uuid,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime dateTo,
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
                    userName,
                    sessionId,
                    uuid,
                    dateFrom,
                    dateTo,
                    limit
            );
        }

        return logQueryService.searchLogs(
                null,
                null,
                error,
                eventType,
                processName,
                userName,
                sessionId,
                uuid,
                dateFrom,
                dateTo,
                limit
        );
    }

    @GetMapping("/import/{importId}")
    public List<LogEntry> getLogsByImportId(@PathVariable Long importId,
                                            @RequestParam(defaultValue = "500") int limit) {
        int safe = Math.max(1, Math.min(limit, 2000));
        return logEntryRepository.findByLogImportIdOrderByLogTimestampAscIdAsc(
                importId, org.springframework.data.domain.PageRequest.of(0, safe));
    }

    @GetMapping("/import/{importId}/errors")
    public List<LogEntry> getErrorLogsByImportId(@PathVariable Long importId) {
        return logEntryRepository.findByLogImportIdAndIsErrorTrueOrderByLogTimestampAsc(importId);
    }

    @GetMapping("/session/{sessionId}")
    public List<LogEntry> getLogsBySessionId(@PathVariable String sessionId) {
        return logEntryRepository.findBySessionIdOrderByLogTimestampAsc(sessionId);
    }

    @GetMapping("/user/{userCorrelationId}")
    public List<LogEntry> getLogsByUserCorrelationId(@PathVariable String userCorrelationId) {
        return logEntryRepository.findByUserCorrelationIdOrderByLogTimestampAsc(userCorrelationId);
    }

    @GetMapping("/level/{level}")
    public List<LogEntry> getLogsByLevel(@PathVariable String level) {
        return logEntryRepository.findByLevelIgnoreCaseOrderByLogTimestampAsc(level);
    }

    @GetMapping("/event/{eventType}")
    public List<LogEntry> getLogsByEventType(@PathVariable String eventType) {
        return logEntryRepository.findByEventTypeOrderByLogTimestampAsc(eventType);
    }
    @GetMapping("/groups")
    public List<LogGroupDto> groupLogs(
            @RequestParam(required = false) Long importId,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) List<Long> importIds,
            @RequestParam(required = false) List<String> fileNames,
            @RequestParam(required = false) Boolean error,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String processName,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String uuid,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime dateTo,
            @RequestParam(required = false, defaultValue = "sessionId") String groupBy,
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

        return logGroupingService.groupLogs(
                mergedImportIds,
                mergedFileNames,
                error,
                eventType,
                processName,
                userName,
                sessionId,
                uuid,
                dateFrom,
                dateTo,
                groupBy,
                limit
        );
    }
}