package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

public class GraphNodeDto {
    private String id;
    private String type;
    private String label;
    private Long count;
    private String severity;
    private String description;
    private List<String> examples = new ArrayList<>();
    private List<GraphOccurrenceDto> occurrences = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getExamples() {
        return examples;
    }

    public void setExamples(List<String> examples) {
        this.examples = examples;
    }

    public List<GraphOccurrenceDto> getOccurrences() {
        return occurrences;
    }

    public void setOccurrences(List<GraphOccurrenceDto> occurrences) {
        this.occurrences = occurrences;
    }
}