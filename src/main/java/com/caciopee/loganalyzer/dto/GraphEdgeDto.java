package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

public class GraphEdgeDto {

    private String from;
    private String to;
    private String relation;
    private long count;
    private List<GraphOccurrenceDto> occurrences = new ArrayList<>();

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getRelation() {
        return relation;
    }

    public void setRelation(String relation) {
        this.relation = relation;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public List<GraphOccurrenceDto> getOccurrences() {
        return occurrences;
    }

    public void setOccurrences(List<GraphOccurrenceDto> occurrences) {
        this.occurrences = occurrences;
    }
}
