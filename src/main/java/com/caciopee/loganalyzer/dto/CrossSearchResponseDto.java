package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

public class CrossSearchResponseDto {

    private String searchType;
    private String value;
    private String businessField;
    private Long totalMatchingLogs;
    private boolean truncated;
    private List<CrossSearchHitDto> sessionHits = new ArrayList<>();
    private List<LogEntryViewDto> sampleLogs = new ArrayList<>();
    private String summary;

    public String getSearchType() { return searchType; }
    public void setSearchType(String searchType) { this.searchType = searchType; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getBusinessField() { return businessField; }
    public void setBusinessField(String businessField) { this.businessField = businessField; }

    public Long getTotalMatchingLogs() { return totalMatchingLogs; }
    public void setTotalMatchingLogs(Long totalMatchingLogs) { this.totalMatchingLogs = totalMatchingLogs; }

    public boolean isTruncated() { return truncated; }
    public void setTruncated(boolean truncated) { this.truncated = truncated; }

    public List<CrossSearchHitDto> getSessionHits() { return sessionHits; }
    public void setSessionHits(List<CrossSearchHitDto> sessionHits) { this.sessionHits = sessionHits; }

    public List<LogEntryViewDto> getSampleLogs() { return sampleLogs; }
    public void setSampleLogs(List<LogEntryViewDto> sampleLogs) { this.sampleLogs = sampleLogs; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
