package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.analysis.UserFriendlyAnalysisService;
import com.caciopee.loganalyzer.analysis.WorkflowAnalyzerService;
import com.caciopee.loganalyzer.analysis.WorkflowStoryService;
import com.caciopee.loganalyzer.analysis.dto.UserFriendlyAnalysisDto;
import com.caciopee.loganalyzer.analysis.dto.WorkflowAnalysisResponseDto;
import com.caciopee.loganalyzer.analysis.dto.WorkflowStoryResponseDto;
import com.caciopee.loganalyzer.analysis.dto.WorkflowSummaryDto;
import org.springframework.web.bind.annotation.*;
import com.caciopee.loganalyzer.analysis.v2.WorkflowAnalyzerV2Service;
import com.caciopee.loganalyzer.analysis.v2.dto.WorkflowV2ResponseDto;

@RestController
@RequestMapping("/workflow-analysis")
public class WorkflowAnalysisController {

    private final WorkflowAnalyzerService workflowAnalyzerService;
    private final WorkflowStoryService workflowStoryService;
    private final UserFriendlyAnalysisService userFriendlyAnalysisService;
    private final WorkflowAnalyzerV2Service workflowAnalyzerV2Service;

    public WorkflowAnalysisController(WorkflowAnalyzerService workflowAnalyzerService,
                                      WorkflowStoryService workflowStoryService,
                                      UserFriendlyAnalysisService userFriendlyAnalysisService,
                                      WorkflowAnalyzerV2Service workflowAnalyzerV2Service) {
        this.workflowAnalyzerService = workflowAnalyzerService;
        this.workflowStoryService = workflowStoryService;
        this.userFriendlyAnalysisService = userFriendlyAnalysisService;
        this.workflowAnalyzerV2Service = workflowAnalyzerV2Service;
    }

    @GetMapping("/import/{importId}")
    public WorkflowAnalysisResponseDto analyzeImport(@PathVariable Long importId) {
        return workflowAnalyzerService.analyzeImport(importId);
    }

    @GetMapping("/import/{importId}/summary")
    public WorkflowSummaryDto summarizeImport(@PathVariable Long importId) {
        return workflowAnalyzerService.summarizeImport(importId);
    }

    @GetMapping("/import/{importId}/story")
    public WorkflowStoryResponseDto buildStory(@PathVariable Long importId) {
        return workflowStoryService.buildImportStory(importId);
    }

    @GetMapping("/import/{importId}/human")
    public UserFriendlyAnalysisDto explainForHuman(@PathVariable Long importId) {
        return userFriendlyAnalysisService.explainImportForHuman(importId);
    }
    @GetMapping("/import/{importId}/v2")
    public WorkflowV2ResponseDto analyzeImportV2(
            @PathVariable Long importId,
            @RequestParam(required = false) String groupBy
    ) {
        return workflowAnalyzerV2Service.analyzeImportV2(importId, groupBy);
    }
    }
