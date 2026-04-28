package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class IncidentCandidateDto {

    private String incidentType;
    private String severity;
    private String confidence;

    private Long importId;
    private String sessionId;
    private String businessKey;
    private String correlationId;

    private LocalDateTime firstTimestamp;
    private LocalDateTime lastTimestamp;

    private String title;
    private String explanation;
    private String probableCause;

    private List<String> evidenceMessages = new ArrayList<>();
    private List<Long> evidenceLogIds = new ArrayList<>();

    public String getIncidentType() {
        return incidentType;
    }

    public void setIncidentType(String incidentType) {
        this.incidentType = incidentType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public Long getImportId() {
        return importId;
    }

    public void setImportId(Long importId) {
        this.importId = importId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public LocalDateTime getFirstTimestamp() {
        return firstTimestamp;
    }

    public void setFirstTimestamp(LocalDateTime firstTimestamp) {
        this.firstTimestamp = firstTimestamp;
    }

    public LocalDateTime getLastTimestamp() {
        return lastTimestamp;
    }

    public void setLastTimestamp(LocalDateTime lastTimestamp) {
        this.lastTimestamp = lastTimestamp;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getProbableCause() {
        return probableCause;
    }

    public void setProbableCause(String probableCause) {
        this.probableCause = probableCause;
    }

    public List<String> getEvidenceMessages() {
        return evidenceMessages;
    }

    public void setEvidenceMessages(List<String> evidenceMessages) {
        this.evidenceMessages = evidenceMessages;
    }

    public List<Long> getEvidenceLogIds() {
        return evidenceLogIds;
    }

    public void setEvidenceLogIds(List<Long> evidenceLogIds) {
        this.evidenceLogIds = evidenceLogIds;
    }
}