package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;

public class GraphOccurrenceDto {
    private Long logId;
    private LocalDateTime timestamp;
    private String processName;
    /** Colonne process brute du log (ex. process.CHANGeAMPE-516557048-StartProcess-0-Start). */
    private String columnProcessName;
    private String actionName;
    private String filterCode;
    private String businessObject;
    private String messagePreview;
    /** Texte complet du log (rawLog ou message) pour affichage chronologique. */
    private String fullMessage;

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

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getColumnProcessName() {
        return columnProcessName;
    }

    public void setColumnProcessName(String columnProcessName) {
        this.columnProcessName = columnProcessName;
    }

    public String getActionName() {
        return actionName;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    public String getFilterCode() {
        return filterCode;
    }

    public void setFilterCode(String filterCode) {
        this.filterCode = filterCode;
    }

    public String getBusinessObject() {
        return businessObject;
    }

    public void setBusinessObject(String businessObject) {
        this.businessObject = businessObject;
    }

    public String getMessagePreview() {
        return messagePreview;
    }

    public void setMessagePreview(String messagePreview) {
        this.messagePreview = messagePreview;
    }

    public String getFullMessage() {
        return fullMessage;
    }

    public void setFullMessage(String fullMessage) {
        this.fullMessage = fullMessage;
    }
}
