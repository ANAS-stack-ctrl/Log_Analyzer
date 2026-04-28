package com.caciopee.loganalyzer.analysis.dto;

import com.caciopee.loganalyzer.analysis.DiagnosticSeverity;

public class WorkflowDiagnosticDto {

    private DiagnosticSeverity severity;
    private String code;
    private String title;
    private String description;

    public WorkflowDiagnosticDto() {
    }

    public WorkflowDiagnosticDto(DiagnosticSeverity severity, String code, String title, String description) {
        this.severity = severity;
        this.code = code;
        this.title = title;
        this.description = description;
    }

    public DiagnosticSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(DiagnosticSeverity severity) {
        this.severity = severity;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}