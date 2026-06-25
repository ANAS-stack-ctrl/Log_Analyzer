package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ImportExtractionAuditDto {

    private Long importId;
    private long totalLogs;
    private long sampledLogs;
    private long withProcess;
    private long withTaskOrAction;
    private long withFilter;
    private long withObject;
    private double workflowCoveragePercent;
    private double catalogCoveragePercent;
    private double graphFamilyCoveragePercent;
    private long catalogMatched;
    private long catalogUnknown;
    private int catalogFamilyCount;
    private Map<String, Long> familyHits = new LinkedHashMap<>();
    private List<String> unknownSamples = new ArrayList<>();
    private String note;

    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }

    public long getTotalLogs() { return totalLogs; }
    public void setTotalLogs(long totalLogs) { this.totalLogs = totalLogs; }

    public long getSampledLogs() { return sampledLogs; }
    public void setSampledLogs(long sampledLogs) { this.sampledLogs = sampledLogs; }

    public long getWithProcess() { return withProcess; }
    public void setWithProcess(long withProcess) { this.withProcess = withProcess; }

    public long getWithTaskOrAction() { return withTaskOrAction; }
    public void setWithTaskOrAction(long withTaskOrAction) { this.withTaskOrAction = withTaskOrAction; }

    public long getWithFilter() { return withFilter; }
    public void setWithFilter(long withFilter) { this.withFilter = withFilter; }

    public long getWithObject() { return withObject; }
    public void setWithObject(long withObject) { this.withObject = withObject; }

    public double getWorkflowCoveragePercent() { return workflowCoveragePercent; }
    public void setWorkflowCoveragePercent(double workflowCoveragePercent) { this.workflowCoveragePercent = workflowCoveragePercent; }

    public double getCatalogCoveragePercent() { return catalogCoveragePercent; }
    public void setCatalogCoveragePercent(double catalogCoveragePercent) { this.catalogCoveragePercent = catalogCoveragePercent; }

    public double getGraphFamilyCoveragePercent() { return graphFamilyCoveragePercent; }
    public void setGraphFamilyCoveragePercent(double graphFamilyCoveragePercent) {
        this.graphFamilyCoveragePercent = graphFamilyCoveragePercent;
    }

    public long getCatalogMatched() { return catalogMatched; }
    public void setCatalogMatched(long catalogMatched) { this.catalogMatched = catalogMatched; }

    public long getCatalogUnknown() { return catalogUnknown; }
    public void setCatalogUnknown(long catalogUnknown) { this.catalogUnknown = catalogUnknown; }

    public int getCatalogFamilyCount() { return catalogFamilyCount; }
    public void setCatalogFamilyCount(int catalogFamilyCount) { this.catalogFamilyCount = catalogFamilyCount; }

    public Map<String, Long> getFamilyHits() { return familyHits; }
    public void setFamilyHits(Map<String, Long> familyHits) { this.familyHits = familyHits; }

    public List<String> getUnknownSamples() { return unknownSamples; }
    public void setUnknownSamples(List<String> unknownSamples) { this.unknownSamples = unknownSamples; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
