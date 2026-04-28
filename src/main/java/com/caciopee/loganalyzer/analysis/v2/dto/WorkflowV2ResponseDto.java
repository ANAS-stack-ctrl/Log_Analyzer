package com.caciopee.loganalyzer.analysis.v2.dto;

import java.util.ArrayList;
import java.util.List;

public class WorkflowV2ResponseDto {

    private Long importId;
    private Integer totalWorkflows;
    private Integer workflowsWithRuleProblems;
    private Integer workflowsWithZeroResults;
    private Integer workflowsWithPerformanceProblems;
    private List<WorkflowV2SummaryDto> workflows = new ArrayList<>();

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

    public Integer getWorkflowsWithRuleProblems() {
        return workflowsWithRuleProblems;
    }

    public void setWorkflowsWithRuleProblems(Integer workflowsWithRuleProblems) {
        this.workflowsWithRuleProblems = workflowsWithRuleProblems;
    }

    public Integer getWorkflowsWithZeroResults() {
        return workflowsWithZeroResults;
    }

    public void setWorkflowsWithZeroResults(Integer workflowsWithZeroResults) {
        this.workflowsWithZeroResults = workflowsWithZeroResults;
    }

    public Integer getWorkflowsWithPerformanceProblems() {
        return workflowsWithPerformanceProblems;
    }

    public void setWorkflowsWithPerformanceProblems(Integer workflowsWithPerformanceProblems) {
        this.workflowsWithPerformanceProblems = workflowsWithPerformanceProblems;
    }

    public List<WorkflowV2SummaryDto> getWorkflows() {
        return workflows;
    }

    public void setWorkflows(List<WorkflowV2SummaryDto> workflows) {
        this.workflows = workflows;
    }
}