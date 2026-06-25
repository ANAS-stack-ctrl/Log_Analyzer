package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

public class AssistantUserTopSessionsResponseDto {

    private String userName;
    private long totalSessionsFound;
    private List<UserSessionSummaryDto> topSessions = new ArrayList<>();

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public long getTotalSessionsFound() { return totalSessionsFound; }
    public void setTotalSessionsFound(long totalSessionsFound) { this.totalSessionsFound = totalSessionsFound; }

    public List<UserSessionSummaryDto> getTopSessions() { return topSessions; }
    public void setTopSessions(List<UserSessionSummaryDto> topSessions) { this.topSessions = topSessions; }

    public static class UserSessionSummaryDto {
        private String sessionId;
        private long totalLogs;
        private long errorCount;
        private long warningCount;
        private long zeroRowCount;
        private String firstTimestamp;
        private String lastTimestamp;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public long getTotalLogs() { return totalLogs; }
        public void setTotalLogs(long totalLogs) { this.totalLogs = totalLogs; }

        public long getErrorCount() { return errorCount; }
        public void setErrorCount(long errorCount) { this.errorCount = errorCount; }

        public long getWarningCount() { return warningCount; }
        public void setWarningCount(long warningCount) { this.warningCount = warningCount; }

        public long getZeroRowCount() { return zeroRowCount; }
        public void setZeroRowCount(long zeroRowCount) { this.zeroRowCount = zeroRowCount; }

        public String getFirstTimestamp() { return firstTimestamp; }
        public void setFirstTimestamp(String firstTimestamp) { this.firstTimestamp = firstTimestamp; }

        public String getLastTimestamp() { return lastTimestamp; }
        public void setLastTimestamp(String lastTimestamp) { this.lastTimestamp = lastTimestamp; }
    }
}

