package com.caciopee.loganalyzer.dto;

import java.util.List;
import java.util.Map;

public class BusinessKeyAnalysisResponse {

    private String requestedBusinessKey;
    private long totalRelatedLogs;

    private Map<String, Long> countByLevel;
    private Map<String, Long> countByEventType;

    private List<String> involvedFields;
    private List<String> involvedRelations;
    private List<String> importantMessages;
    private List<String> detectedErrors;

    private String summary;

    public String getRequestedBusinessKey() {
        return requestedBusinessKey;
    }

    public void setRequestedBusinessKey(String requestedBusinessKey) {
        this.requestedBusinessKey = requestedBusinessKey;
    }

    public long getTotalRelatedLogs() {
        return totalRelatedLogs;
    }

    public void setTotalRelatedLogs(long totalRelatedLogs) {
        this.totalRelatedLogs = totalRelatedLogs;
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

    public List<String> getInvolvedFields() {
        return involvedFields;
    }

    public void setInvolvedFields(List<String> involvedFields) {
        this.involvedFields = involvedFields;
    }

    public List<String> getInvolvedRelations() {
        return involvedRelations;
    }

    public void setInvolvedRelations(List<String> involvedRelations) {
        this.involvedRelations = involvedRelations;
    }

    public List<String> getImportantMessages() {
        return importantMessages;
    }

    public void setImportantMessages(List<String> importantMessages) {
        this.importantMessages = importantMessages;
    }

    public List<String> getDetectedErrors() {
        return detectedErrors;
    }

    public void setDetectedErrors(List<String> detectedErrors) {
        this.detectedErrors = detectedErrors;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}