package com.caciopee.loganalyzer.rag.dto;

/** État de l'indexation RAG d'un import. */
public class RagIndexStatusDto {

    private Long importId;
    /** NONE | RUNNING | DONE | FAILED */
    private String state;
    private int totalChunks;
    private int indexedChunks;
    private String message;

    public RagIndexStatusDto() { }

    public RagIndexStatusDto(Long importId, String state) {
        this.importId = importId;
        this.state = state;
    }

    public int getProgressPercent() {
        if (totalChunks <= 0) return "DONE".equals(state) ? 100 : 0;
        return Math.min(100, (int) Math.round(100.0 * indexedChunks / totalChunks));
    }

    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
    public int getIndexedChunks() { return indexedChunks; }
    public void setIndexedChunks(int indexedChunks) { this.indexedChunks = indexedChunks; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
