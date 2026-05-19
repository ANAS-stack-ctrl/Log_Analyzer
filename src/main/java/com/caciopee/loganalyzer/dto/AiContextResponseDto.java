package com.caciopee.loganalyzer.dto;

public class AiContextResponseDto {
    private String context;
    private Integer totalLogsUsed;

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public Integer getTotalLogsUsed() { return totalLogsUsed; }
    public void setTotalLogsUsed(Integer totalLogsUsed) { this.totalLogsUsed = totalLogsUsed; }
}