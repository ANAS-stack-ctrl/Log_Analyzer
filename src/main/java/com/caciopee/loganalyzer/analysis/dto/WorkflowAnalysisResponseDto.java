package com.caciopee.loganalyzer.analysis.dto;

import java.util.ArrayList;
import java.util.List;

public class WorkflowAnalysisResponseDto {

    private Long importId;
    private Integer totalWorkflows;
    private Integer workflowsWithErrors;
    private Integer workflowsWithWarnings;
    private Integer workflowsWithPerformanceIssues;
    private List<WorkflowAnalysisDto> workflows = new ArrayList<>();

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

    public Integer getWorkflowsWithErrors() {
        return workflowsWithErrors;
    }

    public void setWorkflowsWithErrors(Integer workflowsWithErrors) {
        this.workflowsWithErrors = workflowsWithErrors;
    }

    public Integer getWorkflowsWithWarnings() {
        return workflowsWithWarnings;
    }

    public void setWorkflowsWithWarnings(Integer workflowsWithWarnings) {
        this.workflowsWithWarnings = workflowsWithWarnings;
    }

    public Integer getWorkflowsWithPerformanceIssues() {
        return workflowsWithPerformanceIssues;
    }

    public void setWorkflowsWithPerformanceIssues(Integer workflowsWithPerformanceIssues) {
        this.workflowsWithPerformanceIssues = workflowsWithPerformanceIssues;
    }

    public List<WorkflowAnalysisDto> getWorkflows() {
        return workflows;
    }

    public void setWorkflows(List<WorkflowAnalysisDto> workflows) {
        this.workflows = workflows;
    }
}