package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class GroupAnalysisItemDto {
    private String type;
    private String name;
    private Long count;
    private LocalDateTime firstTimestamp;
    private LocalDateTime lastTimestamp;
    private String diagnostic;
    private List<String> examples = new ArrayList<>();

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getCount() { return count; }
    public void setCount(Long count) { this.count = count; }

    public LocalDateTime getFirstTimestamp() { return firstTimestamp; }
    public void setFirstTimestamp(LocalDateTime firstTimestamp) { this.firstTimestamp = firstTimestamp; }

    public LocalDateTime getLastTimestamp() { return lastTimestamp; }
    public void setLastTimestamp(LocalDateTime lastTimestamp) { this.lastTimestamp = lastTimestamp; }

    public String getDiagnostic() { return diagnostic; }
    public void setDiagnostic(String diagnostic) { this.diagnostic = diagnostic; }

    public List<String> getExamples() { return examples; }
    public void setExamples(List<String> examples) { this.examples = examples; }
}