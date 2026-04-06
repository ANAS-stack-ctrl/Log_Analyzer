package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

public class AutoIncidentDetectionResponse {

    private int evaluatedClusters;
    private int createdIncidents;
    private int skippedClusters;
    private List<String> details = new ArrayList<>();

    public int getEvaluatedClusters() {
        return evaluatedClusters;
    }

    public void setEvaluatedClusters(int evaluatedClusters) {
        this.evaluatedClusters = evaluatedClusters;
    }

    public int getCreatedIncidents() {
        return createdIncidents;
    }

    public void setCreatedIncidents(int createdIncidents) {
        this.createdIncidents = createdIncidents;
    }

    public int getSkippedClusters() {
        return skippedClusters;
    }

    public void setSkippedClusters(int skippedClusters) {
        this.skippedClusters = skippedClusters;
    }

    public List<String> getDetails() {
        return details;
    }

    public void setDetails(List<String> details) {
        this.details = details;
    }
}