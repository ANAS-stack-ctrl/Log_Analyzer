package com.caciopee.loganalyzer.parser;

import com.caciopee.loganalyzer.entity.LogEventType;
import com.caciopee.loganalyzer.entity.LogParseQuality;

import java.time.LocalDateTime;

public class ParsedLogEntry {

    private LocalDateTime logTimestamp;
    private String executionId;
    private String level;
    private String userName;
    private String sourceClass;
    private String processName;
    private String message;
    private String environment;
    private String serverName;
    private String appVersion;
    private String correlationId;
    private Integer logCode;
    private String rawLog;

    private String eventType;
    private LogEventType detectedEventType;

    private String fieldName;
    private String interfaceField;
    private String fieldClassCode;
    private String parsedType;
    private String parsedValue;

    private String relationName;
    private String relationKey;

    private String businessKey;
    private String mandatoryField;

    private String errorColumn;
    private String errorAttribute;
    private String errorValue;
    private String errorBusinessKey;

    private Boolean error;
    private String businessMeaning;

    private LogParseQuality parseQuality;
    private boolean ambiguousMessage;
    private boolean incomplete;

    private String rawMessage;
    private String messageTail;
    private String technicalId;

    public LocalDateTime getLogTimestamp() {
        return logTimestamp;
    }

    public void setLogTimestamp(LocalDateTime logTimestamp) {
        this.logTimestamp = logTimestamp;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
        this.error = level != null && "ERROR".equalsIgnoreCase(level.trim());
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getSourceClass() {
        return sourceClass;
    }

    public void setSourceClass(String sourceClass) {
        this.sourceClass = sourceClass;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Integer getLogCode() {
        return logCode;
    }

    public void setLogCode(Integer logCode) {
        this.logCode = logCode;
    }

    public String getRawLog() {
        return rawLog;
    }

    public void setRawLog(String rawLog) {
        this.rawLog = rawLog;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public LogEventType getDetectedEventType() {
        return detectedEventType;
    }

    public void setDetectedEventType(LogEventType detectedEventType) {
        this.detectedEventType = detectedEventType;
        if (detectedEventType != null) {
            this.eventType = detectedEventType.name();
        }
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

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
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

    public Boolean getError() {
        return error;
    }

    public void setError(Boolean error) {
        this.error = error;
    }

    public String getBusinessMeaning() {
        return businessMeaning;
    }

    public void setBusinessMeaning(String businessMeaning) {
        this.businessMeaning = businessMeaning;
    }

    public LogParseQuality getParseQuality() {
        return parseQuality;
    }

    public void setParseQuality(LogParseQuality parseQuality) {
        this.parseQuality = parseQuality;
    }

    public boolean isAmbiguousMessage() {
        return ambiguousMessage;
    }

    public void setAmbiguousMessage(boolean ambiguousMessage) {
        this.ambiguousMessage = ambiguousMessage;
    }

    public boolean isIncomplete() {
        return incomplete;
    }

    public void setIncomplete(boolean incomplete) {
        this.incomplete = incomplete;
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public void setRawMessage(String rawMessage) {
        this.rawMessage = rawMessage;
    }

    public String getMessageTail() {
        return messageTail;
    }

    public void setMessageTail(String messageTail) {
        this.messageTail = messageTail;
    }

    public String getTechnicalId() {
        return technicalId;
    }

    public void setTechnicalId(String technicalId) {
        this.technicalId = technicalId;
    }
}