package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;

public class LogGroupDto {

    private String groupBy;
    private String groupKey;

    private Long totalLogs;
    private Long errorCount;
    private Long warningCount;
    private Long infoCount;

    private LocalDateTime firstTimestamp;
    private LocalDateTime lastTimestamp;

    private String mainProcessName;
    private String mainUserName;
    private String mainFileName;

    private String summary;

    public String getGroupBy() {
        return groupBy;
    }

    public void setGroupBy(String groupBy) {
        this.groupBy = groupBy;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
    }

    public Long getTotalLogs() {
        return totalLogs;
    }

    public void setTotalLogs(Long totalLogs) {
        this.totalLogs = totalLogs;
    }

    public Long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(Long errorCount) {
        this.errorCount = errorCount;
    }

    public Long getWarningCount() {
        return warningCount;
    }

    public void setWarningCount(Long warningCount) {
        this.warningCount = warningCount;
    }

    public Long getInfoCount() {
        return infoCount;
    }

    public void setInfoCount(Long infoCount) {
        this.infoCount = infoCount;
    }

    public LocalDateTime getFirstTimestamp() {
        return firstTimestamp;
    }

    public void setFirstTimestamp(LocalDateTime firstTimestamp) {
        this.firstTimestamp = firstTimestamp;
    }

    public LocalDateTime getLastTimestamp() {
        return lastTimestamp;
    }

    public void setLastTimestamp(LocalDateTime lastTimestamp) {
        this.lastTimestamp = lastTimestamp;
    }

    public String getMainProcessName() {
        return mainProcessName;
    }

    public void setMainProcessName(String mainProcessName) {
        this.mainProcessName = mainProcessName;
    }

    public String getMainUserName() {
        return mainUserName;
    }

    public void setMainUserName(String mainUserName) {
        this.mainUserName = mainUserName;
    }

    public String getMainFileName() {
        return mainFileName;
    }

    public void setMainFileName(String mainFileName) {
        this.mainFileName = mainFileName;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}