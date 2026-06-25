package com.caciopee.loganalyzer.dto;

public class LatencyOriginRequestDto {

    private Long importId;
    private Long evidenceLogId;
    private String sessionId;
    private String uuid;
    private String filterCode;
    private String processName;

    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }

    public Long getEvidenceLogId() { return evidenceLogId; }
    public void setEvidenceLogId(Long evidenceLogId) { this.evidenceLogId = evidenceLogId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getFilterCode() { return filterCode; }
    public void setFilterCode(String filterCode) { this.filterCode = filterCode; }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
}
