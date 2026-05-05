package com.caciopee.loganalyzer.analysis.model;

public class ExtractedLogContext {

    private String extractedUuid;
    private String extractedTransactionId;
    private String extractedFilterCode;
    private String extractedClassName;
    private String extractedThreadName;

    private Integer extractedRowCount;
    private Long extractedDurationMs;
    private Integer extractedMemoryMo;

    private String extractedTaskName;
    private String extractedActionName;
    private String extractedProcessName;
    private String extractedClientIp;

    private String extractedTriggerName;
    private String extractedCheckpoint;
    private String extractedBusinessObjectKey;
    private String extractedBusinessObjectValue;

    private String extractedQueryParameterName;
    private String extractedQueryParameterValue;

    public String getExtractedUuid() { return extractedUuid; }
    public void setExtractedUuid(String extractedUuid) { this.extractedUuid = extractedUuid; }

    public String getExtractedTransactionId() { return extractedTransactionId; }
    public void setExtractedTransactionId(String extractedTransactionId) { this.extractedTransactionId = extractedTransactionId; }

    public String getExtractedFilterCode() { return extractedFilterCode; }
    public void setExtractedFilterCode(String extractedFilterCode) { this.extractedFilterCode = extractedFilterCode; }

    public String getExtractedClassName() { return extractedClassName; }
    public void setExtractedClassName(String extractedClassName) { this.extractedClassName = extractedClassName; }

    public String getExtractedThreadName() { return extractedThreadName; }
    public void setExtractedThreadName(String extractedThreadName) { this.extractedThreadName = extractedThreadName; }

    public Integer getExtractedRowCount() { return extractedRowCount; }
    public void setExtractedRowCount(Integer extractedRowCount) { this.extractedRowCount = extractedRowCount; }

    public Long getExtractedDurationMs() { return extractedDurationMs; }
    public void setExtractedDurationMs(Long extractedDurationMs) { this.extractedDurationMs = extractedDurationMs; }

    public Integer getExtractedMemoryMo() { return extractedMemoryMo; }
    public void setExtractedMemoryMo(Integer extractedMemoryMo) { this.extractedMemoryMo = extractedMemoryMo; }

    public String getExtractedTaskName() { return extractedTaskName; }
    public void setExtractedTaskName(String extractedTaskName) { this.extractedTaskName = extractedTaskName; }

    public String getExtractedActionName() { return extractedActionName; }
    public void setExtractedActionName(String extractedActionName) { this.extractedActionName = extractedActionName; }

    public String getExtractedProcessName() { return extractedProcessName; }
    public void setExtractedProcessName(String extractedProcessName) { this.extractedProcessName = extractedProcessName; }

    public String getExtractedClientIp() { return extractedClientIp; }
    public void setExtractedClientIp(String extractedClientIp) { this.extractedClientIp = extractedClientIp; }

    public String getExtractedTriggerName() { return extractedTriggerName; }
    public void setExtractedTriggerName(String extractedTriggerName) { this.extractedTriggerName = extractedTriggerName; }

    public String getExtractedCheckpoint() { return extractedCheckpoint; }
    public void setExtractedCheckpoint(String extractedCheckpoint) { this.extractedCheckpoint = extractedCheckpoint; }

    public String getExtractedBusinessObjectKey() { return extractedBusinessObjectKey; }
    public void setExtractedBusinessObjectKey(String extractedBusinessObjectKey) { this.extractedBusinessObjectKey = extractedBusinessObjectKey; }

    public String getExtractedBusinessObjectValue() { return extractedBusinessObjectValue; }
    public void setExtractedBusinessObjectValue(String extractedBusinessObjectValue) { this.extractedBusinessObjectValue = extractedBusinessObjectValue; }

    public String getExtractedQueryParameterName() { return extractedQueryParameterName; }
    public void setExtractedQueryParameterName(String extractedQueryParameterName) { this.extractedQueryParameterName = extractedQueryParameterName; }

    public String getExtractedQueryParameterValue() { return extractedQueryParameterValue; }
    public void setExtractedQueryParameterValue(String extractedQueryParameterValue) { this.extractedQueryParameterValue = extractedQueryParameterValue; }
}