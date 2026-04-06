package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.dto.*;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.service.LogAnalysisService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/analysis")
public class LogAnalysisController {

    private final LogAnalysisService logAnalysisService;

    public LogAnalysisController(LogAnalysisService logAnalysisService) {
        this.logAnalysisService = logAnalysisService;
    }

    @GetMapping("/dashboard")
    public DashboardResponse getDashboard() {
        return logAnalysisService.buildDashboard();
    }

    @GetMapping("/summary")
    public AnalysisSummaryResponse getGlobalSummary() {
        return logAnalysisService.buildGlobalSummary();
    }

    @GetMapping("/import/{id}/summary")
    public ImportSummaryResponse getImportSummary(@PathVariable Long id) {
        return logAnalysisService.buildImportSummary(id);
    }

    @GetMapping("/import/{id}/diagnostic")
    public DiagnosticResult getImportDiagnostic(@PathVariable Long id) {
        return logAnalysisService.buildImportDiagnostic(id);
    }

    @GetMapping("/business-key/{businessKey}/diagnostic")
    public DiagnosticResult getBusinessKeyDiagnostic(@PathVariable String businessKey) {
        return logAnalysisService.buildBusinessKeyDiagnostic(businessKey);
    }

    @GetMapping("/import/{id}/business-key/{businessKey}/diagnostic")
    public DiagnosticResult getBusinessKeyDiagnosticForImport(
            @PathVariable Long id,
            @PathVariable String businessKey
    ) {
        return logAnalysisService.buildBusinessKeyDiagnosticForImport(id, businessKey);
    }

    @GetMapping("/import/{id}/errors")
    public List<LogEntry> getImportErrors(@PathVariable Long id) {
        return logAnalysisService.getImportErrors(id);
    }

    @GetMapping("/import/{id}/business-keys")
    public List<String> getImportBusinessKeys(@PathVariable Long id) {
        return logAnalysisService.getImportBusinessKeys(id);
    }

    @GetMapping("/import/{id}/business-key/{businessKey}")
    public BusinessKeyAnalysisResponse analyzeBusinessKeyForImport(
            @PathVariable Long id,
            @PathVariable String businessKey
    ) {
        return logAnalysisService.analyzeBusinessKeyForImport(id, businessKey);
    }

    @GetMapping("/import/{id}/story")
    public WorkflowStoryResponse getImportStory(@PathVariable Long id) {
        return logAnalysisService.buildImportStory(id);
    }

    @GetMapping("/business-key/{businessKey}")
    public BusinessKeyAnalysisResponse analyzeBusinessKey(@PathVariable String businessKey) {
        return logAnalysisService.analyzeBusinessKey(businessKey);
    }

    @GetMapping("/business-key/{businessKey}/story")
    public WorkflowStoryResponse getBusinessKeyStory(@PathVariable String businessKey) {
        return logAnalysisService.buildBusinessKeyStory(businessKey);
    }

    @GetMapping("/import/{id}/business-key/{businessKey}/story")
    public WorkflowStoryResponse getBusinessKeyStoryForImport(
            @PathVariable Long id,
            @PathVariable String businessKey
    ) {
        return logAnalysisService.buildBusinessKeyStoryForImport(id, businessKey);
    }

    @GetMapping("/log/{id}/explain")
    public LogExplanationResponse explainLog(@PathVariable Long id) {
        return logAnalysisService.explainLog(id);
    }

    @GetMapping("/errors/explain")
    public List<LogExplanationResponse> explainTopErrors(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return logAnalysisService.explainTopErrors(limit);
    }
}