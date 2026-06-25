package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.dto.CrossSearchRequestDto;
import com.caciopee.loganalyzer.dto.CrossSearchResponseDto;
import com.caciopee.loganalyzer.dto.SessionComparisonRequestDto;
import com.caciopee.loganalyzer.dto.SessionComparisonResponseDto;
import com.caciopee.loganalyzer.service.CrossSearchService;
import com.caciopee.loganalyzer.dto.ImportComparisonRequestDto;
import com.caciopee.loganalyzer.dto.ImportComparisonResponseDto;
import com.caciopee.loganalyzer.dto.ImportExtractionAuditDto;
import com.caciopee.loganalyzer.dto.LogAnalysisGuidanceRequestDto;
import com.caciopee.loganalyzer.dto.LogAnalysisGuidanceResponseDto;
import com.caciopee.loganalyzer.dto.WorksCatalogOverviewDto;
import com.caciopee.loganalyzer.analysis.catalog.WorksMessageCatalogService;
import com.caciopee.loganalyzer.service.ImportComparisonService;
import com.caciopee.loganalyzer.service.ImportExtractionAuditService;
import com.caciopee.loganalyzer.service.LogAnalysisGuidanceService;
import com.caciopee.loganalyzer.service.SessionComparisonService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/explorer")
public class LogExplorerController {

    private final CrossSearchService crossSearchService;
    private final SessionComparisonService sessionComparisonService;
    private final ImportComparisonService importComparisonService;
    private final ImportExtractionAuditService importExtractionAuditService;
    private final LogAnalysisGuidanceService logAnalysisGuidanceService;
    private final WorksMessageCatalogService worksMessageCatalogService;

    public LogExplorerController(CrossSearchService crossSearchService,
                                 SessionComparisonService sessionComparisonService,
                                 ImportComparisonService importComparisonService,
                                 ImportExtractionAuditService importExtractionAuditService,
                                 LogAnalysisGuidanceService logAnalysisGuidanceService,
                                 WorksMessageCatalogService worksMessageCatalogService) {
        this.crossSearchService = crossSearchService;
        this.sessionComparisonService = sessionComparisonService;
        this.importComparisonService = importComparisonService;
        this.importExtractionAuditService = importExtractionAuditService;
        this.logAnalysisGuidanceService = logAnalysisGuidanceService;
        this.worksMessageCatalogService = worksMessageCatalogService;
    }

    @PostMapping("/cross-search")
    public CrossSearchResponseDto crossSearch(@RequestBody CrossSearchRequestDto request) {
        return crossSearchService.search(request);
    }

    @PostMapping("/compare-sessions")
    public SessionComparisonResponseDto compareSessions(@RequestBody SessionComparisonRequestDto request) {
        return sessionComparisonService.compare(request);
    }

    @PostMapping("/compare-imports")
    public ImportComparisonResponseDto compareImports(@RequestBody ImportComparisonRequestDto request) {
        return importComparisonService.compare(request);
    }

    @GetMapping("/import-quality/{importId}")
    public ImportExtractionAuditDto importQuality(@PathVariable Long importId) {
        return importExtractionAuditService.audit(importId);
    }

    @GetMapping("/works-catalog")
    public WorksCatalogOverviewDto worksCatalog() {
        return worksMessageCatalogService.getCatalogOverview();
    }

    @PostMapping("/analysis-guidance")
    public LogAnalysisGuidanceResponseDto analysisGuidance(@RequestBody LogAnalysisGuidanceRequestDto request) {
        return logAnalysisGuidanceService.buildGuidance(
                request.getImportIds() != null ? request.getImportIds() : java.util.List.of()
        );
    }
}
