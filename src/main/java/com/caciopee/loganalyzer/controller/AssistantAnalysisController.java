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
import com.caciopee.loganalyzer.dto.SessionDiagnosticResponseDto;
import com.caciopee.loganalyzer.service.SessionDiagnosticService;
import com.caciopee.loganalyzer.dto.AssistantChatRequestDto;
import com.caciopee.loganalyzer.dto.AssistantChatResponseDto;
import com.caciopee.loganalyzer.dto.AssistantFullAnalysisResponseDto;
import com.caciopee.loganalyzer.dto.AssistantUserTopSessionsRequestDto;
import com.caciopee.loganalyzer.dto.AssistantUserTopSessionsResponseDto;
import com.caciopee.loganalyzer.service.AssistantChatService;
import com.caciopee.loganalyzer.service.AssistantFullAnalysisService;
import com.caciopee.loganalyzer.service.AssistantUserTopSessionsService;
import com.caciopee.loganalyzer.dto.GraphLogSearchRequestDto;
import com.caciopee.loganalyzer.dto.GraphLogSearchResponseDto;
import com.caciopee.loganalyzer.dto.LatencyOriginReportDto;
import com.caciopee.loganalyzer.dto.LatencyOriginRequestDto;
import com.caciopee.loganalyzer.service.GraphLogSearchService;
import com.caciopee.loganalyzer.service.ImportAccessService;
import com.caciopee.loganalyzer.service.LatencyInvestigationService;

@RestController
@RequestMapping("/assistant")
public class AssistantAnalysisController {

    private final LogGroupAnalysisService logGroupAnalysisService;
    private final LogRelatedLogsService logRelatedLogsService;
    private final AiContextBuilderService aiContextBuilderService;
    private final WorkflowGraphService workflowGraphService;
    private final SessionDiagnosticService sessionDiagnosticService;
    private final AssistantChatService assistantChatService;
    private final AssistantFullAnalysisService assistantFullAnalysisService;
    private final AssistantUserTopSessionsService assistantUserTopSessionsService;
    private final GraphLogSearchService graphLogSearchService;
    private final LatencyInvestigationService latencyInvestigationService;
    private final ImportAccessService importAccessService;

    public AssistantAnalysisController(LogGroupAnalysisService logGroupAnalysisService,
                                       LogRelatedLogsService logRelatedLogsService,
                                       AiContextBuilderService aiContextBuilderService,
                                       WorkflowGraphService workflowGraphService,
                                       SessionDiagnosticService sessionDiagnosticService,
                                       AssistantChatService assistantChatService,
                                       AssistantFullAnalysisService assistantFullAnalysisService,
                                       AssistantUserTopSessionsService assistantUserTopSessionsService,
                                       GraphLogSearchService graphLogSearchService,
                                       LatencyInvestigationService latencyInvestigationService,
                                       ImportAccessService importAccessService) {
        this.logGroupAnalysisService = logGroupAnalysisService;
        this.logRelatedLogsService = logRelatedLogsService;
        this.aiContextBuilderService = aiContextBuilderService;
        this.workflowGraphService = workflowGraphService;
        this.sessionDiagnosticService = sessionDiagnosticService;
        this.assistantChatService = assistantChatService;
        this.assistantFullAnalysisService = assistantFullAnalysisService;
        this.assistantUserTopSessionsService = assistantUserTopSessionsService;
        this.graphLogSearchService = graphLogSearchService;
        this.latencyInvestigationService = latencyInvestigationService;
        this.importAccessService = importAccessService;
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

    @PostMapping("/graph-log-search")
    public GraphLogSearchResponseDto graphLogSearch(@RequestBody GraphLogSearchRequestDto request) {
        return graphLogSearchService.search(request);
    }

    @PostMapping("/latency-origin")
    public LatencyOriginReportDto latencyOrigin(@RequestBody LatencyOriginRequestDto request) {
        if (request == null || request.getImportId() == null) {
            throw new IllegalArgumentException("importId requis.");
        }
        importAccessService.touch(request.getImportId());
        return latencyInvestigationService.investigate(
                request.getImportId(),
                request.getEvidenceLogId(),
                request.getSessionId(),
                request.getUuid(),
                request.getFilterCode(),
                request.getProcessName()
        );
    }

    @PostMapping("/session-diagnostic")
    public SessionDiagnosticResponseDto sessionDiagnostic(@RequestBody GroupAnalysisRequestDto request) {
        return sessionDiagnosticService.buildDiagnostic(request);
    }
    @PostMapping("/related-context")
    public RelatedLogsResponseDto relatedContext(@RequestBody RelatedContextRequestDto request) {
        return logRelatedLogsService.getContextAround(request);
    }

    @PostMapping("/chat")
    public AssistantChatResponseDto chat(@RequestBody AssistantChatRequestDto request) {
        return assistantChatService.chat(request);
    }

    @PostMapping("/full-analysis")
    public AssistantFullAnalysisResponseDto fullAnalysis(@RequestBody GroupAnalysisRequestDto request) {
        return assistantFullAnalysisService.fullAnalysis(request);
    }

    @PostMapping("/user-top-sessions")
    public AssistantUserTopSessionsResponseDto userTopSessions(@RequestBody AssistantUserTopSessionsRequestDto request) {
        return assistantUserTopSessionsService.findTopSessions(request);
    }
}