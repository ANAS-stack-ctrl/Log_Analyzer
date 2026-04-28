package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ImportAnalysisSummaryDto {

    private Long importId;
    private String fileName;
    private String originalFileName;
    private String status;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    private long totalLines;
    private long parsedLines;
    private long failedLines;

    private long totalLogs;
    private long totalErrors;
    private long totalWarnings;
    private long totalInfos;

    private long ambiguousCount;
    private long incompleteCount;

    private double errorRate;

    private LocalDateTime firstLogTimestamp;
    private LocalDateTime lastLogTimestamp;

    private String sourceServer;
    private String sourceEnvironment;
    private String sourceAppVersion;

    private String summary;
    private String errorMessage;

    private List<CountValueDto> topEventTypes = new ArrayList<>();
    private List<CountValueDto> topSourceClasses = new ArrayList<>();
    private List<CountValueDto> topBusinessKeys = new ArrayList<>();
    private List<CountValueDto> topErrorAttributes = new ArrayList<>();
    private List<CountValueDto> levelDistribution = new ArrayList<>();
    private List<CountValueDto> parseQualityDistribution = new ArrayList<>();

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public long getTotalLines() {
        return totalLines;
    }

    public void setTotalLines(long totalLines) {
        this.totalLines = totalLines;
    }

    public long getParsedLines() {
        return parsedLines;
    }

    public void setParsedLines(long parsedLines) {
        this.parsedLines = parsedLines;
    }

    public long getFailedLines() {
        return failedLines;
    }

    public void setFailedLines(long failedLines) {
        this.failedLines = failedLines;
    }

    public long getTotalLogs() {
        return totalLogs;
    }

    public void setTotalLogs(long totalLogs) {
        this.totalLogs = totalLogs;
    }

    public long getTotalErrors() {
        return totalErrors;
    }

    public void setTotalErrors(long totalErrors) {
        this.totalErrors = totalErrors;
    }

    public long getTotalWarnings() {
        return totalWarnings;
    }

    public void setTotalWarnings(long totalWarnings) {
        this.totalWarnings = totalWarnings;
    }

    public long getTotalInfos() {
        return totalInfos;
    }

    public void setTotalInfos(long totalInfos) {
        this.totalInfos = totalInfos;
    }

    public long getAmbiguousCount() {
        return ambiguousCount;
    }

    public void setAmbiguousCount(long ambiguousCount) {
        this.ambiguousCount = ambiguousCount;
    }

    public long getIncompleteCount() {
        return incompleteCount;
    }

    public void setIncompleteCount(long incompleteCount) {
        this.incompleteCount = incompleteCount;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(double errorRate) {
        this.errorRate = errorRate;
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

    public String getSourceServer() {
        return sourceServer;
    }

    public void setSourceServer(String sourceServer) {
        this.sourceServer = sourceServer;
    }

    public String getSourceEnvironment() {
        return sourceEnvironment;
    }

    public void setSourceEnvironment(String sourceEnvironment) {
        this.sourceEnvironment = sourceEnvironment;
    }

    public String getSourceAppVersion() {
        return sourceAppVersion;
    }

    public void setSourceAppVersion(String sourceAppVersion) {
        this.sourceAppVersion = sourceAppVersion;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<CountValueDto> getTopEventTypes() {
        return topEventTypes;
    }

    public void setTopEventTypes(List<CountValueDto> topEventTypes) {
        this.topEventTypes = topEventTypes;
    }

    public List<CountValueDto> getTopSourceClasses() {
        return topSourceClasses;
    }

    public void setTopSourceClasses(List<CountValueDto> topSourceClasses) {
        this.topSourceClasses = topSourceClasses;
    }

    public List<CountValueDto> getTopBusinessKeys() {
        return topBusinessKeys;
    }

    public void setTopBusinessKeys(List<CountValueDto> topBusinessKeys) {
        this.topBusinessKeys = topBusinessKeys;
    }

    public List<CountValueDto> getTopErrorAttributes() {
        return topErrorAttributes;
    }

    public void setTopErrorAttributes(List<CountValueDto> topErrorAttributes) {
        this.topErrorAttributes = topErrorAttributes;
    }

    public List<CountValueDto> getLevelDistribution() {
        return levelDistribution;
    }

    public void setLevelDistribution(List<CountValueDto> levelDistribution) {
        this.levelDistribution = levelDistribution;
    }

    public List<CountValueDto> getParseQualityDistribution() {
        return parseQualityDistribution;
    }

    public void setParseQualityDistribution(List<CountValueDto> parseQualityDistribution) {
        this.parseQualityDistribution = parseQualityDistribution;
    }
}