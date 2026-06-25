package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

public class SessionDiagnosticResponseDto {

    private String groupBy;
    private String groupKey;
    private GroupAnalysisResponseDto groupAnalysis;
    private ImportAnalysisSummaryDto importQuality;
    private ImportExtractionAuditDto extractionAudit;
    private AiContextResponseDto aiContext;
    private List<IncidentCandidateDto> relatedIncidents = new ArrayList<>();
    private String employeeSummary;

    public String getGroupBy() { return groupBy; }
    public void setGroupBy(String groupBy) { this.groupBy = groupBy; }

    public String getGroupKey() { return groupKey; }
    public void setGroupKey(String groupKey) { this.groupKey = groupKey; }

    public GroupAnalysisResponseDto getGroupAnalysis() { return groupAnalysis; }
    public void setGroupAnalysis(GroupAnalysisResponseDto groupAnalysis) { this.groupAnalysis = groupAnalysis; }

    public ImportAnalysisSummaryDto getImportQuality() { return importQuality; }
    public void setImportQuality(ImportAnalysisSummaryDto importQuality) { this.importQuality = importQuality; }

    public ImportExtractionAuditDto getExtractionAudit() { return extractionAudit; }
    public void setExtractionAudit(ImportExtractionAuditDto extractionAudit) { this.extractionAudit = extractionAudit; }

    public AiContextResponseDto getAiContext() { return aiContext; }
    public void setAiContext(AiContextResponseDto aiContext) { this.aiContext = aiContext; }

    public List<IncidentCandidateDto> getRelatedIncidents() { return relatedIncidents; }
    public void setRelatedIncidents(List<IncidentCandidateDto> relatedIncidents) { this.relatedIncidents = relatedIncidents; }

    public String getEmployeeSummary() { return employeeSummary; }
    public void setEmployeeSummary(String employeeSummary) { this.employeeSummary = employeeSummary; }
}
