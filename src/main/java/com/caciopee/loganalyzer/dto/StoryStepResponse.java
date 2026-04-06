package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;

public class StoryStepResponse {

    private Long logId;
    private LocalDateTime timestamp;
    private String level;
    private String eventType;
    private String fieldName;
    private String interfaceField;
    private String relationName;
    private String businessKey;
    private String originalMessage;
    private String humanExplanation;

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
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

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getInterfaceField() {
        return interfaceField;
    }

    public void setInterfaceField(String interfaceField) {
        this.interfaceField = interfaceField;
    }

    public String getRelationName() {
        return relationName;
    }

    public void setRelationName(String relationName) {
        this.relationName = relationName;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getOriginalMessage() {
        return originalMessage;
    }

    public void setOriginalMessage(String originalMessage) {
        this.originalMessage = originalMessage;
    }

    public String getHumanExplanation() {
        return humanExplanation;
    }

    public void setHumanExplanation(String humanExplanation) {
        this.humanExplanation = humanExplanation;
    }
}