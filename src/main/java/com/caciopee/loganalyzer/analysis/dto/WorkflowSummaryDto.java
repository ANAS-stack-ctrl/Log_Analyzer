package com.caciopee.loganalyzer.analysis.dto;

import java.util.ArrayList;
import java.util.List;

public class WorkflowSummaryDto {

    private Long importId;
    private Integer totalWorkflows;
    private Integer totalErrors;
    private Integer totalWarnings;
    private Integer performanceIssues;
    private Integer noRuleIssues;
    private Integer zeroRowIssues;
    private Integer maxMemoryObserved;
    private Long maxDurationObserved;
    private List<String> topProblemWorkflowKeys = new ArrayList<>();

    public Long getImportId() {
        return importId;
    }

    public void setImportId(Long importId) {
        this.importId = importId;
    }

    public Integer getTotalWorkflows() {
        return totalWorkflows;
    }

    public void setTotalWorkflows(Integer totalWorkflows) {
        this.totalWorkflows = totalWorkflows;
    }

    public Integer getTotalErrors() {
        return totalErrors;
    }

    public void setTotalErrors(Integer totalErrors) {
        this.totalErrors = totalErrors;
    }

    public Integer getTotalWarnings() {
        return totalWarnings;
    }

    public void setTotalWarnings(Integer totalWarnings) {
        this.totalWarnings = totalWarnings;
    }

    public Integer getPerformanceIssues() {
        return performanceIssues;
    }

    public void setPerformanceIssues(Integer performanceIssues) {
        this.performanceIssues = performanceIssues;
    }

    public Integer getNoRuleIssues() {
        return noRuleIssues;
    }

    public void setNoRuleIssues(Integer noRuleIssues) {
        this.noRuleIssues = noRuleIssues;
    }

    public Integer getZeroRowIssues() {
        return zeroRowIssues;
    }

    public void setZeroRowIssues(Integer zeroRowIssues) {
        this.zeroRowIssues = zeroRowIssues;
    }

    public Integer getMaxMemoryObserved() {
        return maxMemoryObserved;
    }

    public void setMaxMemoryObserved(Integer maxMemoryObserved) {
        this.maxMemoryObserved = maxMemoryObserved;
    }

    public Long getMaxDurationObserved() {
        return maxDurationObserved;
    }

    public void setMaxDurationObserved(Long maxDurationObserved) {
        this.maxDurationObserved = maxDurationObserved;
    }

    public List<String> getTopProblemWorkflowKeys() {
        return topProblemWorkflowKeys;
    }

    public void setTopProblemWorkflowKeys(List<String> topProblemWorkflowKeys) {
        this.topProblemWorkflowKeys = topProblemWorkflowKeys;
    }
}