package com.caciopee.loganalyzer.dto;

import java.util.List;
import java.util.Map;

public class AnalysisSummaryResponse {

    private long totalLogs;
    private long totalErrors;
    private long totalInfos;

    private Map<String, Long> countByLevel;
    private Map<String, Long> countByEventType;

    private List<String> topFields;
    private List<String> topBusinessKeys;
    private List<String> topErrorMessages;

    private String globalInterpretation;

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

    public long getTotalInfos() {
        return totalInfos;
    }

    public void setTotalInfos(long totalInfos) {
        this.totalInfos = totalInfos;
    }

    public Map<String, Long> getCountByLevel() {
        return countByLevel;
    }

    public void setCountByLevel(Map<String, Long> countByLevel) {
        this.countByLevel = countByLevel;
    }

    public Map<String, Long> getCountByEventType() {
        return countByEventType;
    }

    public void setCountByEventType(Map<String, Long> countByEventType) {
        this.countByEventType = countByEventType;
    }

    public List<String> getTopFields() {
        return topFields;
    }

    public void setTopFields(List<String> topFields) {
        this.topFields = topFields;
    }

    public List<String> getTopBusinessKeys() {
        return topBusinessKeys;
    }

    public void setTopBusinessKeys(List<String> topBusinessKeys) {
        this.topBusinessKeys = topBusinessKeys;
    }

    public List<String> getTopErrorMessages() {
        return topErrorMessages;
    }

    public void setTopErrorMessages(List<String> topErrorMessages) {
        this.topErrorMessages = topErrorMessages;
    }

    public String getGlobalInterpretation() {
        return globalInterpretation;
    }

    public void setGlobalInterpretation(String globalInterpretation) {
        this.globalInterpretation = globalInterpretation;
    }
}