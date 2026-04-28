package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ExecutionStoryDto {

    private String storyType;
    private Long importId;
    private String sessionId;
    private String businessKey;
    private String correlationId;

    private LocalDateTime firstTimestamp;
    private LocalDateTime lastTimestamp;

    private long totalLogs;
    private long errorCount;
    private long warningCount;

    private String finalStatus;
    private String rootCauseCategory;
    private String rootCauseExplanation;
    private String confidence;

    private List<CountValueDto> topEventTypes = new ArrayList<>();
    private List<StoryStepDto> steps = new ArrayList<>();

    public String getStoryType() {
        return storyType;
    }

    public void setStoryType(String storyType) {
        this.storyType = storyType;
    }

    public Long getImportId() {
        return importId;
    }

    public void setImportId(Long importId) {
        this.importId = importId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public LocalDateTime getFirstTimestamp() {
        return firstTimestamp;
    }

    public void setFirstTimestamp(LocalDateTime firstTimestamp) {
        this.firstTimestamp = firstTimestamp;
    }

    public LocalDateTime getLastTimestamp() {
        return lastTimestamp;
    }

    public void setLastTimestamp(LocalDateTime lastTimestamp) {
        this.lastTimestamp = lastTimestamp;
    }

    public long getTotalLogs() {
        return totalLogs;
    }

    public void setTotalLogs(long totalLogs) {
        this.totalLogs = totalLogs;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(long errorCount) {
        this.errorCount = errorCount;
    }

    public long getWarningCount() {
        return warningCount;
    }

    public void setWarningCount(long warningCount) {
        this.warningCount = warningCount;
    }

    public String getFinalStatus() {
        return finalStatus;
    }

    public void setFinalStatus(String finalStatus) {
        this.finalStatus = finalStatus;
    }

    public String getRootCauseCategory() {
        return rootCauseCategory;
    }

    public void setRootCauseCategory(String rootCauseCategory) {
        this.rootCauseCategory = rootCauseCategory;
    }

    public String getRootCauseExplanation() {
        return rootCauseExplanation;
    }

    public void setRootCauseExplanation(String rootCauseExplanation) {
        this.rootCauseExplanation = rootCauseExplanation;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public List<CountValueDto> getTopEventTypes() {
        return topEventTypes;
    }

    public void setTopEventTypes(List<CountValueDto> topEventTypes) {
        this.topEventTypes = topEventTypes;
    }

    public List<StoryStepDto> getSteps() {
        return steps;
    }

    public void setSteps(List<StoryStepDto> steps) {
        this.steps = steps;
    }
}