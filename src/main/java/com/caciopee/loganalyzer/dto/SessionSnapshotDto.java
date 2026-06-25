package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;

public class SessionSnapshotDto {

    private String sessionId;
    private Long totalLogs;
    private Long errorCount;
    private Long warningCount;
    private Long zeroResultCount;
    private String dominantProcess;
    private LocalDateTime firstTimestamp;
    private LocalDateTime lastTimestamp;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Long getTotalLogs() { return totalLogs; }
    public void setTotalLogs(Long totalLogs) { this.totalLogs = totalLogs; }

    public Long getErrorCount() { return errorCount; }
    public void setErrorCount(Long errorCount) { this.errorCount = errorCount; }

    public Long getWarningCount() { return warningCount; }
    public void setWarningCount(Long warningCount) { this.warningCount = warningCount; }

    public Long getZeroResultCount() { return zeroResultCount; }
    public void setZeroResultCount(Long zeroResultCount) { this.zeroResultCount = zeroResultCount; }

    public String getDominantProcess() { return dominantProcess; }
    public void setDominantProcess(String dominantProcess) { this.dominantProcess = dominantProcess; }

    public LocalDateTime getFirstTimestamp() { return firstTimestamp; }
    public void setFirstTimestamp(LocalDateTime firstTimestamp) { this.firstTimestamp = firstTimestamp; }

    public LocalDateTime getLastTimestamp() { return lastTimestamp; }
    public void setLastTimestamp(LocalDateTime lastTimestamp) { this.lastTimestamp = lastTimestamp; }
}
