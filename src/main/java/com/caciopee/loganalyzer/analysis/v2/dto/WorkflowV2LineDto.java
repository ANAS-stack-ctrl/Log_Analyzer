package com.caciopee.loganalyzer.analysis.v2.dto;

public class WorkflowV2LineDto {

    private Long logEntryId;
    private String timestamp;
    private String level;
    private String eventType;
    private String processName;
    private String sourceClass;
    private String businessMeaning;
    private String message;

    public Long getLogEntryId() {
        return logEntryId;
    }

    public void setLogEntryId(Long logEntryId) {
        this.logEntryId = logEntryId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getSourceClass() {
        return sourceClass;
    }

    public void setSourceClass(String sourceClass) {
        this.sourceClass = sourceClass;
    }

    public String getBusinessMeaning() {
        return businessMeaning;
    }

    public void setBusinessMeaning(String businessMeaning) {
        this.businessMeaning = businessMeaning;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}