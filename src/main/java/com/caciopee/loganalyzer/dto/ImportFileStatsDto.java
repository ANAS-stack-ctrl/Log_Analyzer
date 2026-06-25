package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;

public class ImportFileStatsDto {

    private String sourceFileName;
    private String sourceRelativePath;
    private long logCount;
    private long errorCount;
    private long slowLogCount;
    private Long maxDurationMs;
    private LocalDateTime firstLogTimestamp;
    private LocalDateTime lastLogTimestamp;

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public String getSourceRelativePath() {
        return sourceRelativePath;
    }

    public void setSourceRelativePath(String sourceRelativePath) {
        this.sourceRelativePath = sourceRelativePath;
    }

    public long getLogCount() {
        return logCount;
    }

    public void setLogCount(long logCount) {
        this.logCount = logCount;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(long errorCount) {
        this.errorCount = errorCount;
    }

    public long getSlowLogCount() {
        return slowLogCount;
    }

    public void setSlowLogCount(long slowLogCount) {
        this.slowLogCount = slowLogCount;
    }

    public Long getMaxDurationMs() {
        return maxDurationMs;
    }

    public void setMaxDurationMs(Long maxDurationMs) {
        this.maxDurationMs = maxDurationMs;
    }

    public LocalDateTime getFirstLogTimestamp() {
        return firstLogTimestamp;
    }

    public void setFirstLogTimestamp(LocalDateTime firstLogTimestamp) {
        this.firstLogTimestamp = firstLogTimestamp;
    }

    public LocalDateTime getLastLogTimestamp() {
        return lastLogTimestamp;
    }

    public void setLastLogTimestamp(LocalDateTime lastLogTimestamp) {
        this.lastLogTimestamp = lastLogTimestamp;
    }
}
