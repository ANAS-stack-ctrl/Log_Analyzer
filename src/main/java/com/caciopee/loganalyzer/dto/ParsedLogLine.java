package com.caciopee.loganalyzer.dto;

import com.caciopee.loganalyzer.entity.LogParseQuality;

public class ParsedLogLine {

    private String timestamp;
    private String executionId;
    private String level;
    private String userName;
    private String sourceClass;
    private String processName;

    private String rawMessage;

    private String technicalId;
    private String environment;
    private String serverName;
    private String appVersion;
    private String correlationId;

    private LogParseQuality parseQuality = LogParseQuality.HEALTHY;
    private boolean ambiguousMessage;
    private boolean incomplete;

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
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

    public String getRawMessage() {
        return rawMessage;
    }

    public void setRawMessage(String rawMessage) {
        this.rawMessage = rawMessage;
    }

    public String getTechnicalId() {
        return technicalId;
    }

    public void setTechnicalId(String technicalId) {
        this.technicalId = technicalId;
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
}