package com.caciopee.loganalyzer.analysis.dto;

import com.caciopee.loganalyzer.analysis.WorkflowNature;
import com.caciopee.loganalyzer.analysis.WorkflowStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WorkflowAnalysisDto {

    private String workflowKey;

    private String correlationId;
    private String sessionId;
    private String extractedUuid;
    private String dominantTransactionId;
    private String dominantFilterCode;
    private String dominantThreadName;

    private String processName;
    private String dominantSourceClass;

    private WorkflowNature nature;
    private WorkflowStatus status;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long durationMs;

    private Integer totalLogs;
    private Integer errorCount;
    private Integer warningCount;
    private Integer queryCount;
    private Integer saveCount;
    private Integer maxMemoryMo;
    private Integer maxRowCount;
    private Integer severityScore;

    private boolean hasErrors;
    private boolean hasWarnings;
    private boolean hasPerformanceIssue;
    private boolean hasNoRuleIssue;
    private boolean hasZeroRowIssue;

    private String summary;

    private List<WorkflowStepDto> steps = new ArrayList<>();
    private List<WorkflowDiagnosticDto> diagnostics = new ArrayList<>();

    public String getWorkflowKey() {
        return workflowKey;
    }

    public void setWorkflowKey(String workflowKey) {
        this.workflowKey = workflowKey;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getExtractedUuid() {
        return extractedUuid;
    }

    public void setExtractedUuid(String extractedUuid) {
        this.extractedUuid = extractedUuid;
    }

    public String getDominantTransactionId() {
        return dominantTransactionId;
    }

    public void setDominantTransactionId(String dominantTransactionId) {
        this.dominantTransactionId = dominantTransactionId;
    }

    public String getDominantFilterCode() {
        return dominantFilterCode;
    }

    public void setDominantFilterCode(String dominantFilterCode) {
        this.dominantFilterCode = dominantFilterCode;
    }

    public String getDominantThreadName() {
        return dominantThreadName;
    }

    public void setDominantThreadName(String dominantThreadName) {
        this.dominantThreadName = dominantThreadName;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getDominantSourceClass() {
        return dominantSourceClass;
    }

    public void setDominantSourceClass(String dominantSourceClass) {
        this.dominantSourceClass = dominantSourceClass;
    }

    public WorkflowNature getNature() {
        return nature;
    }

    public void setNature(WorkflowNature nature) {
        this.nature = nature;
    }

    public WorkflowStatus getStatus() {
        return status;
    }

    public void setStatus(WorkflowStatus status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getTotalLogs() {
        return totalLogs;
    }

    public void setTotalLogs(Integer totalLogs) {
        this.totalLogs = totalLogs;
    }

    public Integer getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(Integer errorCount) {
        this.errorCount = errorCount;
    }

    public Integer getWarningCount() {
        return warningCount;
    }

    public void setWarningCount(Integer warningCount) {
        this.warningCount = warningCount;
    }

    public Integer getQueryCount() {
        return queryCount;
    }

    public void setQueryCount(Integer queryCount) {
        this.queryCount = queryCount;
    }

    public Integer getSaveCount() {
        return saveCount;
    }

    public void setSaveCount(Integer saveCount) {
        this.saveCount = saveCount;
    }

    public Integer getMaxMemoryMo() {
        return maxMemoryMo;
    }

    public void setMaxMemoryMo(Integer maxMemoryMo) {
        this.maxMemoryMo = maxMemoryMo;
    }

    public Integer getMaxRowCount() {
        return maxRowCount;
    }

    public void setMaxRowCount(Integer maxRowCount) {
        this.maxRowCount = maxRowCount;
    }

    public Integer getSeverityScore() {
        return severityScore;
    }

    public void setSeverityScore(Integer severityScore) {
        this.severityScore = severityScore;
    }

    public boolean isHasErrors() {
        return hasErrors;
    }

    public void setHasErrors(boolean hasErrors) {
        this.hasErrors = hasErrors;
    }

    public boolean isHasWarnings() {
        return hasWarnings;
    }

    public void setHasWarnings(boolean hasWarnings) {
        this.hasWarnings = hasWarnings;
    }

    public boolean isHasPerformanceIssue() {
        return hasPerformanceIssue;
    }

    public void setHasPerformanceIssue(boolean hasPerformanceIssue) {
        this.hasPerformanceIssue = hasPerformanceIssue;
    }

    public boolean isHasNoRuleIssue() {
        return hasNoRuleIssue;
    }

    public void setHasNoRuleIssue(boolean hasNoRuleIssue) {
        this.hasNoRuleIssue = hasNoRuleIssue;
    }

    public boolean isHasZeroRowIssue() {
        return hasZeroRowIssue;
    }

    public void setHasZeroRowIssue(boolean hasZeroRowIssue) {
        this.hasZeroRowIssue = hasZeroRowIssue;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<WorkflowStepDto> getSteps() {
        return steps;
    }

    public void setSteps(List<WorkflowStepDto> steps) {
        this.steps = steps;
    }

    public List<WorkflowDiagnosticDto> getDiagnostics() {
        return diagnostics;
    }

    public void setDiagnostics(List<WorkflowDiagnosticDto> diagnostics) {
        this.diagnostics = diagnostics;
    }
}