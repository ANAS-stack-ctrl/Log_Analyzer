package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

public class ImportLogTypeSummaryDto {

    private Long importId;
    private String fileName;
    private String typeId;
    private String typeLabel;
    private String typeDescription;
    private long totalLogs;

    private String suggestedGroupBy;
    private String suggestedGroupByLabel;
    private List<String> extractableInfos = new ArrayList<>();
    private List<String> avoidGroupByHints = new ArrayList<>();
    private List<String> tips = new ArrayList<>();

    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getTypeId() { return typeId; }
    public void setTypeId(String typeId) { this.typeId = typeId; }

    public String getTypeLabel() { return typeLabel; }
    public void setTypeLabel(String typeLabel) { this.typeLabel = typeLabel; }

    public String getTypeDescription() { return typeDescription; }
    public void setTypeDescription(String typeDescription) { this.typeDescription = typeDescription; }

    public long getTotalLogs() { return totalLogs; }
    public void setTotalLogs(long totalLogs) { this.totalLogs = totalLogs; }

    public String getSuggestedGroupBy() { return suggestedGroupBy; }
    public void setSuggestedGroupBy(String suggestedGroupBy) { this.suggestedGroupBy = suggestedGroupBy; }

    public String getSuggestedGroupByLabel() { return suggestedGroupByLabel; }
    public void setSuggestedGroupByLabel(String suggestedGroupByLabel) { this.suggestedGroupByLabel = suggestedGroupByLabel; }

    public List<String> getExtractableInfos() { return extractableInfos; }
    public void setExtractableInfos(List<String> extractableInfos) {
        this.extractableInfos = extractableInfos != null ? extractableInfos : new ArrayList<>();
    }

    public List<String> getAvoidGroupByHints() { return avoidGroupByHints; }
    public void setAvoidGroupByHints(List<String> avoidGroupByHints) {
        this.avoidGroupByHints = avoidGroupByHints != null ? avoidGroupByHints : new ArrayList<>();
    }

    public List<String> getTips() { return tips; }
    public void setTips(List<String> tips) {
        this.tips = tips != null ? tips : new ArrayList<>();
    }
}
