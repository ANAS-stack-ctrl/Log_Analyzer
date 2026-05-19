package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class GroupAnalysisResponseDto {

    private String groupBy;
    private String groupKey;
    private LocalDateTime firstTimestamp;
    private LocalDateTime lastTimestamp;

    private Long totalLogs;
    private Long errorCount;
    private Long warningCount;
    private Long zeroResultCount;

    private String narrative;
    private String conclusion;
    private String recommendation;

    private List<String> timeline = new ArrayList<>();

    private List<GroupAnalysisItemDto> processes = new ArrayList<>();
    private List<GroupAnalysisItemDto> actions = new ArrayList<>();
    private List<GroupAnalysisItemDto> filters = new ArrayList<>();
    private List<GroupAnalysisItemDto> businessObjects = new ArrayList<>();
    private List<GroupAnalysisItemDto> warnings = new ArrayList<>();
    private List<GroupAnalysisItemDto> zeroResults = new ArrayList<>();
    private List<GroupAnalysisItemDto> performanceSignals = new ArrayList<>();

    public String getGroupBy() { return groupBy; }
    public void setGroupBy(String groupBy) { this.groupBy = groupBy; }

    public String getGroupKey() { return groupKey; }
    public void setGroupKey(String groupKey) { this.groupKey = groupKey; }

    public LocalDateTime getFirstTimestamp() { return firstTimestamp; }
    public void setFirstTimestamp(LocalDateTime firstTimestamp) { this.firstTimestamp = firstTimestamp; }

    public LocalDateTime getLastTimestamp() { return lastTimestamp; }
    public void setLastTimestamp(LocalDateTime lastTimestamp) { this.lastTimestamp = lastTimestamp; }

    public Long getTotalLogs() { return totalLogs; }
    public void setTotalLogs(Long totalLogs) { this.totalLogs = totalLogs; }

    public Long getErrorCount() { return errorCount; }
    public void setErrorCount(Long errorCount) { this.errorCount = errorCount; }

    public Long getWarningCount() { return warningCount; }
    public void setWarningCount(Long warningCount) { this.warningCount = warningCount; }

    public Long getZeroResultCount() { return zeroResultCount; }
    public void setZeroResultCount(Long zeroResultCount) { this.zeroResultCount = zeroResultCount; }

    public String getNarrative() { return narrative; }
    public void setNarrative(String narrative) { this.narrative = narrative; }

    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

    public List<String> getTimeline() { return timeline; }
    public void setTimeline(List<String> timeline) { this.timeline = timeline; }

    public List<GroupAnalysisItemDto> getProcesses() { return processes; }
    public void setProcesses(List<GroupAnalysisItemDto> processes) { this.processes = processes; }

    public List<GroupAnalysisItemDto> getActions() { return actions; }
    public void setActions(List<GroupAnalysisItemDto> actions) { this.actions = actions; }

    public List<GroupAnalysisItemDto> getFilters() { return filters; }
    public void setFilters(List<GroupAnalysisItemDto> filters) { this.filters = filters; }

    public List<GroupAnalysisItemDto> getBusinessObjects() { return businessObjects; }
    public void setBusinessObjects(List<GroupAnalysisItemDto> businessObjects) { this.businessObjects = businessObjects; }

    public List<GroupAnalysisItemDto> getWarnings() { return warnings; }
    public void setWarnings(List<GroupAnalysisItemDto> warnings) { this.warnings = warnings; }

    public List<GroupAnalysisItemDto> getZeroResults() { return zeroResults; }
    public void setZeroResults(List<GroupAnalysisItemDto> zeroResults) { this.zeroResults = zeroResults; }

    public List<GroupAnalysisItemDto> getPerformanceSignals() { return performanceSignals; }
    public void setPerformanceSignals(List<GroupAnalysisItemDto> performanceSignals) { this.performanceSignals = performanceSignals; }
}