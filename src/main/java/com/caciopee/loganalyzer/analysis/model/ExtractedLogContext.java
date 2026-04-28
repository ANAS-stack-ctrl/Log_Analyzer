package com.caciopee.loganalyzer.analysis.model;

public class ExtractedLogContext {

    private String extractedUuid;
    private String extractedTransactionId;
    private String extractedFilterCode;
    private String extractedThreadName;
    private String extractedClassName;
    private Integer extractedRowCount;
    private Long extractedDurationMs;
    private Integer extractedMemoryMo;

    public String getExtractedUuid() {
        return extractedUuid;
    }

    public void setExtractedUuid(String extractedUuid) {
        this.extractedUuid = extractedUuid;
    }

    public String getExtractedTransactionId() {
        return extractedTransactionId;
    }

    public void setExtractedTransactionId(String extractedTransactionId) {
        this.extractedTransactionId = extractedTransactionId;
    }

    public String getExtractedFilterCode() {
        return extractedFilterCode;
    }

    public void setExtractedFilterCode(String extractedFilterCode) {
        this.extractedFilterCode = extractedFilterCode;
    }

    public String getExtractedThreadName() {
        return extractedThreadName;
    }

    public void setExtractedThreadName(String extractedThreadName) {
        this.extractedThreadName = extractedThreadName;
    }

    public String getExtractedClassName() {
        return extractedClassName;
    }

    public void setExtractedClassName(String extractedClassName) {
        this.extractedClassName = extractedClassName;
    }

    public Integer getExtractedRowCount() {
        return extractedRowCount;
    }

    public void setExtractedRowCount(Integer extractedRowCount) {
        this.extractedRowCount = extractedRowCount;
    }

    public Long getExtractedDurationMs() {
        return extractedDurationMs;
    }

    public void setExtractedDurationMs(Long extractedDurationMs) {
        this.extractedDurationMs = extractedDurationMs;
    }

    public Integer getExtractedMemoryMo() {
        return extractedMemoryMo;
    }

    public void setExtractedMemoryMo(Integer extractedMemoryMo) {
        this.extractedMemoryMo = extractedMemoryMo;
    }
}