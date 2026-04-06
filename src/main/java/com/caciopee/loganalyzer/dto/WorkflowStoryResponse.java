package com.caciopee.loganalyzer.dto;

import java.util.List;

public class WorkflowStoryResponse {

    private String scope;
    private String reference;
    private long totalLogs;
    private long totalImportantSteps;
    private long totalErrors;
    private List<String> detectedAnomalies;
    private String summary;
    private String conclusion;
    private List<StoryStepResponse> steps;

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public long getTotalLogs() {
        return totalLogs;
    }

    public void setTotalLogs(long totalLogs) {
        this.totalLogs = totalLogs;
    }

    public long getTotalImportantSteps() {
        return totalImportantSteps;
    }

    public void setTotalImportantSteps(long totalImportantSteps) {
        this.totalImportantSteps = totalImportantSteps;
    }

    public long getTotalErrors() {
        return totalErrors;
    }

    public void setTotalErrors(long totalErrors) {
        this.totalErrors = totalErrors;
    }

    public List<String> getDetectedAnomalies() {
        return detectedAnomalies;
    }

    public void setDetectedAnomalies(List<String> detectedAnomalies) {
        this.detectedAnomalies = detectedAnomalies;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public List<StoryStepResponse> getSteps() {
        return steps;
    }

    public void setSteps(List<StoryStepResponse> steps) {
        this.steps = steps;
    }
}