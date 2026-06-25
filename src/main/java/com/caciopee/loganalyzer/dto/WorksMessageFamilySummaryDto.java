package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

public class WorksMessageFamilySummaryDto {

    private String id;
    private String group;
    private String label;
    private String displayLabel;
    private List<String> patterns = new ArrayList<>();
    private List<String> extractable = new ArrayList<>();
    private String status;
    private boolean columnOnly;
    private boolean attachToGraph;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDisplayLabel() { return displayLabel; }
    public void setDisplayLabel(String displayLabel) { this.displayLabel = displayLabel; }

    public List<String> getPatterns() { return patterns; }
    public void setPatterns(List<String> patterns) { this.patterns = patterns; }

    public List<String> getExtractable() { return extractable; }
    public void setExtractable(List<String> extractable) { this.extractable = extractable; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isColumnOnly() { return columnOnly; }
    public void setColumnOnly(boolean columnOnly) { this.columnOnly = columnOnly; }

    public boolean isAttachToGraph() { return attachToGraph; }
    public void setAttachToGraph(boolean attachToGraph) { this.attachToGraph = attachToGraph; }
}
