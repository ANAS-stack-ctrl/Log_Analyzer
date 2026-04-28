package com.caciopee.loganalyzer.analysis.v2.model;

import java.util.ArrayList;
import java.util.List;

public class WorkflowGroupV2 {

    private String groupKey;
    private String uuid;
    private String transactionId;
    private String filterCode;
    private String processName;
    private String className;

    private final List<WorkflowEventV2> events = new ArrayList<>();

    public String getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
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

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public List<WorkflowEventV2> getEvents() {
        return events;
    }
}