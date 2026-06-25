package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

public class SessionComparisonResponseDto {

    private String sessionIdA;
    private String sessionIdB;
    private String processFilter;
    private SessionSnapshotDto snapshotA;
    private SessionSnapshotDto snapshotB;
    private List<SessionCompareDiffItemDto> differences = new ArrayList<>();
    private String summary;
    private String recommendation;

    public String getSessionIdA() { return sessionIdA; }
    public void setSessionIdA(String sessionIdA) { this.sessionIdA = sessionIdA; }

    public String getSessionIdB() { return sessionIdB; }
    public void setSessionIdB(String sessionIdB) { this.sessionIdB = sessionIdB; }

    public String getProcessFilter() { return processFilter; }
    public void setProcessFilter(String processFilter) { this.processFilter = processFilter; }

    public SessionSnapshotDto getSnapshotA() { return snapshotA; }
    public void setSnapshotA(SessionSnapshotDto snapshotA) { this.snapshotA = snapshotA; }

    public SessionSnapshotDto getSnapshotB() { return snapshotB; }
    public void setSnapshotB(SessionSnapshotDto snapshotB) { this.snapshotB = snapshotB; }

    public List<SessionCompareDiffItemDto> getDifferences() { return differences; }
    public void setDifferences(List<SessionCompareDiffItemDto> differences) { this.differences = differences; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
}
