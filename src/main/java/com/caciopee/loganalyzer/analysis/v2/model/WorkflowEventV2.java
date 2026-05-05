package com.caciopee.loganalyzer.analysis.v2.model;

import com.caciopee.loganalyzer.analysis.v2.WorkflowEventType;

import java.time.LocalDateTime;

public class WorkflowEventV2 {

    private Long logEntryId;
    private LocalDateTime timestamp;

    private String level;
    private String userName;
    private String processName;
    private String sourceClass;
    private String message;

    private String eventTypeFromParser;
    private String correlationId;
    private String sessionId;
    private String businessKey;

    private String uuid;
    private String transactionId;
    private String filterCode;
    private String className;
    private String threadName;

    private Integer rowCount;
    private Long durationMs;
    private Integer memoryMo;

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

    private WorkflowEventType workflowEventType;

    public Long getLogEntryId() { return logEntryId; }
    public void setLogEntryId(Long logEntryId) { this.logEntryId = logEntryId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }

    public String getSourceClass() { return sourceClass; }
    public void setSourceClass(String sourceClass) { this.sourceClass = sourceClass; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getEventTypeFromParser() { return eventTypeFromParser; }
    public void setEventTypeFromParser(String eventTypeFromParser) { this.eventTypeFromParser = eventTypeFromParser; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getFilterCode() { return filterCode; }
    public void setFilterCode(String filterCode) { this.filterCode = filterCode; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getThreadName() { return threadName; }
    public void setThreadName(String threadName) { this.threadName = threadName; }

    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public Integer getMemoryMo() { return memoryMo; }
    public void setMemoryMo(Integer memoryMo) { this.memoryMo = memoryMo; }

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

    public WorkflowEventType getWorkflowEventType() { return workflowEventType; }
    public void setWorkflowEventType(WorkflowEventType workflowEventType) { this.workflowEventType = workflowEventType; }
}