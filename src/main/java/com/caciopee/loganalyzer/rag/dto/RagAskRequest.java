package com.caciopee.loganalyzer.rag.dto;

/** Requête de question sur un import (chat RAG). */
public class RagAskRequest {

    private Long importId;
    private String question;

    // filtres optionnels pour affiner la recherche
    private Boolean onlyErrors;
    private Long minDurationMs;
    private String sessionId;

    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public Boolean getOnlyErrors() { return onlyErrors; }
    public void setOnlyErrors(Boolean onlyErrors) { this.onlyErrors = onlyErrors; }
    public Long getMinDurationMs() { return minDurationMs; }
    public void setMinDurationMs(Long minDurationMs) { this.minDurationMs = minDurationMs; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
