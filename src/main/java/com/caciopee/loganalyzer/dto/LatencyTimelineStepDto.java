package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;

public class LatencyTimelineStepDto {

    private int stepOrder;
    private String stepType;
    private String operationName;
    private Long durationMs;
    private Integer rowCount;
    private Integer memoryMo;
    private String threadInfo;
    private Long logId;
    private LocalDateTime timestamp;
    private String detail;
    private boolean bottleneck;

    public int getStepOrder() { return stepOrder; }
    public void setStepOrder(int stepOrder) { this.stepOrder = stepOrder; }

    public String getStepType() { return stepType; }
    public void setStepType(String stepType) { this.stepType = stepType; }

    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }

    public Integer getMemoryMo() { return memoryMo; }
    public void setMemoryMo(Integer memoryMo) { this.memoryMo = memoryMo; }

    public String getThreadInfo() { return threadInfo; }
    public void setThreadInfo(String threadInfo) { this.threadInfo = threadInfo; }

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public boolean isBottleneck() { return bottleneck; }
    public void setBottleneck(boolean bottleneck) { this.bottleneck = bottleneck; }
}
