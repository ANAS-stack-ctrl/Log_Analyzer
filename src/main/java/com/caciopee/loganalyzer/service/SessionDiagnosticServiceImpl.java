package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SessionDiagnosticServiceImpl implements SessionDiagnosticService {

    private final LogGroupAnalysisService logGroupAnalysisService;
    private final LogAnalysisService logAnalysisService;
    private final AiContextBuilderService aiContextBuilderService;
    private final ImportExtractionAuditService importExtractionAuditService;

    public SessionDiagnosticServiceImpl(LogGroupAnalysisService logGroupAnalysisService,
                                        LogAnalysisService logAnalysisService,
                                        AiContextBuilderService aiContextBuilderService,
                                        ImportExtractionAuditService importExtractionAuditService) {
        this.logGroupAnalysisService = logGroupAnalysisService;
        this.logAnalysisService = logAnalysisService;
        this.aiContextBuilderService = aiContextBuilderService;
        this.importExtractionAuditService = importExtractionAuditService;
    }

    @Override
    public SessionDiagnosticResponseDto buildDiagnostic(GroupAnalysisRequestDto request) {
        if (request == null || request.getImportIds() == null || request.getImportIds().isEmpty()) {
            throw new IllegalArgumentException("Au moins un importId est requis.");
        }
        if (request.getGroupKey() == null || request.getGroupKey().isBlank()) {
            throw new IllegalArgumentException("groupKey requis (ex. session technique).");
        }

        Long primaryImportId = request.getImportIds().get(0);

        GroupAnalysisResponseDto analysis = logGroupAnalysisService.analyzeGroup(request);

        AiContextRequestDto ctxReq = new AiContextRequestDto();
        ctxReq.setImportIds(request.getImportIds());
        ctxReq.setGroupBy(request.getGroupBy());
        ctxReq.setGroupKey(request.getGroupKey());
        ctxReq.setDateFrom(request.getDateFrom());
        ctxReq.setDateTo(request.getDateTo());
        ctxReq.setMaxEvidenceLogs(25);

        AiContextResponseDto aiContext = aiContextBuilderService.buildContext(ctxReq);

        List<IncidentCandidateDto> incidents = logAnalysisService.detectImportIncidents(primaryImportId).stream()
                .filter(i -> matchesGroup(request.getGroupBy(), request.getGroupKey(), i))
                .limit(10)
                .toList();

        SessionDiagnosticResponseDto response = new SessionDiagnosticResponseDto();
        response.setGroupBy(request.getGroupBy());
        response.setGroupKey(request.getGroupKey());
        response.setGroupAnalysis(analysis);
        response.setImportQuality(logAnalysisService.getImportSummary(primaryImportId));
        response.setExtractionAudit(importExtractionAuditService.audit(primaryImportId));
        response.setAiContext(aiContext);
        response.setRelatedIncidents(incidents);
        response.setEmployeeSummary(buildEmployeeSummary(analysis, incidents, response.getImportQuality()));
        return response;
    }

    private boolean matchesGroup(String groupBy, String groupKey, IncidentCandidateDto incident) {
        if (groupBy == null || groupKey == null) return true;
        return switch (groupBy) {
            case "userName" -> groupKey.equalsIgnoreCase(incident.getUserName());
            case "sessionId" -> groupKey.equals(incident.getSessionId());
            case "businessKey" -> groupKey.equals(incident.getBusinessKey());
            case "correlationId" -> groupKey.equals(incident.getCorrelationId());
            case "uuid" -> groupKey.equals(incident.getBusinessKey()) || groupKey.equals(incident.getCorrelationId());
            case "processName" -> groupKey.equalsIgnoreCase(incident.getProcessName());
            default -> true;
        };
    }

    private String buildEmployeeSummary(GroupAnalysisResponseDto analysis,
                                        List<IncidentCandidateDto> incidents,
                                        ImportAnalysisSummaryDto importQuality) {
        return EmployeeDiagnosticReportBuilder.buildSummary(analysis, incidents, importQuality);
    }
}
