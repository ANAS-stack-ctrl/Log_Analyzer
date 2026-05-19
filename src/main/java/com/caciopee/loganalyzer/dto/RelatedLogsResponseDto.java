package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

public class RelatedLogsResponseDto {

    private String title;
    private String summary;
    private Long total;
    private String chronologicalExplanation;
    private List<RelatedLogDto> logs = new ArrayList<>();

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }

    public String getChronologicalExplanation() {
        return chronologicalExplanation;
    }

    public void setChronologicalExplanation(String chronologicalExplanation) {
        this.chronologicalExplanation = chronologicalExplanation;
    }

    public List<RelatedLogDto> getLogs() { return logs; }
    public void setLogs(List<RelatedLogDto> logs) { this.logs = logs; }
}