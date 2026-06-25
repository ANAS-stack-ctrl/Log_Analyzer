package com.caciopee.loganalyzer.analysis.model;

/**
 * Champs résolus pour le graphe relationnel et l'analyse de groupe.
 */
public class GraphExtraction {

    private String process;
    private String task;
    private String action;
    private String filter;
    private String object;
    private String uuid;
    private String transactionId;

    private boolean warning;
    private boolean zeroResult;
    private boolean error;
    private boolean save;

    private Long durationMs;
    private Integer memoryMo;
    private Integer rowCount;

    public String getProcess() { return process; }
    public void setProcess(String process) { this.process = process; }

    public String getTask() { return task; }
    public void setTask(String task) { this.task = task; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getFilter() { return filter; }
    public void setFilter(String filter) { this.filter = filter; }

    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public boolean isWarning() { return warning; }
    public void setWarning(boolean warning) { this.warning = warning; }

    public boolean isZeroResult() { return zeroResult; }
    public void setZeroResult(boolean zeroResult) { this.zeroResult = zeroResult; }

    public boolean isError() { return error; }
    public void setError(boolean error) { this.error = error; }

    public boolean isSave() { return save; }
    public void setSave(boolean save) { this.save = save; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public Integer getMemoryMo() { return memoryMo; }
    public void setMemoryMo(Integer memoryMo) { this.memoryMo = memoryMo; }

    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }
}
