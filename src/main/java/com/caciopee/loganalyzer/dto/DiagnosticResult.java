package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

public class DiagnosticResult {

    private String scope;
    private String reference;

    private String status;
    private long totalLogs;
    private long errorCount;
    private double errorRate;

    private String dominantEventType;
    private String mainCause;
    private String failureStep;

    private Long firstCriticalLogId;
    private String firstCriticalErrorMessage;

    private String impact;
    private String severity;   // LOW / MEDIUM / HIGH / CRITICAL
    private String confidence; // LOW / MEDIUM / HIGH

    private List<String> affectedFields = new ArrayList<>();
    private List<String> affectedBusinessKeys = new ArrayList<>();
    private List<String> anomalies = new ArrayList<>();
    private List<String> recommendations = new ArrayList<>();

    private String executiveSummary;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public double getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(double errorRate) {
        this.errorRate = errorRate;
    }

    public String getDominantEventType() {
        return dominantEventType;
    }

    public void setDominantEventType(String dominantEventType) {
        this.dominantEventType = dominantEventType;
    }

    public String getMainCause() {
        return mainCause;
    }

    public void setMainCause(String mainCause) {
        this.mainCause = mainCause;
    }

    public String getFailureStep() {
        return failureStep;
    }

    public void setFailureStep(String failureStep) {
        this.failureStep = failureStep;
    }

    public Long getFirstCriticalLogId() {
        return firstCriticalLogId;
    }

    public void setFirstCriticalLogId(Long firstCriticalLogId) {
        this.firstCriticalLogId = firstCriticalLogId;
    }

    public String getFirstCriticalErrorMessage() {
        return firstCriticalErrorMessage;
    }

    public void setFirstCriticalErrorMessage(String firstCriticalErrorMessage) {
        this.firstCriticalErrorMessage = firstCriticalErrorMessage;
    }

    public String getImpact() {
        return impact;
    }

    public void setImpact(String impact) {
        this.impact = impact;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public List<String> getAffectedFields() {
        return affectedFields;
    }

    public void setAffectedFields(List<String> affectedFields) {
        this.affectedFields = affectedFields;
    }

    public List<String> getAffectedBusinessKeys() {
        return affectedBusinessKeys;
    }

    public void setAffectedBusinessKeys(List<String> affectedBusinessKeys) {
        this.affectedBusinessKeys = affectedBusinessKeys;
    }

    public List<String> getAnomalies() {
        return anomalies;
    }

    public void setAnomalies(List<String> anomalies) {
        this.anomalies = anomalies;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<String> recommendations) {
        this.recommendations = recommendations;
    }

    public String getExecutiveSummary() {
        return executiveSummary;
    }

    public void setExecutiveSummary(String executiveSummary) {
        this.executiveSummary = executiveSummary;
    }
}