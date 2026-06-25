package com.caciopee.loganalyzer.dto;

public class AssistantFullAnalysisResponseDto {

    private String reportMarkdown;
    private String mode;
    private boolean aiConfigured;
    private String hint;

    public String getReportMarkdown() { return reportMarkdown; }
    public void setReportMarkdown(String reportMarkdown) { this.reportMarkdown = reportMarkdown; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public boolean isAiConfigured() { return aiConfigured; }
    public void setAiConfigured(boolean aiConfigured) { this.aiConfigured = aiConfigured; }

    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = hint; }
}

