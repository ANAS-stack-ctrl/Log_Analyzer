package com.caciopee.loganalyzer.analysis.v2.model;

import com.caciopee.loganalyzer.analysis.v2.WorkflowEventType;

import java.time.LocalDateTime;

public class WorkflowEventV2 {

    private Long logEntryId;
    private LocalDateTime timestamp;

    private String level;
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

    private WorkflowEventType workflowEventType;

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

    public String getEventTypeFromParser() {
        return eventTypeFromParser;
    }

    public void setEventTypeFromParser(String eventTypeFromParser) {
        this.eventTypeFromParser = eventTypeFromParser;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getFilterCode() {
        return filterCode;
    }

    public void setFilterCode(String filterCode) {
        this.filterCode = filterCode;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public void setRowCount(Integer rowCount) {
        this.rowCount = rowCount;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getMemoryMo() {
        return memoryMo;
    }

    public void setMemoryMo(Integer memoryMo) {
        this.memoryMo = memoryMo;
    }

    public WorkflowEventType getWorkflowEventType() {
        return workflowEventType;
    }

    public void setWorkflowEventType(WorkflowEventType workflowEventType) {
        this.workflowEventType = workflowEventType;
    }
}