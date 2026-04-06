package com.caciopee.loganalyzer.dto;

import com.caciopee.loganalyzer.entity.LogImport;

public class LogImportResult {

    private boolean duplicate;
    private String message;
    private LogImport logImport;

    public LogImportResult() {
    }

    public LogImportResult(boolean duplicate, String message, LogImport logImport) {
        this.duplicate = duplicate;
        this.message = message;
        this.logImport = logImport;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LogImport getLogImport() {
        return logImport;
    }

    public void setLogImport(LogImport logImport) {
        this.logImport = logImport;
    }
}