package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;

public class CrossSearchHitDto {

    private Long importId;
    private String importFileName;
    private String sessionId;
    private String userName;
    private String dominantProcess;
    private Long logCount;
    private Long errorCount;
    private LocalDateTime firstTimestamp;
    private LocalDateTime lastTimestamp;
    private String sampleMessage;
    /** Premier log correspondant dans la session (pour contexte avant/après). */
    private Long anchorLogId;

    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }

    public String getImportFileName() { return importFileName; }
    public void setImportFileName(String importFileName) { this.importFileName = importFileName; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getDominantProcess() { return dominantProcess; }
    public void setDominantProcess(String dominantProcess) { this.dominantProcess = dominantProcess; }

    public Long getLogCount() { return logCount; }
    public void setLogCount(Long logCount) { this.logCount = logCount; }

    public Long getErrorCount() { return errorCount; }
    public void setErrorCount(Long errorCount) { this.errorCount = errorCount; }

    public LocalDateTime getFirstTimestamp() { return firstTimestamp; }
    public void setFirstTimestamp(LocalDateTime firstTimestamp) { this.firstTimestamp = firstTimestamp; }

    public LocalDateTime getLastTimestamp() { return lastTimestamp; }
    public void setLastTimestamp(LocalDateTime lastTimestamp) { this.lastTimestamp = lastTimestamp; }

    public String getSampleMessage() { return sampleMessage; }
    public void setSampleMessage(String sampleMessage) { this.sampleMessage = sampleMessage; }

    public Long getAnchorLogId() { return anchorLogId; }
    public void setAnchorLogId(Long anchorLogId) { this.anchorLogId = anchorLogId; }
}
