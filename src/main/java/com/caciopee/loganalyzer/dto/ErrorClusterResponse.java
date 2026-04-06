package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

public class ErrorClusterResponse {

    private String clusterKey;
    private String eventType;
    private String fieldName;
    private String severity;
    private long totalLogs;
    private long openLogs;
    private String sampleMessage;
    private List<Long> importIds = new ArrayList<>();
    private List<String> businessKeys = new ArrayList<>();

    public String getClusterKey() {
        return clusterKey;
    }

    public void setClusterKey(String clusterKey) {
        this.clusterKey = clusterKey;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public long getTotalLogs() {
        return totalLogs;
    }

    public void setTotalLogs(long totalLogs) {
        this.totalLogs = totalLogs;
    }

    public long getOpenLogs() {
        return openLogs;
    }

    public void setOpenLogs(long openLogs) {
        this.openLogs = openLogs;
    }

    public String getSampleMessage() {
        return sampleMessage;
    }

    public void setSampleMessage(String sampleMessage) {
        this.sampleMessage = sampleMessage;
    }

    public List<Long> getImportIds() {
        return importIds;
    }

    public void setImportIds(List<Long> importIds) {
        this.importIds = importIds;
    }

    public List<String> getBusinessKeys() {
        return businessKeys;
    }

    public void setBusinessKeys(List<String> businessKeys) {
        this.businessKeys = businessKeys;
    }
}