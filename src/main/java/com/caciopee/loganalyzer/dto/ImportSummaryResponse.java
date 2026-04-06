package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ImportSummaryResponse {

    private Long importId;
    private String fileName;
    private String originalFileName;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String status;

    private Integer totalLines;
    private Integer processedLines;
    private Integer failedLines;

    private long totalLogsStored;
    private long totalErrors;
    private long totalInfos;

    private Map<String, Long> countByLevel;
    private Map<String, Long> countByEventType;

    private List<String> topFields;
    private List<String> topBusinessKeys;
    private List<String> topErrorMessages;

    private String summary;

    public Long getImportId() {
        return importId;
    }

    public void setImportId(Long importId) {
        this.importId = importId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalLines() {
        return totalLines;
    }

    public void setTotalLines(Integer totalLines) {
        this.totalLines = totalLines;
    }

    public Integer getProcessedLines() {
        return processedLines;
    }

    public void setProcessedLines(Integer processedLines) {
        this.processedLines = processedLines;
    }

    public Integer getFailedLines() {
        return failedLines;
    }

    public void setFailedLines(Integer failedLines) {
        this.failedLines = failedLines;
    }

    public long getTotalLogsStored() {
        return totalLogsStored;
    }

    public void setTotalLogsStored(long totalLogsStored) {
        this.totalLogsStored = totalLogsStored;
    }

    public long getTotalErrors() {
        return totalErrors;
    }

    public void setTotalErrors(long totalErrors) {
        this.totalErrors = totalErrors;
    }

    public long getTotalInfos() {
        return totalInfos;
    }

    public void setTotalInfos(long totalInfos) {
        this.totalInfos = totalInfos;
    }

    public Map<String, Long> getCountByLevel() {
        return countByLevel;
    }

    public void setCountByLevel(Map<String, Long> countByLevel) {
        this.countByLevel = countByLevel;
    }

    public Map<String, Long> getCountByEventType() {
        return countByEventType;
    }

    public void setCountByEventType(Map<String, Long> countByEventType) {
        this.countByEventType = countByEventType;
    }

    public List<String> getTopFields() {
        return topFields;
    }

    public void setTopFields(List<String> topFields) {
        this.topFields = topFields;
    }

    public List<String> getTopBusinessKeys() {
        return topBusinessKeys;
    }

    public void setTopBusinessKeys(List<String> topBusinessKeys) {
        this.topBusinessKeys = topBusinessKeys;
    }

    public List<String> getTopErrorMessages() {
        return topErrorMessages;
    }

    public void setTopErrorMessages(List<String> topErrorMessages) {
        this.topErrorMessages = topErrorMessages;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}