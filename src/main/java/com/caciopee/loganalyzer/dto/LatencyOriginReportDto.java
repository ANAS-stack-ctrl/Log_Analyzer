package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

public class LatencyOriginReportDto {

    private Long importId;
    private Long anchorLogId;
    private String sessionId;
    private String uuid;
    private String userName;
    private String processName;
    private String filterCode;
    private Long maxDurationMs;
    private String scopeDescription;
    private int logsInScope;
    private int logsInWindow;
    private String analysisConfidence;
    private String primaryCause;
    private String narrativeSummary;
    private String chainExplanation;
    private String chronologicalText;
    private String clientSummary;
    private List<LatencyTimelineStepDto> timelineSteps = new ArrayList<>();

    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }

    public Long getAnchorLogId() { return anchorLogId; }
    public void setAnchorLogId(Long anchorLogId) { this.anchorLogId = anchorLogId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }

    public String getFilterCode() { return filterCode; }
    public void setFilterCode(String filterCode) { this.filterCode = filterCode; }

    public Long getMaxDurationMs() { return maxDurationMs; }
    public void setMaxDurationMs(Long maxDurationMs) { this.maxDurationMs = maxDurationMs; }

    public String getScopeDescription() { return scopeDescription; }
    public void setScopeDescription(String scopeDescription) { this.scopeDescription = scopeDescription; }

    public int getLogsInScope() { return logsInScope; }
    public void setLogsInScope(int logsInScope) { this.logsInScope = logsInScope; }

    public int getLogsInWindow() { return logsInWindow; }
    public void setLogsInWindow(int logsInWindow) { this.logsInWindow = logsInWindow; }

    public String getAnalysisConfidence() { return analysisConfidence; }
    public void setAnalysisConfidence(String analysisConfidence) { this.analysisConfidence = analysisConfidence; }

    public String getPrimaryCause() { return primaryCause; }
    public void setPrimaryCause(String primaryCause) { this.primaryCause = primaryCause; }

    public String getNarrativeSummary() { return narrativeSummary; }
    public void setNarrativeSummary(String narrativeSummary) { this.narrativeSummary = narrativeSummary; }

    public String getChainExplanation() { return chainExplanation; }
    public void setChainExplanation(String chainExplanation) { this.chainExplanation = chainExplanation; }

    public String getClientSummary() { return clientSummary; }
    public void setClientSummary(String clientSummary) { this.clientSummary = clientSummary; }

    public String getChronologicalText() { return chronologicalText; }
    public void setChronologicalText(String chronologicalText) { this.chronologicalText = chronologicalText; }

    public List<LatencyTimelineStepDto> getTimelineSteps() { return timelineSteps; }
    public void setTimelineSteps(List<LatencyTimelineStepDto> timelineSteps) { this.timelineSteps = timelineSteps; }
}
