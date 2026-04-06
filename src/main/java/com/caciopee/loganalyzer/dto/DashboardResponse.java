package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DashboardResponse {

    private long totalImports;
    private long totalLogs;
    private long totalErrors;
    private double errorRate;

    private long totalBusinessKeys;
    private long criticalImportsCount;

    private String topErrorType;
    private String topProblemField;
    private String mostCriticalImportLabel;

    private Map<String, Long> eventTypeDistribution;

    private List<String> topErrorMessages = new ArrayList<>();
    private List<String> topProblemFields = new ArrayList<>();
    private List<String> criticalImports = new ArrayList<>();

    public long getTotalImports() {
        return totalImports;
    }

    public void setTotalImports(long totalImports) {
        this.totalImports = totalImports;
    }

    public long getTotalLogs() {
        return totalLogs;
    }

    public void setTotalLogs(long totalLogs) {
        this.totalLogs = totalLogs;
    }

    public long getTotalErrors() {
        return totalErrors;
    }

    public void setTotalErrors(long totalErrors) {
        this.totalErrors = totalErrors;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(double errorRate) {
        this.errorRate = errorRate;
    }

    public long getTotalBusinessKeys() {
        return totalBusinessKeys;
    }

    public void setTotalBusinessKeys(long totalBusinessKeys) {
        this.totalBusinessKeys = totalBusinessKeys;
    }

    public long getCriticalImportsCount() {
        return criticalImportsCount;
    }

    public void setCriticalImportsCount(long criticalImportsCount) {
        this.criticalImportsCount = criticalImportsCount;
    }

    public String getTopErrorType() {
        return topErrorType;
    }

    public void setTopErrorType(String topErrorType) {
        this.topErrorType = topErrorType;
    }

    public String getTopProblemField() {
        return topProblemField;
    }

    public void setTopProblemField(String topProblemField) {
        this.topProblemField = topProblemField;
    }

    public String getMostCriticalImportLabel() {
        return mostCriticalImportLabel;
    }

    public void setMostCriticalImportLabel(String mostCriticalImportLabel) {
        this.mostCriticalImportLabel = mostCriticalImportLabel;
    }

    public Map<String, Long> getEventTypeDistribution() {
        return eventTypeDistribution;
    }

    public void setEventTypeDistribution(Map<String, Long> eventTypeDistribution) {
        this.eventTypeDistribution = eventTypeDistribution;
    }

    public List<String> getTopErrorMessages() {
        return topErrorMessages;
    }

    public void setTopErrorMessages(List<String> topErrorMessages) {
        this.topErrorMessages = topErrorMessages;
    }

    public List<String> getTopProblemFields() {
        return topProblemFields;
    }

    public void setTopProblemFields(List<String> topProblemFields) {
        this.topProblemFields = topProblemFields;
    }

    public List<String> getCriticalImports() {
        return criticalImports;
    }

    public void setCriticalImports(List<String> criticalImports) {
        this.criticalImports = criticalImports;
    }
}