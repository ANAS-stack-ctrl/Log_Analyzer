package com.caciopee.loganalyzer.analysis.v2.dto;

import java.util.ArrayList;
import java.util.List;

public class WorkflowV2SummaryDto {

    private String workflowKey;
    private String groupingStrategy;
    private String uuid;
    private String transactionId;
    private String filterCode;
    private String processName;
    private String className;

    private String title;
    private String userIntentSummary;
    private String whatHappenedSummary;
    private String finalOutcome;
    private String probableCause;
    private String recommendation;

    private Integer totalEvents;
    private Integer sqlCount;
    private Integer warningCount;
    private Integer errorCount;
    private Integer ruleWarningCount;
    private Integer resultRowCount;
    private Long totalDurationMs;
    private Integer maxMemoryMo;

    private Boolean hasZeroResult;
    private Boolean hasRuleProblem;
    private Boolean hasPerformanceProblem;

    private List<String> detectedInputs = new ArrayList<>();
    private List<String> timeline = new ArrayList<>();
    private List<WorkflowV2LineDto> lines = new ArrayList<>();

    public String getWorkflowKey() {
        return workflowKey;
    }

    public void setWorkflowKey(String workflowKey) {
        this.workflowKey = workflowKey;
    }

    public String getGroupingStrategy() {
        return groupingStrategy;
    }

    public void setGroupingStrategy(String groupingStrategy) {
        this.groupingStrategy = groupingStrategy;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getFilterCode() {
        return filterCode;
    }

    public void setFilterCode(String filterCode) {
        this.filterCode = filterCode;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUserIntentSummary() {
        return userIntentSummary;
    }

    public void setUserIntentSummary(String userIntentSummary) {
        this.userIntentSummary = userIntentSummary;
    }

    public String getWhatHappenedSummary() {
        return whatHappenedSummary;
    }

    public void setWhatHappenedSummary(String whatHappenedSummary) {
        this.whatHappenedSummary = whatHappenedSummary;
    }

    public String getFinalOutcome() {
        return finalOutcome;
    }

    public void setFinalOutcome(String finalOutcome) {
        this.finalOutcome = finalOutcome;
    }

    public String getProbableCause() {
        return probableCause;
    }

    public void setProbableCause(String probableCause) {
        this.probableCause = probableCause;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public Integer getTotalEvents() {
        return totalEvents;
    }

    public void setTotalEvents(Integer totalEvents) {
        this.totalEvents = totalEvents;
    }

    public Integer getSqlCount() {
        return sqlCount;
    }

    public void setSqlCount(Integer sqlCount) {
        this.sqlCount = sqlCount;
    }

    public Integer getWarningCount() {
        return warningCount;
    }

    public void setWarningCount(Integer warningCount) {
        this.warningCount = warningCount;
    }

    public Integer getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(Integer errorCount) {
        this.errorCount = errorCount;
    }

    public Integer getRuleWarningCount() {
        return ruleWarningCount;
    }

    public void setRuleWarningCount(Integer ruleWarningCount) {
        this.ruleWarningCount = ruleWarningCount;
    }

    public Integer getResultRowCount() {
        return resultRowCount;
    }

    public void setResultRowCount(Integer resultRowCount) {
        this.resultRowCount = resultRowCount;
    }

    public Long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(Long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    public Integer getMaxMemoryMo() {
        return maxMemoryMo;
    }

    public void setMaxMemoryMo(Integer maxMemoryMo) {
        this.maxMemoryMo = maxMemoryMo;
    }

    public Boolean getHasZeroResult() {
        return hasZeroResult;
    }

    public void setHasZeroResult(Boolean hasZeroResult) {
        this.hasZeroResult = hasZeroResult;
    }

    public Boolean getHasRuleProblem() {
        return hasRuleProblem;
    }

    public void setHasRuleProblem(Boolean hasRuleProblem) {
        this.hasRuleProblem = hasRuleProblem;
    }

    public Boolean getHasPerformanceProblem() {
        return hasPerformanceProblem;
    }

    public void setHasPerformanceProblem(Boolean hasPerformanceProblem) {
        this.hasPerformanceProblem = hasPerformanceProblem;
    }

    public List<String> getDetectedInputs() {
        return detectedInputs;
    }

    public void setDetectedInputs(List<String> detectedInputs) {
        this.detectedInputs = detectedInputs;
    }

    public List<String> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<String> timeline) {
        this.timeline = timeline;
    }

    public List<WorkflowV2LineDto> getLines() {
        return lines;
    }

    public void setLines(List<WorkflowV2LineDto> lines) {
        this.lines = lines;
    }
}