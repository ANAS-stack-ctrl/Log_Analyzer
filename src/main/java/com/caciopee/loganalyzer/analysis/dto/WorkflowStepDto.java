package com.caciopee.loganalyzer.analysis.dto;

import com.caciopee.loganalyzer.analysis.WorkflowStepType;

import java.time.LocalDateTime;

public class WorkflowStepDto {

    private Long logEntryId;
    private LocalDateTime timestamp;
    private String level;
    private String processName;
    private String sourceClass;
    private String message;
    private String businessMeaning;

    private String extractedUuid;
    private String extractedTransactionId;
    private String extractedFilterCode;
    private String extractedThreadName;
    private String extractedClassName;
    private Integer extractedRowCount;
    private Long extractedDurationMs;
    private Integer extractedMemoryMo;

    private String eventType;
    private String stepCode;
    private String fieldName;
    private String interfaceField;
    private String fieldClassCode;
    private String parsedType;
    private String parsedValue;
    private String businessKey;
    private String relationName;
    private String relationKey;
    private String mandatoryField;
    private String errorColumn;
    private String errorAttribute;
    private String errorValue;
    private String errorBusinessKey;

    private WorkflowStepType stepType;

    public Long getLogEntryId() {
        return logEntryId;
    }

    public void setLogEntryId(Long logEntryId) {
        this.logEntryId = logEntryId;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getBusinessMeaning() {
        return businessMeaning;
    }

    public void setBusinessMeaning(String businessMeaning) {
        this.businessMeaning = businessMeaning;
    }

    public String getExtractedUuid() {
        return extractedUuid;
    }

    public void setExtractedUuid(String extractedUuid) {
        this.extractedUuid = extractedUuid;
    }

    public String getExtractedTransactionId() {
        return extractedTransactionId;
    }

    public void setExtractedTransactionId(String extractedTransactionId) {
        this.extractedTransactionId = extractedTransactionId;
    }

    public String getExtractedFilterCode() {
        return extractedFilterCode;
    }

    public void setExtractedFilterCode(String extractedFilterCode) {
        this.extractedFilterCode = extractedFilterCode;
    }

    public String getExtractedThreadName() {
        return extractedThreadName;
    }

    public void setExtractedThreadName(String extractedThreadName) {
        this.extractedThreadName = extractedThreadName;
    }

    public String getExtractedClassName() {
        return extractedClassName;
    }

    public void setExtractedClassName(String extractedClassName) {
        this.extractedClassName = extractedClassName;
    }

    public Integer getExtractedRowCount() {
        return extractedRowCount;
    }

    public void setExtractedRowCount(Integer extractedRowCount) {
        this.extractedRowCount = extractedRowCount;
    }

    public Long getExtractedDurationMs() {
        return extractedDurationMs;
    }

    public void setExtractedDurationMs(Long extractedDurationMs) {
        this.extractedDurationMs = extractedDurationMs;
    }

    public Integer getExtractedMemoryMo() {
        return extractedMemoryMo;
    }

    public void setExtractedMemoryMo(Integer extractedMemoryMo) {
        this.extractedMemoryMo = extractedMemoryMo;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getStepCode() {
        return stepCode;
    }

    public void setStepCode(String stepCode) {
        this.stepCode = stepCode;
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

    public String getFieldClassCode() {
        return fieldClassCode;
    }

    public void setFieldClassCode(String fieldClassCode) {
        this.fieldClassCode = fieldClassCode;
    }

    public String getParsedType() {
        return parsedType;
    }

    public void setParsedType(String parsedType) {
        this.parsedType = parsedType;
    }

    public String getParsedValue() {
        return parsedValue;
    }

    public void setParsedValue(String parsedValue) {
        this.parsedValue = parsedValue;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getRelationName() {
        return relationName;
    }

    public void setRelationName(String relationName) {
        this.relationName = relationName;
    }

    public String getRelationKey() {
        return relationKey;
    }

    public void setRelationKey(String relationKey) {
        this.relationKey = relationKey;
    }

    public String getMandatoryField() {
        return mandatoryField;
    }

    public void setMandatoryField(String mandatoryField) {
        this.mandatoryField = mandatoryField;
    }

    public String getErrorColumn() {
        return errorColumn;
    }

    public void setErrorColumn(String errorColumn) {
        this.errorColumn = errorColumn;
    }

    public String getErrorAttribute() {
        return errorAttribute;
    }

    public void setErrorAttribute(String errorAttribute) {
        this.errorAttribute = errorAttribute;
    }

    public String getErrorValue() {
        return errorValue;
    }

    public void setErrorValue(String errorValue) {
        this.errorValue = errorValue;
    }

    public String getErrorBusinessKey() {
        return errorBusinessKey;
    }

    public void setErrorBusinessKey(String errorBusinessKey) {
        this.errorBusinessKey = errorBusinessKey;
    }

    public WorkflowStepType getStepType() {
        return stepType;
    }

    public void setStepType(WorkflowStepType stepType) {
        this.stepType = stepType;
    }
}