package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExecutiveDashboardResponse {

    private long totalIncidents;
    private long openIncidents;
    private long inProgressIncidents;
    private long resolvedIncidents;
    private long closedIncidents;

    private long activeLogs;
    private long resolvedLogs;
    private long ignoredLogs;
    private long importantActiveLogs;

    private double avgTriageMinutes;
    private double avgInvestigationMinutes;
    private double avgResolutionMinutes;
    private double avgClosureMinutes;

    private Map<String, Long> incidentsBySeverity;
    private Map<String, Long> incidentsByStatus;
    private Map<String, Long> activeLogsByAssignee;

    private List<String> executiveHighlights = new ArrayList<>();

    public long getTotalIncidents() {
        return totalIncidents;
    }

    public void setTotalIncidents(long totalIncidents) {
        this.totalIncidents = totalIncidents;
    }

    public long getOpenIncidents() {
        return openIncidents;
    }

    public void setOpenIncidents(long openIncidents) {
        this.openIncidents = openIncidents;
    }

    public long getInProgressIncidents() {
        return inProgressIncidents;
    }

    public void setInProgressIncidents(long inProgressIncidents) {
        this.inProgressIncidents = inProgressIncidents;
    }

    public long getResolvedIncidents() {
        return resolvedIncidents;
    }

    public void setResolvedIncidents(long resolvedIncidents) {
        this.resolvedIncidents = resolvedIncidents;
    }

    public long getClosedIncidents() {
        return closedIncidents;
    }

    public void setClosedIncidents(long closedIncidents) {
        this.closedIncidents = closedIncidents;
    }

    public long getActiveLogs() {
        return activeLogs;
    }

    public void setActiveLogs(long activeLogs) {
        this.activeLogs = activeLogs;
    }

    public long getResolvedLogs() {
        return resolvedLogs;
    }

    public void setResolvedLogs(long resolvedLogs) {
        this.resolvedLogs = resolvedLogs;
    }

    public long getIgnoredLogs() {
        return ignoredLogs;
    }

    public void setIgnoredLogs(long ignoredLogs) {
        this.ignoredLogs = ignoredLogs;
    }

    public long getImportantActiveLogs() {
        return importantActiveLogs;
    }

    public void setImportantActiveLogs(long importantActiveLogs) {
        this.importantActiveLogs = importantActiveLogs;
    }

    public double getAvgTriageMinutes() {
        return avgTriageMinutes;
    }

    public void setAvgTriageMinutes(double avgTriageMinutes) {
        this.avgTriageMinutes = avgTriageMinutes;
    }

    public double getAvgInvestigationMinutes() {
        return avgInvestigationMinutes;
    }

    public void setAvgInvestigationMinutes(double avgInvestigationMinutes) {
        this.avgInvestigationMinutes = avgInvestigationMinutes;
    }

    public double getAvgResolutionMinutes() {
        return avgResolutionMinutes;
    }

    public void setAvgResolutionMinutes(double avgResolutionMinutes) {
        this.avgResolutionMinutes = avgResolutionMinutes;
    }

    public double getAvgClosureMinutes() {
        return avgClosureMinutes;
    }

    public void setAvgClosureMinutes(double avgClosureMinutes) {
        this.avgClosureMinutes = avgClosureMinutes;
    }

    public Map<String, Long> getIncidentsBySeverity() {
        return incidentsBySeverity;
    }

    public void setIncidentsBySeverity(Map<String, Long> incidentsBySeverity) {
        this.incidentsBySeverity = incidentsBySeverity;
    }

    public Map<String, Long> getIncidentsByStatus() {
        return incidentsByStatus;
    }

    public void setIncidentsByStatus(Map<String, Long> incidentsByStatus) {
        this.incidentsByStatus = incidentsByStatus;
    }

    public Map<String, Long> getActiveLogsByAssignee() {
        return activeLogsByAssignee;
    }

    public void setActiveLogsByAssignee(Map<String, Long> activeLogsByAssignee) {
        this.activeLogsByAssignee = activeLogsByAssignee;
    }

    public List<String> getExecutiveHighlights() {
        return executiveHighlights;
    }

    public void setExecutiveHighlights(List<String> executiveHighlights) {
        this.executiveHighlights = executiveHighlights;
    }
}