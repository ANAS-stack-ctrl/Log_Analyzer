package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ImportDetailDto {

    private ImportAnalysisSummaryDto summary;
    private Long fileSize;
    private String fileHash;
    private LocalDateTime lastAccessedAt;
    private long distinctUsers;
    private long slowLogCount;
    private Long maxDurationMs;
    private List<ImportFileStatsDto> sourceFiles = new ArrayList<>();

    public ImportAnalysisSummaryDto getSummary() {
        return summary;
    }

    public void setSummary(ImportAnalysisSummaryDto summary) {
        this.summary = summary;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(LocalDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public long getDistinctUsers() {
        return distinctUsers;
    }

    public void setDistinctUsers(long distinctUsers) {
        this.distinctUsers = distinctUsers;
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

    public List<ImportFileStatsDto> getSourceFiles() {
        return sourceFiles;
    }

    public void setSourceFiles(List<ImportFileStatsDto> sourceFiles) {
        this.sourceFiles = sourceFiles;
    }
}
