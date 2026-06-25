package com.caciopee.loganalyzer.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "log_imports",
        indexes = {
                @Index(name = "idx_log_imports_status", columnList = "status"),
                @Index(name = "idx_log_imports_started_at", columnList = "started_at"),
                @Index(name = "idx_log_imports_file_hash", columnList = "file_hash"),
                @Index(name = "idx_log_imports_source_server", columnList = "source_server"),
                @Index(name = "idx_log_imports_source_environment", columnList = "source_environment")
        })
public class LogImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    @Column(name = "file_hash", length = 128)
    private String fileHash;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private LogImportStatus status;

    @Column(name = "total_lines", nullable = false)
    private Integer totalLines = 0;

    @Column(name = "parsed_lines", nullable = false)
    private Integer parsedLines = 0;

    @Column(name = "failed_lines", nullable = false)
    private Integer failedLines = 0;

    @Column(name = "total_errors", nullable = false)
    private Integer totalErrors = 0;

    @Column(name = "total_infos", nullable = false)
    private Integer totalInfos = 0;

    @Column(name = "total_warnings", nullable = false)
    private Integer totalWarnings = 0;

    @Column(name = "source_server", length = 100)
    private String sourceServer;

    @Column(name = "source_environment", length = 100)
    private String sourceEnvironment;

    @Column(name = "source_app_version", length = 255)
    private String sourceAppVersion;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "processing_file_count")
    private Integer processingFileCount;

    @Column(name = "processing_file_index")
    private Integer processingFileIndex;

    @Column(name = "processing_current_file", length = 500)
    private String processingCurrentFile;

    @Column(name = "processing_percent")
    private Integer processingPercent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @OneToMany(mappedBy = "logImport", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<LogEntry> logEntries = new ArrayList<>();

    public LogImport() {
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        if (startedAt == null) {
            startedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = LogImportStatus.PROCESSING;
        }
        if (totalLines == null) totalLines = 0;
        if (parsedLines == null) parsedLines = 0;
        if (failedLines == null) failedLines = 0;
        if (totalErrors == null) totalErrors = 0;
        if (totalInfos == null) totalInfos = 0;
        if (totalWarnings == null) totalWarnings = 0;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
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

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
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

    public LogImportStatus getStatus() {
        return status;
    }

    public void setStatus(LogImportStatus status) {
        this.status = status;
    }

    public Integer getTotalLines() {
        return totalLines;
    }

    public void setTotalLines(Integer totalLines) {
        this.totalLines = totalLines;
    }

    public Integer getParsedLines() {
        return parsedLines;
    }

    public void setParsedLines(Integer parsedLines) {
        this.parsedLines = parsedLines;
    }

    public Integer getFailedLines() {
        return failedLines;
    }

    public void setFailedLines(Integer failedLines) {
        this.failedLines = failedLines;
    }

    public Integer getTotalErrors() {
        return totalErrors;
    }

    public void setTotalErrors(Integer totalErrors) {
        this.totalErrors = totalErrors;
    }

    public Integer getTotalInfos() {
        return totalInfos;
    }

    public void setTotalInfos(Integer totalInfos) {
        this.totalInfos = totalInfos;
    }

    public Integer getTotalWarnings() {
        return totalWarnings;
    }

    public void setTotalWarnings(Integer totalWarnings) {
        this.totalWarnings = totalWarnings;
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

    public Integer getProcessingFileCount() {
        return processingFileCount;
    }

    public void setProcessingFileCount(Integer processingFileCount) {
        this.processingFileCount = processingFileCount;
    }

    public Integer getProcessingFileIndex() {
        return processingFileIndex;
    }

    public void setProcessingFileIndex(Integer processingFileIndex) {
        this.processingFileIndex = processingFileIndex;
    }

    public String getProcessingCurrentFile() {
        return processingCurrentFile;
    }

    public void setProcessingCurrentFile(String processingCurrentFile) {
        this.processingCurrentFile = processingCurrentFile;
    }

    public Integer getProcessingPercent() {
        return processingPercent;
    }

    public void setProcessingPercent(Integer processingPercent) {
        this.processingPercent = processingPercent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(LocalDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public List<LogEntry> getLogEntries() {
        return logEntries;
    }

    public void setLogEntries(List<LogEntry> logEntries) {
        this.logEntries = logEntries;
    }
}