package com.caciopee.loganalyzer.rag.dto;

/**
 * Une "source" = une fenêtre de logs réelle utilisée pour répondre. Affichée à
 * l'utilisateur comme PREUVE (c'est ce qui rend la réponse vérifiable).
 */
public class RagSourceDto {

    private String ref;          // ex. "S1" — cité dans la réponse
    private Long importId;
    private String sessionId;
    private String processName;
    private String filterCode;
    private String userName;
    private boolean hasError;
    private Long maxDurationMs;
    private String firstTs;
    private String lastTs;
    private Long firstLogId;
    private Long lastLogId;
    private int lineCount;
    private double similarity;   // 0..1
    private String content;      // les vraies lignes de logs

    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }
    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
    public String getFilterCode() { return filterCode; }
    public void setFilterCode(String filterCode) { this.filterCode = filterCode; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public boolean isHasError() { return hasError; }
    public void setHasError(boolean hasError) { this.hasError = hasError; }
    public Long getMaxDurationMs() { return maxDurationMs; }
    public void setMaxDurationMs(Long maxDurationMs) { this.maxDurationMs = maxDurationMs; }
    public String getFirstTs() { return firstTs; }
    public void setFirstTs(String firstTs) { this.firstTs = firstTs; }
    public String getLastTs() { return lastTs; }
    public void setLastTs(String lastTs) { this.lastTs = lastTs; }
    public Long getFirstLogId() { return firstLogId; }
    public void setFirstLogId(Long firstLogId) { this.firstLogId = firstLogId; }
    public Long getLastLogId() { return lastLogId; }
    public void setLastLogId(Long lastLogId) { this.lastLogId = lastLogId; }
    public int getLineCount() { return lineCount; }
    public void setLineCount(int lineCount) { this.lineCount = lineCount; }
    public double getSimilarity() { return similarity; }
    public void setSimilarity(double similarity) { this.similarity = similarity; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
