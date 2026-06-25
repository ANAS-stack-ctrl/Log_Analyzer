package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

public class GraphLogSearchResponseDto {

    private long totalLogs;
    private long matchedCount;
    private int displayedCount;
    private String query;
    private List<GraphOccurrenceDto> hits = new ArrayList<>();

    public long getTotalLogs() { return totalLogs; }
    public void setTotalLogs(long totalLogs) { this.totalLogs = totalLogs; }

    public long getMatchedCount() { return matchedCount; }
    public void setMatchedCount(long matchedCount) { this.matchedCount = matchedCount; }

    public int getDisplayedCount() { return displayedCount; }
    public void setDisplayedCount(int displayedCount) { this.displayedCount = displayedCount; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public List<GraphOccurrenceDto> getHits() { return hits; }
    public void setHits(List<GraphOccurrenceDto> hits) { this.hits = hits; }
}
