package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.dto.GroupAnalysisRequestDto;
import com.caciopee.loganalyzer.dto.GroupAnalysisResponseDto;
import com.caciopee.loganalyzer.dto.RelatedLogsRequestDto;
import com.caciopee.loganalyzer.dto.RelatedLogsResponseDto;
import com.caciopee.loganalyzer.service.LogGroupAnalysisService;
import com.caciopee.loganalyzer.service.LogRelatedLogsService;
import org.springframework.web.bind.annotation.*;
import com.caciopee.loganalyzer.dto.AiContextRequestDto;
import com.caciopee.loganalyzer.dto.AiContextResponseDto;
import com.caciopee.loganalyzer.service.AiContextBuilderService;
import com.caciopee.loganalyzer.dto.WorkflowGraphResponseDto;
import com.caciopee.loganalyzer.service.WorkflowGraphService;
import com.caciopee.loganalyzer.dto.RelatedContextRequestDto;

@RestController
@RequestMapping("/assistant")
public class AssistantAnalysisController {

    private final LogGroupAnalysisService logGroupAnalysisService;
    private final LogRelatedLogsService logRelatedLogsService;
    private final AiContextBuilderService aiContextBuilderService;
    private final WorkflowGraphService workflowGraphService;

    public AssistantAnalysisController(LogGroupAnalysisService logGroupAnalysisService,
                                       LogRelatedLogsService logRelatedLogsService,
                                       AiContextBuilderService aiContextBuilderService,
                                       WorkflowGraphService workflowGraphService) {
        this.logGroupAnalysisService = logGroupAnalysisService;
        this.logRelatedLogsService = logRelatedLogsService;
        this.aiContextBuilderService = aiContextBuilderService;
        this.workflowGraphService = workflowGraphService;
    }

    @PostMapping("/analyze-group")
    public GroupAnalysisResponseDto analyzeGroup(@RequestBody GroupAnalysisRequestDto request) {
        return logGroupAnalysisService.analyzeGroup(request);
    }
    @PostMapping("/related-logs")
    public RelatedLogsResponseDto relatedLogs(@RequestBody RelatedLogsRequestDto request) {
        return logRelatedLogsService.getRelatedLogs(request);
    }
    @PostMapping("/build-context")
    public AiContextResponseDto buildContext(@RequestBody AiContextRequestDto request) {
        return aiContextBuilderService.buildContext(request);
    }
    @PostMapping("/workflow-graph")
    public WorkflowGraphResponseDto workflowGraph(@RequestBody GroupAnalysisRequestDto request) {
        return workflowGraphService.buildGraph(request);
    }
    @PostMapping("/related-context")
    public RelatedLogsResponseDto relatedContext(@RequestBody RelatedContextRequestDto request) {
        return logRelatedLogsService.getContextAround(request);
    }
}