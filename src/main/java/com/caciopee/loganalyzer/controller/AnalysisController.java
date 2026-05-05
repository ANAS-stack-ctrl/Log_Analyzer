package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.dto.DashboardSummaryDto;
import com.caciopee.loganalyzer.dto.ExecutionStoryDto;
import com.caciopee.loganalyzer.dto.ImportAnalysisSummaryDto;
import com.caciopee.loganalyzer.dto.IncidentCandidateDto;
import com.caciopee.loganalyzer.dto.LogEntryViewDto;
import com.caciopee.loganalyzer.dto.GenericPatternDto;
import com.caciopee.loganalyzer.service.LogAnalysisService;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequestMapping("/analysis")
public class AnalysisController {

    private final LogAnalysisService logAnalysisService;

    public AnalysisController(LogAnalysisService logAnalysisService) {
        this.logAnalysisService = logAnalysisService;
    }

    @GetMapping("/dashboard")
    public DashboardSummaryDto getDashboard() {
        return logAnalysisService.getDashboard();
    }

    @GetMapping("/import/{importId}/summary")
    public ImportAnalysisSummaryDto getImportSummary(@PathVariable Long importId) {
        return logAnalysisService.getImportSummary(importId);
    }

    @GetMapping("/import/{importId}/story")
    public ExecutionStoryDto getImportStory(@PathVariable Long importId) {
        return logAnalysisService.getImportStory(importId);
    }

    @GetMapping("/import/{importId}/incidents")
    public List<IncidentCandidateDto> getImportIncidents(@PathVariable Long importId) {
        return logAnalysisService.detectImportIncidents(importId);
    }

    @GetMapping("/execution/{sessionId}/story")
    public ExecutionStoryDto getExecutionStory(@PathVariable String sessionId) {
        return logAnalysisService.getExecutionStory(sessionId);
    }

    @GetMapping("/business-key/{businessKey}/story")
    public ExecutionStoryDto getBusinessKeyStory(@PathVariable String businessKey) {
        return logAnalysisService.getBusinessKeyStory(businessKey);
    }
    @GetMapping("/import/{importId}/generic-explanations")
    public List<LogEntryViewDto> getGenericExplanations(@PathVariable Long importId) {
        return logAnalysisService.getGenericExplanations(importId);
    }
    @GetMapping("/generic-explanations/all")
    public List<LogEntryViewDto> getAllGenericExplanations(
            @RequestParam(defaultValue = "1") Long startId,
            @RequestParam(defaultValue = "999999999") Long endId
    ) {
        return logAnalysisService.getAllGenericExplanations(startId, endId);
    }
    @GetMapping("/generic-explanations/grouped")
    public List<GenericPatternDto> getGroupedGenericExplanations(
            @RequestParam(defaultValue = "2000") int pageSize,
            @RequestParam(defaultValue = "300") int maxPatterns
    ) {
        return logAnalysisService.getGroupedGenericExplanations(pageSize, maxPatterns);
    }

}