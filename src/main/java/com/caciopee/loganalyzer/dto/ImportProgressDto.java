package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;

public class ImportProgressDto {

    private Long importId;
    private String status;
    private Integer percent;
    private Integer fileIndex;
    private Integer fileCount;
    private String currentFile;
    private Long linesRead;
    private Integer parsedLines;
    private boolean finished;
    private boolean success;
    private String message;
    private LocalDateTime updatedAt;

    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getPercent() { return percent; }
    public void setPercent(Integer percent) { this.percent = percent; }

    public Integer getFileIndex() { return fileIndex; }
    public void setFileIndex(Integer fileIndex) { this.fileIndex = fileIndex; }

    public Integer getFileCount() { return fileCount; }
    public void setFileCount(Integer fileCount) { this.fileCount = fileCount; }

    public String getCurrentFile() { return currentFile; }
    public void setCurrentFile(String currentFile) { this.currentFile = currentFile; }

    public Long getLinesRead() { return linesRead; }
    public void setLinesRead(Long linesRead) { this.linesRead = linesRead; }

    public Integer getParsedLines() { return parsedLines; }
    public void setParsedLines(Integer parsedLines) { this.parsedLines = parsedLines; }

    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
