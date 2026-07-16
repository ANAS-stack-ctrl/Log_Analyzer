package com.caciopee.loganalyzer.dto;

/**
 * Preuve chiffrée cliquable : un chiffre du récit → la ligne de log exacte.
 */
public class LatencyEvidenceLinkDto {

    private String id;
    private String label;
    private String valueDisplay;
    private Long valueMs;
    private Integer valueCount;
    private Long logId;
    /** Terme de recherche pour filtrer/naviguer vers la preuve dans les logs. */
    private String searchTerm;
    private String excerpt;
    private String sourceFile;
    private String timestamp;
    private String family;

    public LatencyEvidenceLinkDto() {
    }

    public LatencyEvidenceLinkDto(String id, String label, String valueDisplay) {
        this.id = id;
        this.label = label;
        this.valueDisplay = valueDisplay;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getValueDisplay() { return valueDisplay; }
    public void setValueDisplay(String valueDisplay) { this.valueDisplay = valueDisplay; }

    public Long getValueMs() { return valueMs; }
    public void setValueMs(Long valueMs) { this.valueMs = valueMs; }

    public Integer getValueCount() { return valueCount; }
    public void setValueCount(Integer valueCount) { this.valueCount = valueCount; }

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }

    public String getSearchTerm() { return searchTerm; }
    public void setSearchTerm(String searchTerm) { this.searchTerm = searchTerm; }

    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }

    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getFamily() { return family; }
    public void setFamily(String family) { this.family = family; }
}
